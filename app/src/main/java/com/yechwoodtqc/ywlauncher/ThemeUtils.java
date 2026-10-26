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
        String mode=context.getSharedPreferences("launcher",0).getString("theme_mode","system");
        if("dark".equals(mode)) return true;
        if("light".equals(mode)) return false;
        int night=context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return night==android.content.res.Configuration.UI_MODE_NIGHT_YES;
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
    public static Context scaleContext(Context base) {
        String size=base.getSharedPreferences("launcher",0).getString("display_size","default");
        float scale="small".equals(size)?0.90f:("large".equals(size)?1.15f:1.0f);
        if(scale==1.0f) return base;
        android.content.res.Configuration configuration=new android.content.res.Configuration(base.getResources().getConfiguration());
        configuration.densityDpi=Math.max(120,Math.round(configuration.densityDpi*scale));
        return base.createConfigurationContext(configuration);
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
