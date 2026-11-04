package com.lge.ywlauncher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.PendingIntent;
import android.app.NotificationManager;
import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import java.io.File;
import java.io.FileInputStream;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        android.content.SharedPreferences prefs = context.getSharedPreferences("launcher", 0);
        if (prefs.getBoolean("keep_wallpaper", false)) {
            final PendingResult pending = goAsync();
            new Thread(() -> {
                try {
                    File file = new File(context.getFilesDir(), "kept_wallpaper.png");
                    if (file.isFile() && file.length() > 0) {
                        BitmapFactory.Options bounds = new BitmapFactory.Options();
                        bounds.inJustDecodeBounds = true;
                        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
                        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return;

                        // Decode near the display resolution instead of loading a
                        // full-resolution camera image during the boot broadcast.
                        android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
                        int targetWidth = Math.max(1, metrics.widthPixels * 2);
                        int targetHeight = Math.max(1, metrics.heightPixels * 2);
                        int sampleSize = 1;
                        while (bounds.outWidth / sampleSize > targetWidth
                            || bounds.outHeight / sampleSize > targetHeight) {
                            sampleSize *= 2;
                        }
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inSampleSize = sampleSize;
                        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
                        if (bitmap != null) {
                            Bitmap output = null;
                            try {
                                String fit=prefs.getString("kept_wallpaper_fit","crop");
                                int width=Math.max(1,metrics.widthPixels), height=Math.max(1,metrics.heightPixels);
                                output=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
                                Canvas canvas=new Canvas(output);
                                Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
                                float sx=width/(float)bitmap.getWidth(), sy=height/(float)bitmap.getHeight();
                                float scale="fit".equals(fit)?Math.min(sx,sy):("stretch".equals(fit)?-1f:Math.max(sx,sy));
                                if (!"stretch".equals(fit)) scale *= Math.max(0.5f, Math.min(2f, prefs.getFloat("kept_wallpaper_zoom", 1f)));
                                Rect destination;
                                if("stretch".equals(fit)) {
                                    float zoom=Math.max(0.5f,Math.min(2f,prefs.getFloat("kept_wallpaper_zoom",1f)));
                                    int dw=Math.round(width*zoom), dh=Math.round(height*zoom);
                                    destination=new Rect((width-dw)/2,(height-dh)/2,(width+dw)/2,(height+dh)/2);
                                } else {
                                    int dw=Math.round(bitmap.getWidth()*scale), dh=Math.round(bitmap.getHeight()*scale);
                                    destination=new Rect((width-dw)/2,(height-dh)/2,(width+dw)/2,(height+dh)/2);
                                }
                                canvas.drawBitmap(bitmap,null,destination,paint);
                                WallpaperManager.getInstance(context).setBitmap(output);
                            } finally {
                                bitmap.recycle();
                                if(output!=null) output.recycle();
                            }
                        }
                    }
                } catch (Exception ignored) {
                } finally {
                    pending.finish();
                }
            }, "YWWallpaperRestore").start();
        }
        if (!prefs.getBoolean("notify", true)) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel("yw_launcher", "Launcher access", NotificationManager.IMPORTANCE_LOW));
        Intent open = new Intent(context, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 1, open, flags);
        Notification.Builder notification = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(context, "yw_launcher") : new Notification.Builder(context);
        boolean showIcon = prefs.getBoolean("status_icon", false);
        notification.setSmallIcon(showIcon ? android.R.drawable.ic_menu_view : R.drawable.ic_notification_hidden)
            .setContentTitle("YW Launcher").setContentText("Press to open your app launcher")
            .setContentIntent(pendingIntent).setOngoing(true);
        manager.notify(42, notification.build());
    }
}