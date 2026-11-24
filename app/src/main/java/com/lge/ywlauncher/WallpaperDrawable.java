package com.lge.ywlauncher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

public class WallpaperDrawable extends Drawable {
    private final Bitmap bitmap;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final String mode;
    private final float zoom, rotation;

    public WallpaperDrawable(Bitmap bitmap, String mode) { this(bitmap, mode, 1f, 0f); }
    public WallpaperDrawable(Bitmap bitmap, String mode, float zoom, float rotation) {
        this.bitmap = bitmap;
        this.mode = mode == null ? "crop" : mode;
        this.zoom = Math.max(0.5f, Math.min(3f, zoom));
        this.rotation = rotation;
    }
    @Override public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (bitmap == null || b.isEmpty()) return;
        float vw = b.width(), vh = b.height(), iw = bitmap.getWidth(), ih = bitmap.getHeight();
        float sx = vw / iw, sy = vh / ih;
        float base = "fit".equals(mode) ? Math.min(sx, sy) : Math.max(sx, sy);
        float scale = base * zoom;
        float dw = iw * scale, dh = ih * scale;
        RectF dst = new RectF(b.left + (vw - dw) / 2f, b.top + (vh - dh) / 2f,
            b.left + (vw + dw) / 2f, b.top + (vh + dh) / 2f);
        canvas.save();
        canvas.clipRect(b);
        canvas.rotate(rotation, b.exactCenterX(), b.exactCenterY());
        canvas.drawBitmap(bitmap, null, dst, paint);
        canvas.restore();
    }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
}
