package com.lge.ywlauncher;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * LG Exalt-style home screen background, tuned to the supplied VN220 screen
 * reference: aqua across the top-left, blue through the middle, deep indigo
 * toward the lower-left, and violet along the right edge.
 */
public final class ExaltBackgroundDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint overlay = new Paint(Paint.ANTI_ALIAS_FLAG);

    @Override public void draw(Canvas canvas) {
        Rect b = getBounds();
        float w = b.width(), h = b.height();
        if (w <= 0 || h <= 0) return;
        canvas.save();
        canvas.translate(b.left, b.top);

        // Main left-to-right color sweep.
        paint.setShader(new LinearGradient(0, 0, w, h * 0.12f,
            new int[] { 0xff63d2c9, 0xff3b91bd, 0xff7547a6 },
            new float[] { 0f, 0.53f, 1f }, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);

        // Preserve the luminous aqua upper band while darkening the lower half.
        paint.setShader(new LinearGradient(0, 0, 0, h,
            new int[] { 0x18ffffff, 0x080c63a5, 0xb900073b },
            new float[] { 0f, 0.48f, 1f }, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);

        // Aqua bloom in the upper-left, similar to the photographed menu.
        paint.setShader(new RadialGradient(w * 0.03f, h * 0.02f,
            Math.max(w, h) * 0.72f,
            new int[] { 0x668cf0df, 0x22a1eee0, 0x00000000 },
            new float[] { 0f, 0.42f, 1f }, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);

        // Violet glow toward the right, strongest around the lower-right.
        paint.setShader(new RadialGradient(w * 1.02f, h * 0.82f,
            Math.max(w, h) * 0.78f,
            new int[] { 0x887f4bb4, 0x447c4bb0, 0x00000000 },
            new float[] { 0f, 0.5f, 1f }, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);

        // Deep blue pool along the lower-left keeps icons legible.
        paint.setShader(new RadialGradient(w * 0.08f, h * 0.98f,
            Math.max(w, h) * 0.63f,
            new int[] { 0xaa061b60, 0x550b286c, 0x00000000 },
            new float[] { 0f, 0.5f, 1f }, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);

        // A distinct violet bloom reaches the bottom-right corner; the
        // previous right-side glow faded before it reached the lower edge.
        paint.setShader(new RadialGradient(w * 1.02f, h * 1.04f,
            Math.max(w, h) * 0.62f,
            new int[] { 0xaa8b55c9, 0x66804ab7, 0x00000000 },
            new float[] { 0f, 0.52f, 1f }, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);

        paint.setShader(null);
        canvas.restore();
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return android.graphics.PixelFormat.OPAQUE; }
}
