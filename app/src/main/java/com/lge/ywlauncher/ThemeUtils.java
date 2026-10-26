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

    public static int text(Context context) {
        return isDark(context) ? Color.WHITE : Color.rgb(28, 36, 44);
    }

    public static int secondaryText(Context context) {
        return isDark(context) ? Color.rgb(180, 190, 200) : Color.rgb(95, 105, 115);
    }

    public static int surface(Context context) {
        return isDark(context) ? Color.rgb(16, 24, 32) : Color.rgb(245, 247, 250);
    }

    public static int divider(Context context) {
        return isDark(context) ? Color.rgb(48, 58, 68) : Color.rgb(222, 228, 234);
    }

    public static void apply(Activity activity) {
        activity.setTheme(isDark(activity) ? R.style.AppThemeDark : R.style.AppTheme);
    }

    public static void tint(Button button, Context context) {
        button.setBackgroundTintList(ColorStateList.valueOf(accent(context)));
        button.setTextColor(onAccent(context));
    }

    // Transparent at rest; only the currently focused/pressed item gets a highlight.
    public static StateListDrawable focusBackground(Context context) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, focusShape(context));
        states.addState(new int[]{android.R.attr.state_pressed}, focusShape(context));
        states.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        return states;
    }

    private static GradientDrawable focusShape(Context context) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(accent(context));
        shape.setCornerRadius(12 * context.getResources().getDisplayMetrics().density);
        return shape;
    }
}
