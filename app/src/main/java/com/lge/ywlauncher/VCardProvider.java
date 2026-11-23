package com.lge.ywlauncher;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

public class VCardProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "text/x-vcard"; }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode) || uri == null || !("launcher.vcf".equals(uri.getLastPathSegment()) || "home.vcf".equals(uri.getLastPathSegment()))) {
            throw new FileNotFoundException("Unknown contact card");
        }
        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            Thread writer = new Thread(() -> {
                String assetName = "home.vcf".equals(uri.getLastPathSegment()) ? "YWHome.vcf" : "YWLauncher.vcf";
                try (InputStream input = getContext().getAssets().open(assetName);
                     OutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                    output.flush();
                } catch (IOException ignored) { }
            }, "YWVCardStream");
            writer.start();
            return pipe[0];
        } catch (IOException e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sortOrder) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
