package com.lge.ywlauncher;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Additional premium-looking gradient presets for the launcher background picker. */
public final class PresetBackgroundDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int preset;

    public PresetBackgroundDrawable(int preset) { this.preset = preset; }

    @Override public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        float w = bounds.width(), h = bounds.height();
        if (w <= 0 || h <= 0) return;
        int top, middle, bottom, glow;
        switch (preset) {
            case 8: // Midnight
                top = 0xff10152f; middle = 0xff30265d; bottom = 0xff111a32; glow = 0xff8b72ee; break;
            case 9: // Ocean
                top = 0xff062b49; middle = 0xff075f83; bottom = 0xff071c38; glow = 0xff39c5df; break;
            case 10: // Sunset
                top = 0xff291a48; middle = 0xff82476d; bottom = 0xffd17b4e; glow = 0xffffbd68; break;
            case 11: // Forest
                top = 0xff071f25; middle = 0xff145b4c; bottom = 0xff102c32; glow = 0xff65d6a1; break;
            case 12: // Graphite
                top = 0xff101820; middle = 0xff354454; bottom = 0xff111923; glow = 0xff8aa9bd; break;
            case 13: // Ember
                top = 0xff241522; middle = 0xff713d36; bottom = 0xff201725; glow = 0xffff965e; break;
            case 14: // Glacier
                top = 0xff0b2439; middle = 0xff286a80; bottom = 0xff102d49; glow = 0xff8be8f2; break;
            case 15: // Plum
                top = 0xff1d1635; middle = 0xff573b78; bottom = 0xff21172f; glow = 0xffd69bfa; break;
            case 16: // Dune
                top = 0xff33251f; middle = 0xff806044; bottom = 0xff34251e; glow = 0xffffd08a; break;
            case 17: // Borealis
                top = 0xff071d2b; middle = 0xff12635e; bottom = 0xff10283d; glow = 0xff72f0d0; break;
            default:
                top = 0xff101820; middle = 0xff354454; bottom = 0xff111923; glow = 0xff8aa9bd; break;
        }
        canvas.save();
        canvas.translate(bounds.left, bounds.top);
        paint.setShader(new LinearGradient(0, 0, w * .25f, h,
            new int[]{top, middle, bottom}, new float[]{0f, .52f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(new RadialGradient(w * .78f, h * .28f, Math.max(w, h) * .72f,
            new int[]{withAlpha(glow, 112), withAlpha(glow, 35), 0x00000000},
            new float[]{0f, .48f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(new RadialGradient(w * .12f, h * .82f, Math.max(w, h) * .62f,
            new int[]{0x226f9fff, 0x00000000}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
        canvas.restore();
    }

    private int withAlpha(int color, int alpha) { return (color & 0x00ffffff) | ((alpha & 0xff) << 24); }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return android.graphics.PixelFormat.OPAQUE; }
}
