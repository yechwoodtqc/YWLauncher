package com.lge.ywlauncher;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Smooth blue-to-purple Exalt home-screen gradient from the supplied reference. */
public final class ExaltBackgroundDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    @Override public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        float width = bounds.width(), height = bounds.height();
        if (width <= 0 || height <= 0) return;
        canvas.save();
        canvas.translate(bounds.left, bounds.top);

        // The reference is a clean vertical sweep: deep blue at the top,
        // saturated royal blue through the middle, then purple and magenta.
        paint.setShader(new LinearGradient(0, 0, 0, height,
            new int[] {
                0xff173f91,
                0xff2452b0,
                0xff5145b5,
                0xff7839ad,
                0xff9b399e
            },
            new float[] { 0f, 0.34f, 0.62f, 0.84f, 1f },
            Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);

        // Soft blue illumination near the upper-left, without the teal cast
        // that made the previous version look unlike the supplied screen.
        paint.setShader(new RadialGradient(width * 0.08f, height * 0.02f,
            Math.max(width, height) * 0.82f,
            new int[] { 0x443b8ff0, 0x1a327be0, 0x00000000 },
            new float[] { 0f, 0.48f, 1f }, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);

        // A restrained violet-magenta bloom at the bottom adds the soft
        // luminous finish visible in the Exalt reference.
        paint.setShader(new RadialGradient(width * 0.78f, height * 1.02f,
            Math.max(width, height) * 0.88f,
            new int[] { 0x667f35bd, 0x337e36b5, 0x00000000 },
            new float[] { 0f, 0.52f, 1f }, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);

        // Gentle edge shading keeps white labels and icons readable.
        paint.setShader(new LinearGradient(0, 0, width, 0,
            new int[] { 0x18071235, 0x00000000, 0x120b062d },
            new float[] { 0f, 0.5f, 1f }, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);

        paint.setShader(null);
        canvas.restore();
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return android.graphics.PixelFormat.OPAQUE; }
}
