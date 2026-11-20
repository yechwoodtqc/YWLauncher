package com.lge.ywlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

/** Creates the full-colour icon shown inside notification cards, separate from the monochrome status glyph. */
public final class NotificationIconUtils {
    private NotificationIconUtils() { }

    public static Bitmap createLargeIcon(Context context, int drawableId) {
        Drawable drawable = context.getDrawable(drawableId);
        int size = Math.max(64, Math.round(64 * context.getResources().getDisplayMetrics().density));
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, size, size);
        drawable.draw(canvas);
        return bitmap;
    }
}
