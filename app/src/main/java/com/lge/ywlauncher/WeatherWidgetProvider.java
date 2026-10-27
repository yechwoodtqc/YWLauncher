package com.lge.ywlauncher;

import android.app.PendingIntent;
import android.appwidget.*;
import android.content.*;
import android.widget.RemoteViews;

public class WeatherWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) updateOne(context, manager, id);
    }

    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, WeatherWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(component);
        for (int id : ids) updateOne(context, manager, id);
    }

    private static void updateOne(Context context, AppWidgetManager manager, int id) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("launcher", 0);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.weather_widget);
        String place = prefs.getString("weather_location", "Set location in YW Weather");
        String summary = prefs.getString("weather_widget_summary", "Open YW Weather to load a forecast");
        String temperature = prefs.getString("weather_widget_temperature", "—");
        String icon = prefs.getString("weather_widget_icon", "🌤️");
        views.setTextViewText(R.id.weatherWidgetLocation, place);
        views.setTextViewText(R.id.weatherWidgetIcon, icon);
        views.setTextViewText(R.id.weatherWidgetTemperature, temperature);
        views.setTextViewText(R.id.weatherWidgetSummary, summary);
        Intent open = new Intent(context, WeatherActivity.class);
        PendingIntent pending = PendingIntent.getActivity(context, id, open,
            PendingIntent.FLAG_UPDATE_CURRENT | (android.os.Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        views.setOnClickPendingIntent(R.id.weatherWidgetRoot, pending);
        manager.updateAppWidget(id, views);
    }
}