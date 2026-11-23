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

        // Color stops sampled from the supplied LG Exalt screenshot:
        // navy at the top, intense blue through the upper half, violet in
        // the lower third, and pink-magenta at the bottom.
        paint.setShader(new LinearGradient(0, 0, 0, height,
            new int[] {
                0xff001e60,
                0xff002d91,
                0xff003dbb,
                0xff0641d2,
                0xff182fe0,
                0xff4930e3,
                0xff7730df,
                0xffa331dc,
                0xffc832dc,
                0xffd934ec
            },
            new float[] { 0f, 0.10f, 0.22f, 0.34f, 0.46f, 0.58f, 0.70f, 0.82f, 0.93f, 1f },
            Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);

        // The reference has a soft electric-blue bloom behind the upper app
        // area; it is not a lower-screen pink spotlight.
        paint.setShader(new RadialGradient(width * 0.54f, height * 0.34f,
            Math.max(width, height) * 0.58f,
            new int[] { 0x553d83ff, 0x24345fff, 0x00000000 },
            new float[] { 0f, 0.50f, 1f }, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);

        paint.setShader(null);
        canvas.restore();
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return android.graphics.PixelFormat.OPAQUE; }
}
