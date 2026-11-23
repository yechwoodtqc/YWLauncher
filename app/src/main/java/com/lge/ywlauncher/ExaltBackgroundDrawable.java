package com.lge.ywlauncher;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Aurora-style LG Exalt LTE background, based on the VN220 display reference. */
public final class ExaltBackgroundDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path aurora = new Path();

    @Override public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        float width = bounds.width(), height = bounds.height();
        if (width <= 0 || height <= 0) return;
        canvas.save();
        canvas.translate(bounds.left, bounds.top);
        canvas.scale(width / 240f, height / 400f);

        paint.setShader(new LinearGradient(0, 0, 0, 400,
            new int[]{0xff251449, 0xff29205e, 0xff202052, 0xff171b43},
            new float[]{0f, .38f, .72f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, 240, 400, paint);

        paint.setShader(new RadialGradient(36, 178, 190,
            new int[]{0x995b3a91, 0x44402b79, 0x000f1740},
            new float[]{0f, .55f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, 240, 400, paint);
        paint.setShader(new RadialGradient(192, 156, 155,
            new int[]{0x9966f4bd, 0x5570d9b6, 0x000d1a45},
            new float[]{0f, .45f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, 240, 400, paint);

        aurora.reset();
        aurora.moveTo(112, -18);
        aurora.cubicTo(180, 34, 142, 88, 183, 144);
        aurora.cubicTo(220, 196, 173, 252, 218, 322);
        aurora.cubicTo(231, 348, 225, 379, 244, 416);
        aurora.lineTo(240, 416);
        aurora.cubicTo(205, 367, 203, 338, 190, 298);
        aurora.cubicTo(158, 234, 197, 191, 157, 139);
        aurora.cubicTo(121, 91, 154, 42, 112, -18);
        aurora.close();
        paint.setShader(new LinearGradient(108, 0, 222, 390,
            new int[]{0x0079f7c7, 0x9971efb0, 0xdd75eaa4, 0x6678e9c2, 0x0075cbe8},
            new float[]{0f, .22f, .48f, .78f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawPath(aurora, paint);

        aurora.reset();
        aurora.moveTo(132, -8);
        aurora.cubicTo(165, 50, 143, 92, 169, 143);
        aurora.cubicTo(198, 202, 157, 252, 190, 321);
        aurora.cubicTo(198, 349, 204, 379, 214, 405);
        aurora.cubicTo(180, 355, 178, 326, 164, 286);
        aurora.cubicTo(139, 225, 177, 191, 145, 140);
        aurora.cubicTo(118, 91, 143, 48, 132, -8);
        aurora.close();
        paint.setShader(new LinearGradient(130, 0, 202, 400,
            new int[]{0x0080dfff, 0x5574dfff, 0x8869d9ff, 0x2278d8ed, 0x0078d8ed},
            new float[]{0f, .28f, .52f, .8f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawPath(aurora, paint);

        paint.setShader(new LinearGradient(0, 70, 90, 350,
            new int[]{0x004a39a0, 0x554f39a2, 0x004d3b9a},
            new float[]{0f, .52f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawOval(-72, 42, 128, 388, paint);

        paint.setShader(null);
        canvas.restore();
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return android.graphics.PixelFormat.OPAQUE; }
}