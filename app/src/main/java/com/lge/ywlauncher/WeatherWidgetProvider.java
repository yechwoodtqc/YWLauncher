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

    private static int weatherDrawable(String forecast) {
        String value = forecast == null ? "" : forecast.toLowerCase(java.util.Locale.US);
        if (value.contains("thunder")) return R.drawable.breezy_weather_thunderstorm;
        if (value.contains("hail")) return R.drawable.breezy_weather_hail;
        if (value.contains("sleet") || value.contains("freezing")) return R.drawable.breezy_weather_sleet;
        if (value.contains("snow") || value.contains("blizzard")) return R.drawable.breezy_weather_snow;
        if (value.contains("rain") || value.contains("shower") || value.contains("drizzle")) return R.drawable.breezy_weather_rain;
        if (value.contains("fog")) return R.drawable.breezy_weather_fog;
        if (value.contains("haze") || value.contains("smoke")) return R.drawable.breezy_weather_haze;
        if (value.contains("wind")) return R.drawable.breezy_weather_wind;
        boolean night = value.contains("night") || value.contains("tonight");
        if (value.contains("partly") || value.contains("mostly")) return night
            ? R.drawable.breezy_weather_partly_cloudy_night : R.drawable.breezy_weather_partly_cloudy_day;
        if (value.contains("cloud") || value.contains("overcast")) return R.drawable.breezy_weather_cloudy;
        return night ? R.drawable.breezy_weather_clear_night : R.drawable.breezy_weather_clear_day;
    }

    private static void updateOne(Context context, AppWidgetManager manager, int id) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("launcher", 0);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.weather_widget);
        String place = prefs.getString("weather_location", "Set location in YW Weather");
        String summary = prefs.getString("weather_widget_summary", "Open YW Weather to load a forecast");
        String temperature = prefs.getString("weather_widget_temperature", "—");
        String condition = prefs.getString("weather_widget_condition", "Partly Cloudy");
        views.setTextViewText(R.id.weatherWidgetLocation, place);
        views.setImageViewResource(R.id.weatherWidgetIcon, weatherDrawable(condition));
        views.setTextViewText(R.id.weatherWidgetTemperature, temperature);
        views.setTextViewText(R.id.weatherWidgetSummary, summary);
        Intent open = new Intent(context, WeatherActivity.class);
        PendingIntent pending = PendingIntent.getActivity(context, id, open,
            PendingIntent.FLAG_UPDATE_CURRENT | (android.os.Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        views.setOnClickPendingIntent(R.id.weatherWidgetRoot, pending);
        manager.updateAppWidget(id, views);
    }
}