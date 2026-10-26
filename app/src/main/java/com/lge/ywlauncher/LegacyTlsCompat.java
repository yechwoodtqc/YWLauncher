package com.lge.ywlauncher;

import android.content.Context;
import android.os.Build;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Android 6 does not trust the ISRG Root X1 CA on every OEM certificate store.
 * This adds that public root as a narrowly scoped fallback while preserving normal
 * system validation and HttpsURLConnection's default hostname verification.
 */
public final class LegacyTlsCompat {
    private static volatile SSLSocketFactory cachedFactory;

    private LegacyTlsCompat() {}

    public static void configure(Context context, HttpsURLConnection connection)
        throws GeneralSecurityException, java.io.IOException {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) return;
        connection.setSSLSocketFactory(getFactory(context.getApplicationContext()));
    }

    private static SSLSocketFactory getFactory(Context context)
        throws GeneralSecurityException, java.io.IOException {
        SSLSocketFactory cached = cachedFactory;
        if (cached != null) return cached;
        synchronized (LegacyTlsCompat.class) {
            if (cachedFactory != null) return cachedFactory;

            X509TrustManager systemTrust = findX509TrustManager(null);
            KeyStore extraStore = KeyStore.getInstance(KeyStore.getDefaultType());
            try {
                extraStore.load(null, null);
                CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
                try (InputStream input = context.getResources().openRawResource(R.raw.isrg_root_x1)) {
                    Certificate root = certificateFactory.generateCertificate(input);
                    extraStore.setCertificateEntry("isrg-root-x1", root);
                }
            } catch (java.io.IOException e) {
                throw e;
            } catch (Exception e) {
                throw new GeneralSecurityException("Unable to load bundled ISRG Root X1 certificate", e);
            }

            X509TrustManager extraTrust = findX509TrustManager(extraStore);
            X509TrustManager combined = new X509TrustManager() {
                @Override public void checkClientTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
                    systemTrust.checkClientTrusted(chain, authType);
                }

                @Override public void checkServerTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
                    try {
                        systemTrust.checkServerTrusted(chain, authType);
                    } catch (CertificateException systemFailure) {
                        try {
                            // This still performs PKIX chain/expiry/signature checks to ISRG Root X1.
                            extraTrust.checkServerTrusted(chain, authType);
                        } catch (CertificateException extraFailure) {
                            extraFailure.addSuppressed(systemFailure);
                            throw extraFailure;
                        }
                    }
                }

                @Override public X509Certificate[] getAcceptedIssuers() {
                    X509Certificate[] system = systemTrust.getAcceptedIssuers();
                    X509Certificate[] extra = extraTrust.getAcceptedIssuers();
                    X509Certificate[] merged = new X509Certificate[system.length + extra.length];
                    System.arraycopy(system, 0, merged, 0, system.length);
                    System.arraycopy(extra, 0, merged, system.length, extra.length);
                    return merged;
                }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{combined}, null);
            cachedFactory = sslContext.getSocketFactory();
            return cachedFactory;
        }
    }

    private static X509TrustManager findX509TrustManager(KeyStore store)
        throws GeneralSecurityException {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(store);
        for (TrustManager manager : factory.getTrustManagers()) {
            if (manager instanceof X509TrustManager) return (X509TrustManager) manager;
        }
        throw new GeneralSecurityException("No X509 trust manager is available");
    }
}
