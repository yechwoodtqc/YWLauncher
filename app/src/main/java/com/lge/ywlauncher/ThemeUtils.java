package com.lge.ywlauncher;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.widget.Button;

public final class ThemeUtils {
    private ThemeUtils() {}

    public static boolean isDark(Context context) {
        String mode = context.getSharedPreferences("launcher", 0).getString("theme_mode", "system");
        if ("dark".equals(mode)) return true;
        if ("light".equals(mode)) return false;
        int night = context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return night == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    public static int accent(Context context) {
        return context.getSharedPreferences("launcher", 0).getInt("accent_color", Color.rgb(26, 115, 232));
    }

    public static int onAccent(Context context) {
        int color = accent(context);
        int luminance = (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000;
        return luminance > 140 ? Color.BLACK : Color.WHITE;
    }

    public static int text(Context context) { return isDark(context) ? Color.WHITE : Color.rgb(24, 28, 33); }
    public static int secondaryText(Context context) { return isDark(context) ? Color.rgb(190, 198, 208) : Color.rgb(85, 95, 105); }
    public static int focusText(Context context) {
        return context.getSharedPreferences("launcher", 0).getBoolean("focus_outline_only", false)
            ? text(context) : onAccent(context);
    }

    public static int surface(Context context) { return isDark(context) ? Color.rgb(12, 17, 23) : Color.WHITE; }
    public static int divider(Context context) { return isDark(context) ? Color.rgb(48, 58, 68) : Color.rgb(225, 229, 234); }

    public static void apply(Activity activity) {
        activity.setTheme(isDark(activity) ? R.style.AppThemeDark : R.style.AppTheme);
    }

    public static void tint(Button button, Context context) {
        button.setBackgroundTintList(ColorStateList.valueOf(accent(context)));
        button.setTextColor(onAccent(context));
    }

    public static StateListDrawable focusBackground(Context context) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, focusShape(context, true));
        states.addState(new int[]{android.R.attr.state_pressed}, focusShape(context, true));
        states.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        return states;
    }

    public static StateListDrawable selectedBackground(Context context) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{}, focusShape(context, false));
        return states;
    }

    private static GradientDrawable focusShape(Context context, boolean focused) {
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(10 * context.getResources().getDisplayMetrics().density);
        boolean outline = context.getSharedPreferences("launcher", 0).getBoolean("focus_outline_only", false);
        if (outline && focused) {
            shape.setColor(Color.TRANSPARENT);
            shape.setStroke(Math.max(2, Math.round(2 * context.getResources().getDisplayMetrics().density)), accent(context));
        } else if (focused) {
            shape.setColor(accent(context));
        } else {
            shape.setColor(withAlpha(accent(context), 90));
        }
        return shape;
    }

    private static int withAlpha(int color, int alpha) { return (color & 0x00ffffff) | ((alpha & 0xff) << 24); }
}
