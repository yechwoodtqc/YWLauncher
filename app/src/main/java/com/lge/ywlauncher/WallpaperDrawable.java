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
    private final float zoom, rotation, panX, panY;

    public WallpaperDrawable(Bitmap bitmap, String mode) { this(bitmap, mode, 1f, 0f); }
    public WallpaperDrawable(Bitmap bitmap, String mode, float zoom, float rotation) {
        this(bitmap, mode, zoom, rotation, 0f, 0f);
    }
    public WallpaperDrawable(Bitmap bitmap, String mode, float zoom, float rotation, float panX, float panY) {
        this.bitmap = bitmap;
        this.mode = mode == null ? "crop" : mode;
        this.zoom = Math.max(1f, Math.min(3f, zoom));
        this.rotation = rotation;
        this.panX = panX;
        this.panY = panY;
    }
    @Override public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (bitmap == null || b.isEmpty()) return;
        float vw = b.width(), vh = b.height(), iw = bitmap.getWidth(), ih = bitmap.getHeight();
        boolean quarterTurn = (Math.round(rotation / 90f) & 1) == 1;
        float rotatedWidth = quarterTurn ? ih : iw;
        float rotatedHeight = quarterTurn ? iw : ih;
        float sx = vw / rotatedWidth, sy = vh / rotatedHeight;
        float base = "fit".equals(mode) ? Math.min(sx, sy) : Math.max(sx, sy);
        float scale = base * zoom;
        float dw = iw * scale, dh = ih * scale;
        float maxPanX = Math.max(0f, (rotatedWidth * scale - vw) / 2f);
        float maxPanY = Math.max(0f, (rotatedHeight * scale - vh) / 2f);
        float boundedPanX = Math.max(-maxPanX, Math.min(maxPanX, panX));
        float boundedPanY = Math.max(-maxPanY, Math.min(maxPanY, panY));
        RectF dst = new RectF(b.left + (vw - dw) / 2f, b.top + (vh - dh) / 2f,
            b.left + (vw + dw) / 2f, b.top + (vh + dh) / 2f);
        canvas.save();
        canvas.clipRect(b);
        canvas.rotate(rotation, b.exactCenterX(), b.exactCenterY());
        canvas.translate(boundedPanX, boundedPanY);
        canvas.drawBitmap(bitmap, null, dst, paint);
        canvas.restore();
    }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
}
