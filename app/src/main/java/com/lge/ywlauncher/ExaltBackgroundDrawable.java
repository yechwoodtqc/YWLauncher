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
                0xff001e60,
                0xff00309a,
                0xff003ec2,
                0xff0844d8,
                0xff2838e1,
                0xff4a2de1,
                0xff7e2adc,
                0xffa22ed8,
                0xffc632da
            },
            new float[] { 0f, 0.10f, 0.20f, 0.31f, 0.46f, 0.59f, 0.76f, 0.90f, 1f },
            Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);

        // A restrained blue glow toward the right-center matches the luminous
        // cobalt area in the reference without introducing visible bands.
        paint.setShader(new RadialGradient(width * 0.92f, height * 0.46f,
            Math.max(width, height) * 1.05f,
            new int[] { 0x332f6cff, 0x142b55ff, 0x00000000 },
            new float[] { 0f, 0.55f, 1f }, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);

        paint.setShader(null);
        canvas.restore();
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return android.graphics.PixelFormat.OPAQUE; }
}
