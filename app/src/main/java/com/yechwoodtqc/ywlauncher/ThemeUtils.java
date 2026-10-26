package com.yechwoodtqc.ywlauncher;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.widget.Button;

public final class ThemeUtils {
    private ThemeUtils() {}
    public static boolean isDark(Context context) {
        return context.getSharedPreferences("launcher",0).getString("theme_mode","dark").equals("dark");
    }
    public static int accent(Context context) {
        return context.getSharedPreferences("launcher",0).getInt("accent_color",Color.rgb(26,115,232));
    }
    public static int text(Context context) {
        return isDark(context)?Color.WHITE:Color.rgb(28,36,44);
    }
    public static int surface(Context context) {
        return isDark(context)?Color.rgb(16,24,32):Color.rgb(245,247,250);
    }
    public static void apply(Activity activity) {
        activity.setTheme(isDark(activity)?R.style.AppThemeDark:R.style.AppTheme);
    }
    public static void tint(Button button, Context context) {
        button.setBackgroundTintList(ColorStateList.valueOf(accent(context)));
        button.setTextColor(Color.WHITE);
    }
    public static StateListDrawable focusBackground(Context context) {
        int color=accent(context);
        StateListDrawable states=new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused},new ColorDrawable(color));
        states.addState(new int[]{android.R.attr.state_pressed},new ColorDrawable(color));
        states.addState(new int[]{},new ColorDrawable(isDark(context)?Color.rgb(30,42,54):Color.rgb(230,237,245)));
        return states;
    }
}
