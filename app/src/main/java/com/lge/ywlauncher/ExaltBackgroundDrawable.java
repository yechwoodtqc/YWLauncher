package com.lge.ywlauncher;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Exalt gradient sampled from the original side-by-side launcher reference. */
public final class ExaltBackgroundDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    @Override public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        float width = bounds.width(), height = bounds.height();
        if (width <= 0 || height <= 0) return;
        canvas.save();
        canvas.translate(bounds.left, bounds.top);

        // The supplied Exalt reference is much more saturated than the first
        // approximation: midnight navy at the top, electric royal blue in the
        // upper-middle, violet below, and a bright pink-magenta lower edge.
        paint.setShader(new LinearGradient(0, 0, 0, height,
            new int[] {
                0xff001d5f,
                0xff00349f,
                0xff0140c8,
                0xff0d40db,
                0xff292fe4,
                0xff4b30e4,
                0xff7430e0,
                0xff9630dc,
                0xffb936dc
            },
            new float[] { 0f, 0.12f, 0.23f, 0.34f, 0.49f, 0.62f, 0.77f, 0.90f, 1f },
            Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);

        // The reference has a broad, subtle violet-pink bloom low in the
        // display, not a blue hotspot on the right. Keep it soft so the
        // transition remains a clean continuous gradient.
        paint.setShader(new RadialGradient(width * 0.52f, height * 0.88f,
            Math.max(width, height) * 0.82f,
            new int[] { 0x44f04bff, 0x20c13dff, 0x00000000 },
            new float[] { 0f, 0.52f, 1f }, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);

        paint.setShader(null);
        canvas.restore();
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return android.graphics.PixelFormat.OPAQUE; }
}
