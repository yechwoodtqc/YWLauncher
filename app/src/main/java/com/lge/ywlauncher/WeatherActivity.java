package com.lge.ywlauncher;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.location.*;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class WeatherActivity extends Activity {
    private static final int LOCATION_PERMISSION_REQUEST = 71;
    private static final String PREFS = "launcher";
    private final Handler handler = new Handler();
    private TextView locationTitle, updated, forecasted;
    private LinearLayout forecastList, root, toolbar;
    private ScrollView forecastScroller;
    private ImageButton refreshButton, settingsButton;
    private LocationListener pendingLocationListener;
    private Runnable locationTimeout, autoRefreshRunnable;
    private boolean configureMode, loading, hourlyMode, detailMode;
    private JSONArray dailyPeriods = new JSONArray(), hourlyPeriods = new JSONArray(), activeAlerts = new JSONArray();
    private final ArrayList<DayForecast> dailyDays = new ArrayList<>();
    private static class DayForecast {
        String label;
        JSONObject day, night;
    }
    private JSONArray discussionProducts = new JSONArray();
    private int detailIndex = 0;
    private String currentPage = "forecast";
    private String lastLocationQuery = "";
    private float fontScale = 1.0f;
    private final ArrayList<SavedPlace> savedPlaces = new ArrayList<>();
    private static class SavedPlace {
        String name; double lat, lon;
        SavedPlace(String name, double lat, double lon) { this.name=name; this.lat=lat; this.lon=lon; }
        JSONObject json() throws JSONException { JSONObject o=new JSONObject(); o.put("name",name); o.put("lat",lat); o.put("lon",lon); return o; }
    }
    private android.graphics.Bitmap createTaskIcon(int resourceId) {
        android.graphics.drawable.Drawable drawable = getDrawable(resourceId);
        if (drawable == null) {
            try { drawable = getPackageManager().getActivityIcon(getComponentName()); }
            catch (Exception activityIconError) {
                try { drawable = getPackageManager().getApplicationIcon(getApplicationInfo()); }
                catch (Exception appIconError) { return null; }
            }
        }
        int size = Math.max(64, dp(48));
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
            size, size, android.graphics.Bitmap.Config.ARGB_8888);
        bitmap.setDensity(getResources().getDisplayMetrics().densityDpi);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        drawable.setBounds(0, 0, size, size);
        drawable.draw(canvas);
        return bitmap;
    }

    private void updateWeatherTaskDescription() {
        if (android.os.Build.VERSION.SDK_INT < 21) return;
        try {
            setTaskDescription(new android.app.ActivityManager.TaskDescription(
                "YW Weather", createTaskIcon(R.drawable.ic_weather), ThemeUtils.accent(this)));
        } catch (RuntimeException ignored) {
            setTaskDescription(new android.app.ActivityManager.TaskDescription("YW Weather"));
        }
    }

    private int dialogTheme() {
        return ThemeUtils.isDark(this) ? R.style.FullScreenDialogDarkTheme : R.style.FullScreenDialogTheme;
    }

    private final class FullScreenDialogBuilder extends AlertDialog.Builder {
        FullScreenDialogBuilder(Activity activity) { super(activity, ThemeUtils.isDark(activity) ? R.style.FullScreenDialogDarkTheme : R.style.FullScreenDialogTheme); }
        @Override public AlertDialog show() {
            AlertDialog dialog = super.show();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(ThemeUtils.surface(WeatherActivity.this)));
                dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                dialog.getWindow().setDimAmount(1.0f);
                dialog.getWindow().setLayout(android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.MATCH_PARENT);
            }
            return dialog;
        }
    }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private android.content.SharedPreferences prefs() { return getSharedPreferences(PREFS, 0); }
    private float textSize(float base) { return base * fontScale; }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ThemeUtils.apply(this);
        // Supply an explicit bitmap: LG Android 6 Recents does not reliably
        // fall back to the manifest icon for TaskDescription entries.
        updateWeatherTaskDescription();
        fontScale = prefs().getFloat("weather_font_scale", 1.0f);
        try { discussionProducts = new JSONArray(prefs().getString("weather_discussion_cache", "[]")); }
        catch (Exception ignored) { discussionProducts = new JSONArray(); }
        loadSavedPlaces();
        buildScreen();
        getWindow().getDecorView().post(() -> {
            if (!isFinishing()) updateWeatherTaskDescription();
        });
        if (getIntent().getBooleanExtra("configure_location", false)) showLocationsPage();
        else if ("current".equals(prefs().getString("weather_location_source", ""))) {
            // Current-location coordinates are a cache only, never a saved-place selection.
            detectLocation();
        } else {
            SavedPlace selected = selectedPlace();
            if (selected != null) fetchForecastAt(selected.lat, selected.lon);
            else if (prefs().contains("weather_lat") && prefs().contains("weather_lon")
                && !prefs().getString("weather_location", "").trim().isEmpty()
                && !"Use current location".equals(prefs().getString("weather_location", ""))) {
                fetchForecastAt(prefs().getFloat("weather_lat", 0), prefs().getFloat("weather_lon", 0));
            } else detectLocation();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            try {
                for (ActivityManager.AppTask task : ((ActivityManager)getSystemService(ACTIVITY_SERVICE)).getAppTasks())
                    task.setExcludeFromRecents(false);
            } catch (Exception ignored) { }
            // Re-apply the registered Weather activity icon after restoring
            // task visibility on LG's Android 6 task switcher.
            updateWeatherTaskDescription();
            getWindow().getDecorView().post(() -> {
                if (!isFinishing()) updateWeatherTaskDescription();
            });
        }
        scheduleAutoRefresh();
    }
    @Override protected void onPause() {
        if(autoRefreshRunnable!=null)handler.removeCallbacks(autoRefreshRunnable);
        if(locationTimeout!=null)handler.removeCallbacks(locationTimeout);
        if(pendingLocationListener!=null) {
            try { ((LocationManager)getSystemService(LOCATION_SERVICE)).removeUpdates(pendingLocationListener); }
            catch(Exception ignored) { }
            pendingLocationListener=null;
        }
        super.onPause();
    }

    private void buildScreen() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(4), dp(4), dp(4), dp(4));
        root.setBackgroundColor(ThemeUtils.surface(this));
        setContentView(root);

        toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(48)));

        forecastScroller = new ScrollView(this);
        forecastScroller.setFillViewport(true);
        forecastScroller.setSmoothScrollingEnabled(true);
        forecastList = new LinearLayout(this);
        forecastList.setOrientation(LinearLayout.VERTICAL);
        forecastScroller.addView(forecastList, new ScrollView.LayoutParams(-1, -2));
        root.addView(forecastScroller, new LinearLayout.LayoutParams(-1, 0, 1));
        showForecastPageHeader();
    }

    private void rebuildToolbar(String titleText, boolean showRefresh, boolean showSettings) {
        toolbar.removeAllViews();
        if (!"forecast".equals(currentPage)) {
            ImageButton back = new ImageButton(this);
            back.setImageResource(R.drawable.ic_back_modern);
            back.setColorFilter(ThemeUtils.text(this));
            back.setContentDescription("Back");
            back.setBackground(outlineFocusBackground());
            back.setPadding(dp(8),dp(8),dp(8),dp(8));
            back.setFocusable(true);
            back.setOnClickListener(v -> {
                if ("add-location".equals(currentPage)) showLocationsPage();
                else if ("settings-font".equals(currentPage) || "settings-refresh".equals(currentPage)) showWeatherSettings();
                else showForecastPage();
            });
            toolbar.addView(back,new LinearLayout.LayoutParams(dp(36),dp(36)));
        }
        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(ThemeUtils.text(this));
        title.setTextSize(textSize(20));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        if (showRefresh) {
            refreshButton = new ImageButton(this);
            refreshButton.setImageResource(R.drawable.ic_refresh_modern);
            refreshButton.setColorFilter(ThemeUtils.accent(this));
            refreshButton.setContentDescription("Refresh weather");
            refreshButton.setBackground(outlineFocusBackground());
            refreshButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            refreshButton.setPadding(dp(8), dp(8), dp(8), dp(8));
            refreshButton.setFocusable(true);
            refreshButton.setOnClickListener(v -> refreshCurrentPage());
            toolbar.addView(refreshButton, new LinearLayout.LayoutParams(dp(46), dp(46)));
        } else refreshButton = null;
        if (showSettings) {
            settingsButton = new ImageButton(this);
            settingsButton.setImageResource(R.drawable.ic_settings_modern);
            settingsButton.setContentDescription("Weather settings");
            settingsButton.setBackground(outlineFocusBackground());
            settingsButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            settingsButton.setPadding(dp(7), dp(7), dp(7), dp(7));
            settingsButton.setFocusable(true);
            settingsButton.setOnClickListener(v -> showWeatherSettings());
            toolbar.addView(settingsButton, new LinearLayout.LayoutParams(dp(54), dp(48)));
        } else settingsButton = null;
    }

    private android.graphics.drawable.Drawable outlineFocusBackground() {
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(Color.TRANSPARENT);
        focused.setCornerRadius(dp(9));
        focused.setStroke(dp(2), ThemeUtils.accent(this));
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(withAlpha(ThemeUtils.accent(this), 35));
        pressed.setCornerRadius(dp(9));
        pressed.setStroke(dp(1), ThemeUtils.accent(this));
        states.addState(new int[]{android.R.attr.state_pressed}, pressed);
        states.addState(new int[]{}, new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        return states;
    }

    private int withAlpha(int color, int alpha) { return (color & 0x00ffffff) | ((alpha & 0xff) << 24); }

    private void showForecastPageHeader() {
        currentPage = "forecast";
        rebuildToolbar("Weather", true, true);
        locationTitle = new TextView(this);
        locationTitle.setTextColor(ThemeUtils.accent(this));
        locationTitle.setTextSize(textSize(15));
        locationTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        locationTitle.setPadding(dp(2), 0, dp(2), dp(1));
        locationTitle.setFocusable(true);
        locationTitle.setClickable(true);
        locationTitle.setOnClickListener(v -> showLocationsPage());
        locationTitle.setOnFocusChangeListener((v, focused) -> {
            locationTitle.setBackground(focused ? outlineFocusBackground() : new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        });
        root.addView(locationTitle, root.indexOfChild(toolbar) + 1, new LinearLayout.LayoutParams(-1, -2));

        updated = new TextView(this);
        updated.setTextColor(ThemeUtils.secondaryText(this));
        updated.setTextSize(textSize(11));
        updated.setPadding(dp(4), dp(2), dp(4), dp(1));
        long cachedAt = prefs().getLong("weather_cache_saved_at", 0L);
        if (cachedAt > 0L) updated.setText("Last updated: " + relativeTimestamp(cachedAt));
        forecasted = new TextView(this);
        forecasted.setTextColor(ThemeUtils.secondaryText(this));
        forecasted.setTextSize(textSize(11));
        forecasted.setPadding(dp(4), 0, dp(4), dp(5));
        forecasted.setText("Forecast issued: " + relativeForecastTimestamp(prefs().getString("weather_cache_generated_raw", "")));
        setSelectedPlaceTitle();
    }

    private void removeHeaderRows() {
        if (locationTitle != null) root.removeView(locationTitle);
        if (updated != null) forecastList.removeView(updated);
        if (forecasted != null) forecastList.removeView(forecasted);
        locationTitle = null; updated = null; forecasted = null;
    }

    private void setSelectedPlaceTitle() {
        if (locationTitle == null) return;
        SavedPlace place = selectedPlace();
        if (place != null) locationTitle.setText(place.name + "  ▾");
        else {
            String label = prefs().getString("weather_location", "");
            locationTitle.setText(!label.isEmpty() && !"Use current location".equals(label) ? label + "  ▾" : "Choose location  ▾");
        }
    }

    private void setLoading(boolean value, String message) {
        loading = value;
        if (refreshButton != null) refreshButton.setEnabled(!value);
        if (value) {
            forecastList.removeAllViews();
            if (updated != null) forecastList.addView(updated, new LinearLayout.LayoutParams(-1, -2));
            if (forecasted != null) forecastList.addView(forecasted, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout holder = new LinearLayout(this);
            holder.setOrientation(LinearLayout.VERTICAL);
            holder.setGravity(Gravity.CENTER);
            holder.setPadding(dp(10), dp(40), dp(10), dp(40));
            WeatherLoadingView animation = new WeatherLoadingView(this);
            holder.addView(animation, new LinearLayout.LayoutParams(dp(104), dp(92)));
            TextView label = new TextView(this);
            label.setText(message == null ? "Updating forecast…" : message);
            label.setTextColor(ThemeUtils.secondaryText(this));
            label.setTextSize(textSize(14));
            label.setGravity(Gravity.CENTER);
            label.setPadding(0, dp(12), 0, 0);
            holder.addView(label);
            forecastList.addView(holder, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private final class WeatherLoadingView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private long start = System.currentTimeMillis();
        WeatherLoadingView(Context c) { super(c); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx=getWidth()/2f, cy=getHeight()/2f;
            float phase=((System.currentTimeMillis()-start)%1800L)/1800f;
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(3)); paint.setColor(ThemeUtils.accent(WeatherActivity.this));
            canvas.save(); canvas.rotate(phase*360f,cx,cy);
            for(int i=0;i<8;i++){ canvas.save(); canvas.rotate(i*45,cx,cy); canvas.drawLine(cx,cy-dp(36),cx,cy-dp(26),paint); canvas.restore(); }
            canvas.restore();
            paint.setStyle(Paint.Style.FILL); paint.setColor(0xffffc857);
            canvas.drawCircle(cx-dp(13),cy-dp(4),dp(17),paint);
            paint.setColor(ThemeUtils.surface(WeatherActivity.this));
            canvas.drawCircle(cx+dp(7),cy+dp(9),dp(17),paint);
            canvas.drawCircle(cx-dp(8),cy+dp(11),dp(13),paint);
            canvas.drawCircle(cx+dp(22),cy+dp(10),dp(10),paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(2)); paint.setColor(ThemeUtils.accent(WeatherActivity.this));
            canvas.drawArc(cx-dp(29),cy-dp(29),cx+dp(29),cy+dp(29),phase*360,220,false,paint);
            postInvalidateDelayed(45);
        }
    }

    private void refreshCurrentPage() {
        if ("settings".equals(currentPage)) { showWeatherSettings(); return; }
        if ("locations".equals(currentPage)) { showLocationsPage(); return; }
        if ("discussion".equals(currentPage)) {
            if ("current".equals(prefs().getString("weather_location_source", ""))) detectLocation();
            else { SavedPlace p=selectedPlace(); if(p!=null)fetchForecastAt(p.lat,p.lon); }
            return;
        }
        if ("current".equals(prefs().getString("weather_location_source", ""))) {
            detectLocation();
            return;
        }
        SavedPlace place = selectedPlace();
        if (place != null) fetchForecastAt(place.lat, place.lon);
        else if (prefs().contains("weather_lat") && prefs().contains("weather_lon")) fetchForecastAt(prefs().getFloat("weather_lat",0),prefs().getFloat("weather_lon",0));
        else detectLocation();
    }

    private boolean showCachedForecastIfFresh(double latitude, double longitude) {
        SharedPreferences p = prefs();
        long savedAt = p.getLong("weather_cache_saved_at", 0L);
        boolean samePlace = p.contains("weather_data_lat") && p.contains("weather_data_lon")
            && Math.abs(p.getFloat("weather_data_lat", 999f) - latitude) < 0.03d
            && Math.abs(p.getFloat("weather_data_lon", 999f) - longitude) < 0.03d;
        if (!samePlace || savedAt <= 0L || System.currentTimeMillis() - savedAt > 24L * 60L * 60L * 1000L) return false;
        try {
            dailyPeriods = new JSONArray(p.getString("weather_cache_daily", "[]"));
            hourlyPeriods = new JSONArray(p.getString("weather_cache_hourly", "[]"));
            activeAlerts = new JSONArray(p.getString("weather_cache_alerts", "[]"));
            if (dailyPeriods.length() == 0) return false;
            buildDailyDays(dailyPeriods);
            renderForecast();
            if (updated != null) updated.setText("Last updated: " + relativeTimestamp(savedAt));
            if (forecasted != null) forecasted.setText("Forecast issued: " + relativeForecastTimestamp(p.getString("weather_cache_generated_raw", "")));
            return true;
        } catch (Exception ignored) { return false; }
    }

    private String relativeTimestamp(long timestamp) {
        long elapsed = Math.max(0L, System.currentTimeMillis() - timestamp);
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(timestamp);
        Calendar today = Calendar.getInstance();
        Calendar yesterday = (Calendar) today.clone();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        SimpleDateFormat clock = new SimpleDateFormat("h:mm a", Locale.getDefault());
        if (then.get(Calendar.YEAR) == today.get(Calendar.YEAR)
            && then.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR))
            return "Today at " + clock.format(new Date(timestamp));
        if (then.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR)
            && then.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR))
            return "Yesterday at " + clock.format(new Date(timestamp));
        if (elapsed < 60L * 1000L) return "Just now";
        if (elapsed < 60L * 60L * 1000L) {
            long minutes = Math.max(1L, elapsed / (60L * 1000L));
            return minutes == 1 ? "1 minute ago" : minutes + " minutes ago";
        }
        if (elapsed < 24L * 60L * 60L * 1000L) {
            long hours = Math.max(1L, elapsed / (60L * 60L * 1000L));
            return hours == 1 ? "an hour ago" : hours + " hours ago";
        }
        return new SimpleDateFormat("MMM d 'at' h:mm a", Locale.getDefault()).format(new Date(timestamp));
    }

    private String relativeForecastTimestamp(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "time not provided";
        try {
            String normalized = raw.replaceFirst("([+-]\\d{2}):(\\d{2})$", "$1$2").replaceFirst("Z$", "+0000");
            normalized = normalized.replaceFirst("\\.\\d+(?=[+-]\\d{4}$)", "");
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US);
            Date parsed = parser.parse(normalized);
            return parsed == null ? raw : relativeTimestamp(parsed.getTime());
        } catch (Exception ignored) { return raw; }
    }

    private void showNewForecastNotification() {
        if (!prefs().getBoolean("weather_new_forecast_notifications", true)) return;
        try {
            Intent open = new Intent(this, WeatherActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pending = PendingIntent.getActivity(this, 77, open,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
            Notification.Builder builder = new Notification.Builder(this)
                .setSmallIcon(R.drawable.ic_weather_notification)
                .setLargeIcon(NotificationIconUtils.createLargeIcon(this, R.drawable.ic_weather))
                .setContentTitle("New weather forecast")
                .setContentText("The forecast has been updated.")
                .setContentIntent(pending).setAutoCancel(true).setWhen(System.currentTimeMillis());
            if (Build.VERSION.SDK_INT >= 21) builder.setColor(ThemeUtils.accent(this));
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(77, builder.build());
        } catch (Exception ignored) { }
    }

    private void fetchForecastAt(double latitude, double longitude) {
        boolean showedCache = showCachedForecastIfFresh(latitude, longitude);
        setLoading(!showedCache, showedCache ? null : "Loading forecast…");
        new Thread(() -> {
            ExecutorService requests = Executors.newFixedThreadPool(3);
            try {
                SharedPreferences p = prefs();
                String dailyUrl = "";
                String hourlyUrl = "";
                String office = "";
                boolean cacheValid = p.contains("nws_cache_lat") && p.contains("nws_cache_lon")
                    && Math.abs(p.getFloat("nws_cache_lat", 999f) - latitude) < 0.03d
                    && Math.abs(p.getFloat("nws_cache_lon", 999f) - longitude) < 0.03d;
                if (cacheValid) {
                    dailyUrl = p.getString("nws_forecast_url", "");
                    hourlyUrl = p.getString("nws_hourly_url", "");
                    office = p.getString("nws_office", "");
                }
                if (dailyUrl.isEmpty()) {
                    JSONObject point = getJson("https://api.weather.gov/points/" + latitude + "," + longitude);
                    JSONObject properties = point.getJSONObject("properties");
                    dailyUrl = properties.getString("forecast");
                    hourlyUrl = properties.optString("forecastHourly", "");
                    office = properties.optString("cwa", "");
                    p.edit().putFloat("nws_cache_lat", (float) latitude).putFloat("nws_cache_lon", (float) longitude)
                        .putString("nws_forecast_url", dailyUrl).putString("nws_hourly_url", hourlyUrl)
                        .putString("nws_office", office).apply();
                }
                final String forecastUrl = dailyUrl;
                final String hourlyForecastUrl = hourlyUrl;
                final String forecastOffice = office;
                Future<JSONObject> dailyRequest = requests.submit(() -> getJson(forecastUrl));
                Future<JSONObject> hourlyRequest = hourlyForecastUrl.isEmpty() ? null : requests.submit(() -> getJson(hourlyForecastUrl));
                Future<JSONObject> alertsRequest = requests.submit(() ->
                    getJson("https://api.weather.gov/alerts/active?point=" + latitude + "," + longitude));

                JSONObject daily = dailyRequest.get(25, TimeUnit.SECONDS);
                JSONArray days = daily.getJSONObject("properties").getJSONArray("periods");
                String generated = daily.getJSONObject("properties").optString("updateTime", "");
                if (generated.trim().isEmpty()) generated = daily.getJSONObject("properties").optString("generatedAt", "");

                JSONArray hours = new JSONArray();
                if (hourlyRequest != null) {
                    try { hours = hourlyRequest.get(15, TimeUnit.SECONDS).getJSONObject("properties").getJSONArray("periods"); }
                    catch (Exception ignored) { }
                }
                JSONArray alerts = new JSONArray();
                try {
                    JSONObject response = alertsRequest.get(15, TimeUnit.SECONDS);
                    JSONArray features = response.optJSONArray("features");
                    if (features != null) for (int i = 0; i < features.length(); i++) {
                        JSONObject feature = features.optJSONObject(i);
                        JSONObject alert = feature == null ? null : feature.optJSONObject("properties");
                        if (alert != null) alerts.put(alert);
                    }
                } catch (Exception ignored) { }

                JSONArray finalDays = days;
                JSONArray finalHours = hours;
                JSONArray finalAlerts = alerts;
                String finalGenerated = generated;
                handler.post(() -> {
                    String previousIssuedTime = prefs().getString("weather_cache_generated_raw", "");
                    boolean forecastChanged = !finalGenerated.isEmpty()
                        && !previousIssuedTime.isEmpty()
                        && !previousIssuedTime.equals(finalGenerated);
                    dailyPeriods = finalDays;
                    hourlyPeriods = finalHours;
                    activeAlerts = finalAlerts;
                    buildDailyDays(finalDays);
                    setLoading(false, null);
                    renderForecast();
                    long savedAt = System.currentTimeMillis();
                    if (updated != null) updated.setText("Last updated: " + relativeTimestamp(savedAt));
                    String forecastTime = finalGenerated;
                    if (!forecastTime.isEmpty()) {
                        try {
                            String normalizedTime = forecastTime.replaceFirst("([+-]\\d{2}):(\\d{2})$", "$1$2").replaceFirst("Z$", "+0000");
                            normalizedTime = normalizedTime.replaceFirst("\\.\\d+(?=[+-]\\d{4}$)", "");
                            java.text.SimpleDateFormat input = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US);
                            Date date = input.parse(normalizedTime);
                            forecastTime = date == null ? forecastTime : new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(date);
                        } catch (Exception ignored) { }
                    }
                    if (forecasted != null) forecasted.setText("Forecast issued: " + relativeForecastTimestamp(finalGenerated));
                    String fingerprint = finalDays.toString() + "|" + finalHours.toString();
                    prefs().edit()
                        .putString("weather_cache_daily", finalDays.toString())
                        .putString("weather_cache_hourly", finalHours.toString())
                        .putString("weather_cache_alerts", finalAlerts.toString())
                        .putLong("weather_cache_saved_at", savedAt)
                        .putFloat("weather_data_lat", (float) latitude)
                        .putFloat("weather_data_lon", (float) longitude)
                        .putString("weather_cache_issued", forecastTime)
                        .putString("weather_cache_generated_raw", finalGenerated)
                        .putString("weather_cache_fingerprint", fingerprint)
                        .apply();
                    if (forecastChanged) showNewForecastNotification();
                    JSONObject first = finalDays.optJSONObject(0);
                    JSONObject currentPeriod = finalHours.optJSONObject(0);
                    String widgetForecast = currentPeriod == null
                        ? (first == null ? "" : first.optString("shortForecast", ""))
                        : currentPeriod.optString("shortForecast", "");
                    boolean widgetDaytime = currentPeriod == null || currentPeriod.optBoolean("isDaytime", true);
                    prefs().edit().putString("weather_widget_summary", first == null ? "Forecast ready" : first.optString("shortForecast", "Forecast ready"))
                        .putString("weather_widget_temperature", first == null ? "—" : first.optString("temperature", "—") + "°" + first.optString("temperatureUnit", "F"))
                        .putString("weather_widget_icon", first == null ? "🌤️" : weatherEmoji(widgetForecast, widgetDaytime))
                        .apply();
                    WeatherWidgetProvider.updateAll(this);
                    fetchDiscussion(forecastOffice);
                    scheduleAutoRefresh();
                });
            } catch (Exception e) {
                handler.post(() -> {
                    setLoading(false, null);
                    if (showedCache) {
                        if (updated != null) updated.setText("Last updated: " +
                            relativeTimestamp(prefs().getLong("weather_cache_saved_at", System.currentTimeMillis())) + " (offline copy)");
                    } else {
                        showWeatherError("Failed to load...");
                    }
                });
            } finally {
                requests.shutdownNow();
            }
        }, "YWWeatherFetch").start();
    }

    private void renderForecast() {
        if (!"forecast".equals(currentPage)) return;
        forecastList.removeAllViews();
        if (updated != null) forecastList.addView(updated, new LinearLayout.LayoutParams(-1, -2));
        if (forecasted != null) forecastList.addView(forecasted, new LinearLayout.LayoutParams(-1, -2));
        if (activeAlerts.length() > 0) {
            addSectionTitle("ACTIVE WEATHER ALERTS");
            for (int i = 0; i < Math.min(4, activeAlerts.length()); i++) {
                JSONObject alert = activeAlerts.optJSONObject(i);
                if (alert == null) continue;
                String event = alert.optString("event", "Weather alert");
                String headline = alert.optString("headline", alert.optString("description", "See alert details"));
                String severity = alert.optString("severity", "Alert");
                LinearLayout warning = new LinearLayout(this);
                warning.setOrientation(LinearLayout.VERTICAL);
                warning.setPadding(dp(12), dp(10), dp(12), dp(10));
                GradientDrawable warningBg = new GradientDrawable();
                warningBg.setColor(ThemeUtils.isDark(this) ? 0xff40231f : 0xfffff0ed);
                warningBg.setCornerRadius(dp(9));
                warningBg.setStroke(dp(2), 0xffd94b3d);
                warning.setBackground(warningBg);
                warning.setFocusable(true); warning.setClickable(true);
                TextView eventText = text("⚠ " + event + " · " + severity, 14, true);
                eventText.setTextColor(ThemeUtils.isDark(this) ? 0xffffb3a9 : 0xff9e241a);
                warning.addView(eventText);
                TextView headlineText = text(headline, 12, false);
                headlineText.setPadding(0, dp(4), 0, 0);
                warning.addView(headlineText);
                final JSONObject selectedAlert = alert;
                warning.setOnClickListener(v -> new FullScreenDialogBuilder(this)
                    .setTitle(selectedAlert.optString("event", "Weather alert"))
                    .setMessage(selectedAlert.optString("headline", "") + "\n\n" + selectedAlert.optString("description", ""))
                    .setPositiveButton("Close", null).show());
                warning.setOnFocusChangeListener((v, focused) -> warning.setBackground(focused ? ThemeUtils.focusOutlineBackground(this) : warningBg));
                forecastList.addView(warning, rowMargins());
            }
        }
        addCurrentSummary();
        addSectionTitle("Hourly forecast");
        HorizontalScrollView strip=new HorizontalScrollView(this);strip.setHorizontalScrollBarEnabled(false);
        LinearLayout hours=new LinearLayout(this);hours.setOrientation(LinearLayout.HORIZONTAL);
        for(int i=0;i<Math.min(18,hourlyPeriods.length());i++) {
            JSONObject period=hourlyPeriods.optJSONObject(i);if(period==null)continue;final int hourIndex=i;
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setGravity(Gravity.CENTER);
            card.setPadding(dp(9),dp(8),dp(9),dp(8));card.setMinimumWidth(dp(82));card.setBackground(ThemeUtils.focusBackground(this));card.setFocusable(true);card.setClickable(true);
            String start=period.optString("startTime","");String time=formatForecastTime(start);
            card.addView(text(time,12,true));card.addView(weatherIcon(period.optString("shortForecast","") + (period.optBoolean("isDaytime", true) ? "" : " night"),40),new LinearLayout.LayoutParams(dp(52),dp(46)));
            card.addView(text(period.optString("temperature","—")+"°",15,true));
            JSONObject hourlyChance=period.optJSONObject("probabilityOfPrecipitation");
            card.addView(text("Rain "+(hourlyChance==null||hourlyChance.isNull("value")?"—":hourlyChance.optString("value")+"%"),11,false));
            card.setOnClickListener(v->showHourDetail(hourIndex));
            card.setOnFocusChangeListener((v,f)->card.setBackground(f?ThemeUtils.focusOutlineBackground(this):ThemeUtils.focusBackground(this)));
            hours.addView(card,new LinearLayout.LayoutParams(-2,dp(106)));
        }
        strip.addView(hours);forecastList.addView(strip,rowMargins());
        addSectionTitle("Daily forecast · select a day for details");
        for(int i=0;i<Math.min(8,dailyDays.size());i++)addDailyPeriod(dailyDays.get(i),i);
        addDiscussionPreview();
    }

    private void addCurrentSummary() {
        JSONObject current = hourlyPeriods.length()>0 ? hourlyPeriods.optJSONObject(0) : (dailyPeriods.length()>0 ? dailyPeriods.optJSONObject(0) : null);
        if(current==null)return;
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(14),dp(12),dp(14),dp(12)); card.setBackground(cardBackground());
        LinearLayout nowRow = new LinearLayout(this);
        nowRow.setGravity(Gravity.CENTER_VERTICAL);
        nowRow.addView(weatherIcon(current.optString("shortForecast","") + (current.optBoolean("isDaytime", true) ? "" : " night"),48),new LinearLayout.LayoutParams(dp(58),dp(58)));
        TextView heading = text("NOW  "+current.optString("temperature","—")+"°"+current.optString("temperatureUnit","F"),22,true);
        nowRow.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        card.addView(nowRow);
        card.addView(text(current.optString("shortForecast","Current conditions"),14,false));
        String humidity="—",precip="—";
        JSONObject hum=current.optJSONObject("relativeHumidity"); if(hum!=null&&!hum.isNull("value"))humidity=hum.optString("value")+"%";
        JSONObject pp=current.optJSONObject("probabilityOfPrecipitation"); if(pp!=null&&!pp.isNull("value"))precip=pp.optString("value")+"%";
        card.addView(text("Wind "+current.optString("windSpeed","—")+" "+current.optString("windDirection","")+"   Humidity "+humidity+"   Precip. "+precip,12,false));
        forecastList.addView(card,rowMargins());
    }

    private void buildDailyDays(JSONArray periods) {
        dailyDays.clear();
        for(int i=0;i<periods.length();i++) {
            JSONObject period=periods.optJSONObject(i);
            if(period==null)continue;
            boolean daytime=period.optBoolean("isDaytime",true);
            if(daytime) {
                DayForecast group=new DayForecast();
                group.day=period;group.label=period.optString("name","Day");
                if(i+1<periods.length()) {
                    JSONObject next=periods.optJSONObject(i+1);
                    if(next!=null&&!next.optBoolean("isDaytime",true)){group.night=next;i++;}
                }
                dailyDays.add(group);
            } else {
                DayForecast group=new DayForecast();
                group.night=period;group.label=period.optString("name","Tonight");
                dailyDays.add(group);
            }
        }
    }

    private void addDailyPeriod(DayForecast day,int index) {
        if (day == null) return;
        if (day.day != null) addDailyEntry(day.day, day.label, "High", index);
        if (day.night != null) addDailyEntry(day.night,
            day.night.optString("name", "Tonight"), "Low", index);
    }

    private void addDailyEntry(JSONObject period, String label, String range, int index) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(8), dp(7), dp(8), dp(7));
        card.setBackground(ThemeUtils.focusBackground(this));
        card.setFocusable(true);
        card.setClickable(true);
        card.addView(weatherIcon(period.optString("shortForecast", "") + (period.optBoolean("isDaytime", true) ? "" : " night"), 48),
            new LinearLayout.LayoutParams(dp(56), dp(56)));
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(8), 0, dp(3), 0);
        info.addView(text(label, 14, true));
        info.addView(text(range + " " + period.optString("temperature", "—") + "°"
            + period.optString("temperatureUnit", "F") + " · "
            + period.optString("shortForecast", ""), 12, false));
        JSONObject pp = period.optJSONObject("probabilityOfPrecipitation");
        String precip = pp == null || pp.isNull("value") ? "—" : pp.optString("value") + "%";
        info.addView(text("Wind " + period.optString("windSpeed", "—") + " "
            + period.optString("windDirection", "") + " · Precip. " + precip, 11, false));
        card.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(text("›", 24, false), new LinearLayout.LayoutParams(dp(22), -2));
        card.setOnClickListener(v -> { detailIndex = index; showDayDetail(index); });
        card.setOnFocusChangeListener((v, focused) ->
            card.setBackground(focused ? outlineFocusBackground() : ThemeUtils.focusBackground(this)));
        forecastList.addView(card, rowMargins());
    }

    private String formatForecastTime(String iso) {
        if(iso==null||iso.length()<16)return "Hour";
        try {
            String value=iso.replaceFirst("([+-]\\d{2}):(\\d{2})$","$1$2").replaceFirst("Z$","+0000");
            java.text.SimpleDateFormat input=new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ",Locale.US);
            Date date=input.parse(value);
            return date==null?iso.substring(11,16):new java.text.SimpleDateFormat("h:mm a",Locale.getDefault()).format(date);
        } catch(Exception e) { return iso.substring(11,16); }
    }

    private void addHourlyPeriod(JSONObject period,int index) {
        if(period==null)return;
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.HORIZONTAL);card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(8),dp(8),dp(8),dp(8));card.setBackground(ThemeUtils.focusBackground(this));card.setFocusable(true);card.setClickable(true);
        card.addView(text(weatherEmoji(period.optString("shortForecast",""), period.optBoolean("isDaytime", true)),25,true),new LinearLayout.LayoutParams(dp(42),dp(44)));
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(dp(7),0,0,0);
        String time=formatForecastTime(period.optString("startTime",""));
        info.addView(text(time+" · "+period.optString("temperature","—")+"°"+period.optString("temperatureUnit","F"),14,true));
        info.addView(text(period.optString("shortForecast",""),12,false));
        JSONObject pp=period.optJSONObject("probabilityOfPrecipitation");
        info.addView(text("Wind "+period.optString("windSpeed","—")+" "+period.optString("windDirection","")+" · Precip. "+(pp==null||pp.isNull("value")?"—":pp.optString("value")+"%"),11,false));
        card.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        card.setOnClickListener(v->showHourDetail(index));
        card.setOnFocusChangeListener((v,focused)->card.setBackground(focused?ThemeUtils.focusOutlineBackground(this):ThemeUtils.focusBackground(this)));
        forecastList.addView(card,rowMargins());
    }

    private void showDayDetail(int index) {
        if(index<0||index>=dailyDays.size())return;
        detailMode=true;currentPage="day-detail";detailIndex=index;
        if(forecastScroller!=null)forecastScroller.post(()->forecastScroller.scrollTo(0,0));
        DayForecast day=dailyDays.get(index);
        rebuildToolbar(day.label,false,false);
        forecastList.removeAllViews();

        JSONObject primary=day.day!=null?day.day:day.night;
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER);
        hero.setPadding(dp(14),dp(18),dp(14),dp(18));
        hero.setBackground(cardBackground());
        hero.addView(weatherIcon(primary.optString("shortForecast","") + (primary.optBoolean("isDaytime", true) ? "" : " night"),76),new LinearLayout.LayoutParams(dp(90),dp(86)));
        TextView dayTitle = text(day.label,22,true);
        dayTitle.setGravity(Gravity.CENTER);
        hero.addView(dayTitle);
        TextView summary = text(primary.optString("shortForecast","Forecast details"),14,false);
        summary.setGravity(Gravity.CENTER);
        hero.addView(summary);
        forecastList.addView(hero,rowMargins());
        if(day.day!=null){
            forecastList.addView(text("Day · "+day.day.optString("temperature","—")+"°"+day.day.optString("temperatureUnit","F")+" · "+day.day.optString("shortForecast",""),16,true));
            forecastList.addView(text(day.day.optString("detailedForecast","No daytime details available."),textSize(14),false));
            addDetailLine("Daytime wind",day.day.optString("windSpeed","—")+" "+day.day.optString("windDirection",""));
            JSONObject pp=day.day.optJSONObject("probabilityOfPrecipitation");
            addDetailLine("Daytime precipitation chance",pp==null||pp.isNull("value")?"Not available":pp.optString("value")+"%");
        }
        if(day.night!=null){
            forecastList.addView(text("Night · "+day.night.optString("temperature","—")+"°"+day.night.optString("temperatureUnit","F")+" · "+day.night.optString("shortForecast",""),16,true));
            forecastList.addView(text(day.night.optString("detailedForecast","No nighttime details available."),textSize(14),false));
            addDetailLine("Nighttime wind",day.night.optString("windSpeed","—")+" "+day.night.optString("windDirection",""));
            JSONObject pp=day.night.optJSONObject("probabilityOfPrecipitation");
            addDetailLine("Nighttime precipitation chance",pp==null||pp.isNull("value")?"Not available":pp.optString("value")+"%");
        }

    }

    private void showHourDetail(int index) {
        if (index < 0 || index >= hourlyPeriods.length()) return;
        JSONObject p=hourlyPeriods.optJSONObject(index); if(p==null)return;
        JSONObject chance=p.optJSONObject("probabilityOfPrecipitation");
        String rain=chance==null||chance.isNull("value")?"Not available":chance.optString("value")+"%";
        new AlertDialog.Builder(this, dialogTheme()).setTitle(weatherEmoji(p.optString("shortForecast",""), p.optBoolean("isDaytime", true))+" "+p.optString("shortForecast","Hourly details"))
            .setMessage(formatForecastTime(p.optString("startTime",""))+"\n"+p.optString("temperature","—")+"°"+p.optString("temperatureUnit","F")+"\nPrecipitation chance: "+rain+"\nWind: "+p.optString("windSpeed","—")+" "+p.optString("windDirection","")+"\n"+p.optString("detailedForecast",""))
            .setPositiveButton("OK",null).show();
    }

    private void addDiscussionPreview() {
        TextView title=text("FORECAST DISCUSSION",13,true);title.setPadding(dp(10),dp(14),dp(10),dp(4));forecastList.addView(title);
        TextView preview=text(discussionProducts.length()>0 ? "Read the latest forecast discussion  ›" : "Discussion is loading…",13,true);
        preview.setPadding(dp(12),dp(12),dp(12),dp(12));preview.setBackground(ThemeUtils.focusBackground(this));
        preview.setFocusable(true);preview.setClickable(true);
        preview.setOnFocusChangeListener((v,f)->preview.setBackground(f?ThemeUtils.focusOutlineBackground(this):ThemeUtils.focusBackground(this)));
        forecastList.addView(preview,rowMargins());
        preview.setOnClickListener(v->showDiscussionPage());
    }

    private void fetchDiscussion(String office) {
        if(office==null||office.isEmpty())return;
        new Thread(()->{
            try {
                JSONObject list=getJson("https://api.weather.gov/products/types/AFD/locations/"+office);
                JSONArray products=list.optJSONArray("@graph");
                JSONArray result=new JSONArray();
                if(products!=null&&products.length()>0){
                    JSONObject first=products.optJSONObject(0);
                    String url=first.optString("@id","");
                    if(!url.isEmpty()){
                        JSONObject product=getJson(url);
                        String body=product.optString("productText","");
                        JSONObject item=new JSONObject();item.put("name",first.optString("issuanceTime","Latest discussion"));
                        item.put("text",body);item.put("preview",body.length()>360?body.substring(0,360)+"…":body);result.put(item);
                    }
                }
                handler.post(()->{
                    // Never replace a valid offline copy with an empty or
                    // incomplete discussion response.
                    if (result.length() > 0) {
                        discussionProducts=result;
                        prefs().edit().putString("weather_discussion_cache", result.toString())
                            .putLong("weather_discussion_saved_at", System.currentTimeMillis()).apply();
                    } else if (discussionProducts.length() == 0) {
                        try { discussionProducts = new JSONArray(prefs().getString("weather_discussion_cache", "[]")); }
                        catch (Exception ignored) { discussionProducts = new JSONArray(); }
                    }
                    if("forecast".equals(currentPage)&&!hourlyMode)renderForecast();
                    if("discussion".equals(currentPage)) showDiscussionPage();
                });
            }catch(Exception ignored){}
        }).start();
    }

    private void showDiscussionPage() {
        currentPage="discussion";rebuildToolbar("Forecast discussion",false,false);forecastList.removeAllViews();
        if(forecastScroller!=null)forecastScroller.post(()->forecastScroller.scrollTo(0,0));
        if (discussionProducts.length()==0) {
            try { discussionProducts = new JSONArray(prefs().getString("weather_discussion_cache", "[]")); }
            catch (Exception ignored) { discussionProducts = new JSONArray(); }
        }
        String body=discussionProducts.length()>0?discussionProducts.optJSONObject(0).optString("text","No discussion available."):"Forecast discussion is not available yet. Connect to the internet to download it.";
        TextView content=text(body,textSize(13),false);content.setPadding(dp(6),dp(8),dp(6),dp(24));content.setFocusable(false);content.setTextIsSelectable(false);content.setLineSpacing(dp(3),1.08f);forecastList.addView(content,new LinearLayout.LayoutParams(-1,-2));
        TextView footer=text("End of forecast discussion",11,false);footer.setGravity(Gravity.CENTER);footer.setPadding(0,dp(18),0,dp(24));forecastList.addView(footer);
    }

    private void showForecastPage() {
        currentPage="forecast";removeHeaderRows();
        // Restore the three forecast header rows immediately after the toolbar.
        showForecastPageHeader();
        renderForecast();
        SavedPlace place=selectedPlace();
        setSelectedPlaceTitle();
        if(dailyPeriods.length()==0 && hourlyPeriods.length()==0) {
            String savedLocation=prefs().getString("weather_location","");
            if("Use current location".equals(savedLocation) || (!prefs().contains("weather_lat") && place==null)) detectLocation();
            else if(place!=null) fetchForecastAt(place.lat,place.lon);
            else if(prefs().contains("weather_lat") && prefs().contains("weather_lon")) fetchForecastAt(prefs().getFloat("weather_lat",0),prefs().getFloat("weather_lon",0));
            else detectLocation();
        }
    }

    private void addDetailLine(String label,String value) {
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(dp(8),dp(7),dp(8),dp(7));
        row.addView(text(label,12,true));row.addView(text(value,14,false));forecastList.addView(row);View divider=new View(this);divider.setBackgroundColor(ThemeUtils.divider(this));forecastList.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
    }

    private void showLocationsPage() {
        currentPage="locations";detailMode=false;removeHeaderRows();rebuildToolbar("Weather locations",false,false);forecastList.removeAllViews();
        LinearLayout add=new LinearLayout(this);add.setGravity(Gravity.CENTER_VERTICAL);add.setFocusable(true);add.setClickable(true);add.setBackground(ThemeUtils.focusBackground(this));add.setPadding(dp(10),dp(10),dp(10),dp(10));
        TextView plus=text("＋",28,true);plus.setTextColor(ThemeUtils.accent(this));add.addView(plus,new LinearLayout.LayoutParams(dp(46),dp(46)));
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);labels.addView(text("Add a location",15,true));labels.addView(text("Search by city, state or ZIP code",12,false));add.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        add.setOnClickListener(v->showAddLocationPage(""));forecastList.addView(add,rowMargins());
        for(SavedPlace place:new ArrayList<>(savedPlaces)){
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(10),dp(10),dp(8),dp(10));row.setFocusable(true);row.setClickable(true);row.setBackground(ThemeUtils.focusBackground(this));
            LinearLayout textCol=new LinearLayout(this);textCol.setOrientation(LinearLayout.VERTICAL);textCol.addView(text(place.name,15,true));textCol.addView(text("Tap to use this forecast",11,false));row.addView(textCol,new LinearLayout.LayoutParams(0,-2,1));
            TextView check=text(place.name.equals(selectedPlaceName())?"✓":"›",22,true);check.setTextColor(ThemeUtils.accent(this));row.addView(check);
            row.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this, dialogTheme()).setTitle("Remove saved location?")
                    .setMessage("Remove " + place.name + " from saved locations?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Remove", (d,w) -> {
                        savedPlaces.remove(place); persistSavedPlaces();
                        if (place.name.equals(selectedPlaceName())) prefs().edit().putString("weather_selected_location","").apply();
                        showLocationsPage();
                    }).show();
                return true;
            });
            row.setOnClickListener(v->{
                prefs().edit().putString("weather_selected_location",place.name).putString("weather_location_source","saved").putString("weather_location",place.name)
                    .putFloat("weather_lat",(float)place.lat).putFloat("weather_lon",(float)place.lon).apply();
                dailyPeriods=new JSONArray();hourlyPeriods=new JSONArray();dailyDays.clear();
                showForecastPage();
            });
            row.setOnFocusChangeListener((v,focused)->row.setBackground(focused?ThemeUtils.focusOutlineBackground(this):ThemeUtils.focusBackground(this)));
            forecastList.addView(row,rowMargins());
        }
        Button current=new Button(this);current.setText("Use current location");ThemeUtils.tint(current,this);current.setOnClickListener(v->{
            prefs().edit().putString("weather_selected_location","").putString("weather_location","Use current location").apply();
            dailyPeriods=new JSONArray();hourlyPeriods=new JSONArray();dailyDays.clear();showForecastPage();
        });forecastList.addView(current,rowMargins());
        
    }

    private void showAddLocationPage(String query) {
        currentPage="add-location";rebuildToolbar("Add location",false,false);forecastList.removeAllViews();
        TextView prompt=text("Enter a US city, state or ZIP. Choose a suggestion to save it.",12,false);forecastList.addView(prompt);
        EditText input=new EditText(this);input.setSingleLine(true);input.setHint("City, state or ZIP code");input.setTextColor(ThemeUtils.text(this));input.setHintTextColor(ThemeUtils.secondaryText(this));input.setText(query);
        forecastList.addView(input,new LinearLayout.LayoutParams(-1,dp(52)));
        LinearLayout suggestions=new LinearLayout(this);suggestions.setOrientation(LinearLayout.VERTICAL);forecastList.addView(suggestions,new LinearLayout.LayoutParams(-1,-2));
        TextView status=text("Start typing to search locations…",12,false);suggestions.addView(status);
        final Runnable[] pending={null};
        input.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){
                if(pending[0]!=null)handler.removeCallbacks(pending[0]);
                String q=s.toString().trim();lastLocationQuery=q;suggestions.removeAllViews();
                if(q.length()<2){TextView hint=text("Type at least 2 characters.",12,false);suggestions.addView(hint);return;}
                TextView searching=text("Searching suggestions…",12,false);suggestions.addView(searching);
                pending[0]=()->searchLocationSuggestions(q,suggestions);
                handler.postDelayed(pending[0],450);
            }
            @Override public void afterTextChanged(Editable e){}
        });
        Button current=new Button(this);current.setText("Use current location");ThemeUtils.tint(current,this);current.setOnClickListener(v->{
            prefs().edit().putString("weather_location","Use current location").putString("weather_selected_location","").putString("weather_location_source","current").apply();
            dailyPeriods=new JSONArray();hourlyPeriods=new JSONArray();dailyDays.clear();showForecastPage();
        });forecastList.addView(current,rowMargins());
        Button cancel=new Button(this);cancel.setText("Cancel");ThemeUtils.tint(cancel,this);cancel.setOnClickListener(v->showLocationsPage());forecastList.addView(cancel,rowMargins());
        input.requestFocus();
    }

    private void searchLocationSuggestions(String query,LinearLayout suggestions) {
        new Thread(()->{
            ArrayList<SavedPlace> found=new ArrayList<>();
            try{
                if(query.matches("\\d{5}")){
                    JSONObject zip=getJson("https://api.zippopotam.us/us/"+query);
                    JSONArray places=zip.getJSONArray("places");
                    for(int i=0;i<places.length();i++){JSONObject p=places.optJSONObject(i);found.add(new SavedPlace(p.optString("place name")+", "+p.optString("state abbreviation"),Double.parseDouble(p.getString("latitude")),Double.parseDouble(p.getString("longitude"))));}
                }else{
                    String url="https://geocoding.geo.census.gov/geocoder/locations/onelineaddress?address="+URLEncoder.encode(query,"UTF-8")+"&benchmark=Public_AR_Current&format=json";
                    try{
                        JSONArray matches=getJson(url).getJSONObject("result").getJSONObject("addressMatches").getJSONArray("matches");
                        for(int i=0;i<Math.min(5,matches.length());i++){
                            JSONObject match=matches.optJSONObject(i);JSONObject coords=match.getJSONObject("coordinates");String name=match.optString("matchedAddress",query);
                            found.add(new SavedPlace(name,coords.getDouble("y"),coords.getDouble("x")));
                        }
                    }catch(Exception ignored){}
                    if(found.isEmpty()){
                        String osm="https://nominatim.openstreetmap.org/search?q="+URLEncoder.encode(query,"UTF-8")+"&format=jsonv2&limit=5&countrycodes=us&addressdetails=1";
                        JSONArray matches=new JSONArray(getText(osm));
                        for(int i=0;i<matches.length();i++){
                            JSONObject p=matches.optJSONObject(i);
                            JSONObject address=p.optJSONObject("address");
                            String city="";
                            if(address!=null){
                                city=address.optString("city","");
                                if(city.isEmpty())city=address.optString("town","");
                                if(city.isEmpty())city=address.optString("village","");
                                if(city.isEmpty())city=address.optString("suburb","");
                            }
                            String state=address==null?"":address.optString("state","");
                            String name=!city.isEmpty()&&!state.isEmpty()?city+", "+state:p.optString("display_name",query);
                            found.add(new SavedPlace(name,Double.parseDouble(p.getString("lat")),Double.parseDouble(p.getString("lon"))));
                        }
                    }
                }
            }catch(Exception ignored){}
            handler.post(()->{
                if(!"add-location".equals(currentPage)||!query.equals(lastLocationQuery))return;
                suggestions.removeAllViews();
                if(found.isEmpty()){suggestions.addView(text("No suggestions found. Try a city and state or a 5-digit ZIP.",12,false));return;}
                for(SavedPlace place:found){
                    LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(10),dp(10),dp(10),dp(10));row.setFocusable(true);row.setClickable(true);row.setBackground(ThemeUtils.focusBackground(this));
                    TextView name=text(place.name,13,true);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));row.addView(text("＋",22,true));
                    row.setOnClickListener(v->saveAndSelectLocation(place));row.setOnFocusChangeListener((v,focused)->row.setBackground(focused?ThemeUtils.focusOutlineBackground(this):ThemeUtils.focusBackground(this)));
                    suggestions.addView(row,rowMargins());
                }
            });
        }).start();
    }

    private void saveAndSelectLocation(SavedPlace place) {
        boolean exists=false;for(SavedPlace p:savedPlaces)if(p.name.equalsIgnoreCase(place.name)){exists=true;place=p;break;}
        if(!exists)savedPlaces.add(place);
        persistSavedPlaces();
        prefs().edit().putString("weather_selected_location",place.name).putString("weather_location",place.name)
            .putFloat("weather_lat",(float)place.lat).putFloat("weather_lon",(float)place.lon).apply();
        dailyPeriods=new JSONArray();hourlyPeriods=new JSONArray();dailyDays.clear();
        showForecastPage();
    }

    private interface WeatherChoiceListener { void onSelected(int index); }

    private void showWeatherChoicePage(String page, String title, String[] options,
            int selected, WeatherChoiceListener listener) {
        currentPage = page;
        removeHeaderRows();
        rebuildToolbar(title, false, false);
        forecastList.removeAllViews();
        for (int i = 0; i < options.length; i++) {
            final int index = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(11), dp(14), dp(11));
            row.setFocusable(true);
            row.setClickable(true);
            row.setBackground(weatherSettingBackground(false));
            TextView label = text(options[i], 15, index == selected);
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView check = text(index == selected ? "✓" : "", 20, true);
            check.setTextColor(ThemeUtils.accent(this));
            row.addView(check, new LinearLayout.LayoutParams(dp(28), -2));
            row.setOnFocusChangeListener((v, focused) -> {
                row.setBackground(weatherSettingBackground(focused));
                int focusedText = prefs().getBoolean("focus_outline_only", true)
                    ? ThemeUtils.text(this) : ThemeUtils.onAccent(this);
                label.setTextColor(focused ? focusedText : ThemeUtils.text(this));
                check.setTextColor(focused ? focusedText : ThemeUtils.accent(this));
            });
            row.setOnClickListener(v -> listener.onSelected(index));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(dp(4), dp(3), dp(4), dp(3));
            forecastList.addView(row, params);
        }
        if (forecastList.getChildCount() > 0) forecastList.getChildAt(Math.max(0, Math.min(selected, forecastList.getChildCount() - 1))).requestFocus();
    }

    private void showWeatherSettings() {
        currentPage = "settings";
        removeHeaderRows();
        rebuildToolbar("Weather settings", false, false);
        forecastList.removeAllViews();
        addSetting("Choose location", "Manage saved locations", () -> showLocationsPage());
        addSetting("Font size", fontScaleLabel(), () -> {
            String[] labels = {"Small", "Normal", "Large", "Extra large"};
            float[] values = {0.85f, 1f, 1.15f, 1.3f};
            int selected = 1;
            for (int i = 0; i < values.length; i++) if (Math.abs(values[i] - fontScale) < 0.04f) selected = i;
            showWeatherChoicePage("settings-font", "Weather font size", labels, selected, which -> {
                fontScale = values[which];
                prefs().edit().putFloat("weather_font_scale", fontScale).apply();
                showWeatherSettings();
            });
        });
        addSwitchSetting("New forecast notifications", "Notify only when the forecast issue/update time changes.",
            "weather_new_forecast_notifications", true);
        addSetting("Auto refresh", refreshLabel(), () -> {
            String[] labels = {"Off", "5 minutes", "10 minutes", "15 minutes", "30 minutes", "60 minutes"};
            int[] values = {0, 5, 10, 15, 30, 60};
            int selected = 3;
            int current = prefs().getInt("weather_refresh_minutes", 30);
            for (int i = 0; i < values.length; i++) if (values[i] == current) selected = i;
            showWeatherChoicePage("settings-refresh", "Auto refresh interval", labels, selected, which -> {
                prefs().edit().putInt("weather_refresh_minutes", values[which]).apply();
                scheduleAutoRefresh();
                showWeatherSettings();
            });
        });
        addSetting("Refresh now", "Update forecast immediately", () -> {
            currentPage = "forecast";
            removeHeaderRows();
            showForecastPageHeader();
            if ("current".equals(prefs().getString("weather_location_source", ""))) detectLocation();
            else {
                SavedPlace p = selectedPlace();
                if (p != null) fetchForecastAt(p.lat, p.lon);
                else if (prefs().contains("weather_lat") && prefs().contains("weather_lon"))
                    fetchForecastAt(prefs().getFloat("weather_lat", 0), prefs().getFloat("weather_lon", 0));
                else detectLocation();
            }
        });
        addSetting("Back to forecast", "Return to the forecast", () -> showForecastPage());
    }

    private String fontScaleLabel(){return fontScale<0.95f?"Small":fontScale>1.25f?"Extra large":fontScale>1.05f?"Large":"Normal";}
    private String refreshLabel(){int n=prefs().getInt("weather_refresh_minutes",30);return n==0?"Off":"Every "+n+" minutes";}
    private android.graphics.drawable.GradientDrawable weatherSettingBackground(boolean focused) {
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setColor(ThemeUtils.surface(this));
        shape.setCornerRadius(dp(12));
        shape.setStroke(dp(focused ? 2 : 1),
            focused ? ThemeUtils.accent(this) : ThemeUtils.divider(this));
        return shape;
    }

    private void addSwitchSetting(String title, String summary, String key, boolean defaultValue) {
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14),dp(12),dp(14),dp(12));
        row.setBackground(weatherSettingBackground(false));
        row.setFocusable(true); row.setClickable(true);
        LinearLayout labels=new LinearLayout(this); labels.setOrientation(LinearLayout.VERTICAL);
        TextView titleView=text(title,15,true), summaryView=text(summary,12,false);
        labels.addView(titleView); labels.addView(summaryView);
        row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        android.widget.Switch toggle=new android.widget.Switch(this);
        toggle.setFocusable(false);
        toggle.setClickable(false);
        toggle.setChecked(prefs().getBoolean(key,defaultValue));
        toggle.setThumbTintList(android.content.res.ColorStateList.valueOf(ThemeUtils.accent(this)));
        toggle.setTrackTintList(android.content.res.ColorStateList.valueOf(ThemeUtils.accent(this)));
        toggle.setOnCheckedChangeListener((button,checked)->prefs().edit().putBoolean(key,checked).apply());
        row.addView(toggle,new LinearLayout.LayoutParams(-2,-2));
        row.setOnClickListener(v->toggle.setChecked(!toggle.isChecked()));
        row.setOnFocusChangeListener((v,focused)->{
            row.setBackground(weatherSettingBackground(focused));
            int focusedText = prefs().getBoolean("focus_outline_only", true)
                ? ThemeUtils.text(this) : ThemeUtils.onAccent(this);
            titleView.setTextColor(focused ? focusedText : ThemeUtils.text(this));
            summaryView.setTextColor(focused ? focusedText : ThemeUtils.secondaryText(this));
        });
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.setMargins(dp(4),dp(3),dp(4),dp(3));
        forecastList.addView(row,params);
    }

    private void addSetting(String title,String summary,Runnable action){
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14),dp(12),dp(14),dp(12));
        row.setBackground(weatherSettingBackground(false));
        row.setFocusable(true); row.setClickable(true);
        LinearLayout labels=new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = text(title,15,true);
        TextView summaryView = text(summary,12,false);
        labels.addView(titleView);
        labels.addView(summaryView);
        row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        TextView chevron=text("›",24,true);
        chevron.setTextColor(ThemeUtils.secondaryText(this));
        chevron.setGravity(Gravity.CENTER);
        row.addView(chevron,new LinearLayout.LayoutParams(dp(24),-2));
        row.setOnClickListener(v->action.run());
        row.setOnFocusChangeListener((v,focused)->{
            row.setBackground(weatherSettingBackground(focused));
            int focusedText = prefs().getBoolean("focus_outline_only", true)
                ? ThemeUtils.text(this) : ThemeUtils.onAccent(this);
            titleView.setTextColor(focused ? focusedText : ThemeUtils.text(this));
            summaryView.setTextColor(focused ? focusedText : ThemeUtils.secondaryText(this));
            chevron.setTextColor(focused ? focusedText : ThemeUtils.secondaryText(this));
        });
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.setMargins(dp(12),dp(4),dp(12),dp(4));
        forecastList.addView(row,params);
    }

    private void loadSavedPlaces(){
        savedPlaces.clear();
        String raw=prefs().getString("weather_saved_locations","");
        if(!raw.isEmpty())try{JSONArray a=new JSONArray(raw);for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null)savedPlaces.add(new SavedPlace(o.optString("name","Saved location"),o.optDouble("lat"),o.optDouble("lon")));}}catch(Exception ignored){}
        if(savedPlaces.isEmpty()&&!"current".equals(prefs().getString("weather_location_source",""))&&prefs().contains("weather_lat")&&prefs().contains("weather_lon")){
            String name=prefs().getString("weather_location","");if(!name.isEmpty()&&!"Use current location".equals(name)&&!"Current location".equals(name))savedPlaces.add(new SavedPlace(name,prefs().getFloat("weather_lat",0),prefs().getFloat("weather_lon",0)));
            persistSavedPlaces();
        }
    }
    private void persistSavedPlaces(){JSONArray a=new JSONArray();for(SavedPlace p:savedPlaces)try{a.put(p.json());}catch(Exception ignored){}prefs().edit().putString("weather_saved_locations",a.toString()).apply();}
    private String selectedPlaceName(){return prefs().getString("weather_selected_location",prefs().getString("weather_location",""));}
    private SavedPlace selectedPlace(){
        if ("current".equals(prefs().getString("weather_location_source", ""))) return null;
        String name=selectedPlaceName();for(SavedPlace p:savedPlaces)if(p.name.equalsIgnoreCase(name))return p;
        if(prefs().contains("weather_lat")&&prefs().contains("weather_lon")&&!name.isEmpty()&&!"Use current location".equals(name)&&!"Current location".equals(name))return new SavedPlace(name,prefs().getFloat("weather_lat",0),prefs().getFloat("weather_lon",0));
        return null;
    }

    private void detectLocation(){
        final boolean hasFine = android.os.Build.VERSION.SDK_INT < 23
            || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        final boolean hasCoarse = android.os.Build.VERSION.SDK_INT < 23 || hasFine
            || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!hasFine && !hasCoarse) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},LOCATION_PERMISSION_REQUEST);
            return;
        }

        // findCurrentLocation() checks both live providers and a recent
        // cached fix before offering the city/ZIP fallback.
        findCurrentLocation();
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==LOCATION_PERMISSION_REQUEST){boolean granted=false;for(int r:grantResults)if(r==PackageManager.PERMISSION_GRANTED)granted=true;if(granted)findCurrentLocation();else locationUnavailable("Location permission was not granted.");}
    }

    @SuppressLint("MissingPermission")
    private void findCurrentLocation(){
        final boolean hasFine = android.os.Build.VERSION.SDK_INT < 23
            || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        final boolean hasCoarse = android.os.Build.VERSION.SDK_INT < 23
            || hasFine
            || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!hasFine && !hasCoarse) {
            locationUnavailable("Location permission is required. Allow precise or approximate device location, or choose a city or ZIP code.");
            return;
        }

        final LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (manager == null) {
            locationUnavailable("Android location services are unavailable. Choose a city or ZIP code.");
            return;
        }

        setLoading(true, "Finding your current location…");
        if (locationTimeout != null) handler.removeCallbacks(locationTimeout);
        if (pendingLocationListener != null) {
            try { manager.removeUpdates(pendingLocationListener); } catch (Exception ignored) { }
        }
        pendingLocationListener = null;

        final Location[] best = {null};
        // Older flip phones may not deliver a fresh callback promptly. Keep a
        // recent, reasonably accurate cached fix as a bounded fallback.
        long now = System.currentTimeMillis();
        String[] providers = {LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER};
        for (String provider : providers) {
            try {
                if ((LocationManager.GPS_PROVIDER.equals(provider) && !hasFine)
                    || (LocationManager.NETWORK_PROVIDER.equals(provider) && !hasCoarse)) continue;
                Location cached = manager.getLastKnownLocation(provider);
                if (cached == null || !cached.hasAccuracy() || cached.getAccuracy() > 5000f) continue;
                long age = now - cached.getTime();
                if (age >= 0 && age <= 15L * 60L * 1000L) best[0] = betterLocation(best[0], cached);
            } catch (Exception ignored) { }
        }
        final long requestStarted = System.currentTimeMillis();
        final boolean[] finished = {false};

        pendingLocationListener = new LocationListener() {
            @Override public void onLocationChanged(Location location) {
                if (finished[0] || location == null) return;
                String provider = location.getProvider();
                if (!LocationManager.GPS_PROVIDER.equals(provider)
                    && !LocationManager.NETWORK_PROVIDER.equals(provider)) return;
                // Provider registration may replay a cached fix. Reject fixes
                // older than this refresh request so a stale area is never reused.
                long fixTime = location.getTime();
                long age = System.currentTimeMillis() - fixTime;
                if (fixTime < requestStarted - 2000L || age < 0 || age > 30000L) return;
                if (!location.hasAccuracy() || location.getAccuracy() > 5000f) return;
                if (Double.isNaN(location.getLatitude()) || Double.isInfinite(location.getLatitude())
                    || Double.isNaN(location.getLongitude()) || Double.isInfinite(location.getLongitude())) return;

                best[0] = betterLocation(best[0], location);
                if (location.getAccuracy() <= 500f) {
                    finished[0] = true;
                    stopLocationUpdates(manager);
                    onCurrentLocation(location);
                }
            }
            @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
            @Override public void onProviderEnabled(String provider) { }
            @Override public void onProviderDisabled(String provider) { }
        };

        boolean requested = false;
        if (hasFine) {
            try {
                if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000L, 25f,
                        pendingLocationListener, Looper.getMainLooper());
                    requested = true;
                }
            } catch (Exception ignored) { }
        }
        if (hasCoarse) {
            try {
                if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10000L, 25f,
                        pendingLocationListener, Looper.getMainLooper());
                    requested = true;
                }
            } catch (Exception ignored) { }
        }

        if (!requested) {
            stopLocationUpdates(manager);
            if (best[0] != null) onCurrentLocation(best[0]);
            else locationUnavailable("No usable device location provider is enabled. Turn on Location or choose a city or ZIP code.");
            return;
        }

        locationTimeout = () -> {
            if (finished[0]) return;
            finished[0] = true;
            stopLocationUpdates(manager);
            Location result = best[0];
            long fixAge = result == null ? Long.MAX_VALUE : System.currentTimeMillis() - result.getTime();
            if (result != null && fixAge >= 0 && fixAge <= 15L * 60L * 1000L
                && result.hasAccuracy() && result.getAccuracy() <= 5000f) {
                onCurrentLocation(result);
            } else {
                locationUnavailable("The phone did not provide a fresh, accurate location. Choose a city or ZIP code, or try again with device Location enabled.");
            }
        };
        handler.postDelayed(locationTimeout, 20000L);
    }

    private Location betterLocation(Location oldLocation,Location newLocation){
        if(oldLocation==null)return newLocation;
        if(newLocation==null)return oldLocation;
        if(newLocation.hasAccuracy()&&!oldLocation.hasAccuracy())return newLocation;
        if(!newLocation.hasAccuracy()&&oldLocation.hasAccuracy())return oldLocation;
        if(newLocation.hasAccuracy()&&oldLocation.hasAccuracy()){
            if(newLocation.getAccuracy()<oldLocation.getAccuracy())return newLocation;
            if(newLocation.getAccuracy()>oldLocation.getAccuracy())return oldLocation;
        }
        return newLocation.getTime()>oldLocation.getTime()?newLocation:oldLocation;
    }

    private void stopLocationUpdates(LocationManager manager){
        if(locationTimeout!=null){handler.removeCallbacks(locationTimeout);locationTimeout=null;}
        if(pendingLocationListener!=null){
            try{manager.removeUpdates(pendingLocationListener);}catch(Exception ignored){}
            pendingLocationListener=null;
        }
    }

    private void onCurrentLocation(Location location) {
        final double latitude = location.getLatitude();
        final double longitude = location.getLongitude();
        // Request the forecast immediately. Reverse-geocoding is optional and
        // must not delay the weather screen on the phone's slow network stack.
        useCurrentCoordinates(latitude, longitude, "Current location");
        new Thread(() -> {
            String label = "";
            try {
                Geocoder geocoder = new Geocoder(this, Locale.US);
                List<Address> matches = geocoder.getFromLocation(latitude, longitude, 1);
                if (matches != null && !matches.isEmpty()) {
                    Address address = matches.get(0);
                    String city = address.getLocality();
                    if (city == null || city.trim().isEmpty()) city = address.getSubAdminArea();
                    String state = address.getAdminArea();
                    if (city != null && !city.trim().isEmpty())
                        label = city + (state == null || state.trim().isEmpty() ? "" : ", " + state);
                }
            } catch (Exception ignored) { }
            final String resolvedLabel = label;
            if (!resolvedLabel.isEmpty()) handler.post(() -> {
                SharedPreferences saved = prefs();
                boolean sameCoordinates = "current".equals(saved.getString("weather_location_source", ""))
                    && Math.abs(saved.getFloat("weather_lat", 999f) - (float) latitude) < 0.00001f
                    && Math.abs(saved.getFloat("weather_lon", 999f) - (float) longitude) < 0.00001f;
                if (sameCoordinates) {
                    saved.edit().putString("weather_location", resolvedLabel).apply();
                    if (locationTitle != null) locationTitle.setText(resolvedLabel + "  ▾");
                }
            });
        }, "YWReverseGeocode").start();
    }

    private void fallbackIpLocation() {
        locationUnavailable("YW Weather could not get a fresh device location. Choose a city or ZIP code instead of using an inaccurate network/IP estimate.");
    }

    private void locationUnavailable(String message) {
        handler.post(() -> {
            SharedPreferences saved = prefs();
            // A previously obtained current-location fix is a useful fallback
            // on older flip phones whose location providers often fail to wake.
            if ("current".equals(saved.getString("weather_location_source", ""))
                && saved.contains("weather_lat") && saved.contains("weather_lon")) {
                float lat = saved.getFloat("weather_lat", 999f);
                float lon = saved.getFloat("weather_lon", 999f);
                if (lat >= -90f && lat <= 90f && lon >= -180f && lon <= 180f) {
                    useCurrentCoordinates(lat, lon, "Last known location");
                    return;
                }
            }
            setLoading(false, null);
            showWeatherError(message);
        });
    }

    private void useCurrentCoordinates(double lat,double lon,String label){
        prefs().edit().putString("weather_location",label).putString("weather_selected_location","").putString("weather_location_source","current").putFloat("weather_lat",(float)lat).putFloat("weather_lon",(float)lon).apply();
        if(locationTitle!=null)locationTitle.setText(label+"  ▾");
        fetchForecastAt(lat,lon);
    }

    private void scheduleAutoRefresh(){
        if(autoRefreshRunnable!=null)handler.removeCallbacks(autoRefreshRunnable);
        int minutes=prefs().getInt("weather_refresh_minutes",30);
        if(minutes<=0||isFinishing())return;
        autoRefreshRunnable=()->{if("forecast".equals(currentPage)){
            if ("current".equals(prefs().getString("weather_location_source", ""))) detectLocation();
            else {SavedPlace p=selectedPlace();if(p!=null)fetchForecastAt(p.lat,p.lon);else if(prefs().contains("weather_lat"))fetchForecastAt(prefs().getFloat("weather_lat",0),prefs().getFloat("weather_lon",0));}
        }scheduleAutoRefresh();};
        handler.postDelayed(autoRefreshRunnable,minutes*60L*1000L);
    }

    private void showWeatherError(String message){
        forecastList.removeAllViews();TextView t=text(message,textSize(14),false);t.setPadding(dp(8),dp(18),dp(8),dp(18));forecastList.addView(t);
        Button locations=new Button(this);locations.setText("Choose location");ThemeUtils.tint(locations,this);locations.setOnClickListener(v->showLocationsPage());forecastList.addView(locations,rowMargins());
    }

    private TextView text(String value,float size,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextColor(ThemeUtils.text(this));t.setTextSize(textSize(size));if(bold)t.setTypeface(null,android.graphics.Typeface.BOLD);return t;
    }
    private void addSectionTitle(String value){TextView t=text(value,13,true);t.setTextColor(ThemeUtils.accent(this));t.setPadding(dp(4),dp(13),dp(4),dp(5));forecastList.addView(t);}
    private LinearLayout.LayoutParams rowMargins(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(7);return p;}
    private GradientDrawable cardBackground(){GradientDrawable d=new GradientDrawable();d.setColor(ThemeUtils.isDark(this)?0xff18232f:0xfff5f8fc);d.setCornerRadius(dp(10));d.setStroke(dp(1),ThemeUtils.divider(this));return d;}

    private View weatherIcon(String forecast, int sizeDp) {
        return new WeatherIconView(this, forecast, sizeDp);
    }

    private final class WeatherIconView extends View {
        private final String condition;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path cloudPath = new Path();
        private final Path detailPath = new Path();
        private final Shader sunGradient = new RadialGradient(24, 25, 18,
            new int[]{0xfffff6a8, 0xffffc928, 0xffff8b16}, null, Shader.TileMode.CLAMP);
        private final Shader moonGradient = new RadialGradient(27, 20, 20,
            new int[]{0xfffff4d0, 0xffc9c8ff, 0xff858fe8}, null, Shader.TileMode.CLAMP);
        private final Shader cloudGradient = new LinearGradient(17, 17, 59, 58,
            new int[]{0xffffffff, 0xff67d8ff, 0xff287de8}, null, Shader.TileMode.CLAMP);
        private final Shader dropGradient = new LinearGradient(22, 61, 27, 73,
            0xff69edff, 0xff1670e8, Shader.TileMode.CLAMP);
        private final Shader boltGradient = new LinearGradient(37, 48, 51, 72,
            0xfffff27a, 0xffff8c28, Shader.TileMode.CLAMP);
        private final int size;

        WeatherIconView(Context context, String forecast, int sizeDp) {
            super(context);
            condition = forecast == null ? "" : forecast.toLowerCase(Locale.US);
            size = sizeDp;
            setContentDescription(forecast);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int target = dp(size);
            setMeasuredDimension(resolveSize(target, widthSpec), resolveSize(target, heightSpec));
        }

        private boolean windyCondition() {
            return condition.contains("wind") || condition.contains("breezy");
        }

        private void drawSun(Canvas canvas, float cx, float cy, float radius) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3.2f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setShader(null);
            paint.setColor(0xffffb51b);
            for (int i = 0; i < 8; i++) {
                double angle = Math.PI * i / 4d;
                float dx = (float) Math.cos(angle);
                float dy = (float) Math.sin(angle);
                canvas.drawLine(cx + dx * (radius + 4), cy + dy * (radius + 4),
                    cx + dx * (radius + 10), cy + dy * (radius + 10), paint);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(sunGradient);
            canvas.drawCircle(cx, cy, radius, paint);
            paint.setShader(null);
            paint.setColor(0x66ffffff);
            canvas.drawCircle(cx - radius * .28f, cy - radius * .32f, radius * .25f, paint);
        }

        private void buildCloud() {
            cloudPath.reset();
            cloudPath.moveTo(18, 57);
            cloudPath.cubicTo(10, 57, 7, 52, 7, 45);
            cloudPath.cubicTo(7, 37, 13, 31, 21, 31);
            cloudPath.cubicTo(23, 21, 31, 16, 40, 17);
            cloudPath.cubicTo(50, 18, 56, 25, 57, 35);
            cloudPath.cubicTo(65, 35, 70, 40, 70, 47);
            cloudPath.cubicTo(70, 53, 65, 57, 58, 57);
            cloudPath.close();
        }

        private void drawCloud(Canvas canvas) {
            // Puffy, layered cloud matching the launcher weather tile rather
            // than the thin outlined cartoon cloud used by the old renderer.
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(null);
            paint.setColor(0x44304f83);
            canvas.drawRoundRect(14, 48, 72, 66, 10, 10, paint);
            canvas.drawCircle(28, 48, 13, paint);
            canvas.drawCircle(45, 41, 17, paint);
            canvas.drawCircle(61, 49, 13, paint);

            paint.setShader(cloudGradient);
            canvas.drawCircle(27, 45, 13, paint);
            canvas.drawCircle(44, 39, 17, paint);
            canvas.drawCircle(60, 47, 13, paint);
            canvas.drawRoundRect(14, 46, 73, 64, 9, 9, paint);
            paint.setShader(null);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.2f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(0x99ffffff);
            canvas.drawArc(31, 27, 53, 51, 205, 105, false, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x22ffffff);
            canvas.drawRoundRect(19, 49, 67, 53, 2, 2, paint);
        }

        private void drawDrop(Canvas canvas, float x, float y) {
            detailPath.reset();
            detailPath.moveTo(x, y - 5);
            detailPath.cubicTo(x + 3, y - 1, x + 5, y + 2, x + 3, y + 5);
            detailPath.cubicTo(x + 1, y + 8, x - 3, y + 7, x - 4, y + 4);
            detailPath.cubicTo(x - 5, y + 1, x - 2, y - 3, x, y - 5);
            detailPath.close();
            paint.setShader(dropGradient);
            canvas.drawPath(detailPath, paint);
            paint.setShader(null);
        }

        private void drawSnowflake(Canvas canvas, float x, float y) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.6f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(0xff7568f5);
            for (int i = 0; i < 3; i++) {
                canvas.save();
                canvas.rotate(i * 60, x, y);
                canvas.drawLine(x, y - 5, x, y + 5, paint);
                canvas.drawLine(x, y - 5, x - 2, y - 2, paint);
                canvas.drawLine(x, y - 5, x + 2, y - 2, paint);
                canvas.restore();
            }
            paint.setStyle(Paint.Style.FILL);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float scale = Math.min(getWidth(), getHeight()) / 80f;
            canvas.save();
            canvas.translate((getWidth() - 80f * scale) / 2f, (getHeight() - 80f * scale) / 2f);
            canvas.scale(scale, scale);

            boolean thunder = condition.contains("thunder");
            boolean rain = condition.contains("rain") || condition.contains("shower") || condition.contains("drizzle") || thunder;
            boolean snow = condition.contains("snow") || condition.contains("sleet") || condition.contains("ice") || condition.contains("blizzard");
            boolean fog = condition.contains("fog") || condition.contains("haze") || condition.contains("smoke");
            boolean partly = condition.contains("partly") || condition.contains("mostly");
            boolean sunny = condition.contains("sun") || condition.contains("clear") || partly;
            boolean night = condition.contains("night") || condition.contains("tonight") || condition.contains("overnight");
            boolean cloudy = condition.contains("cloud") || condition.contains("overcast") || partly || rain || snow || fog || thunder;

            // Breezy-inspired flat weather artwork: transparent canvas, clear
            // condition silhouettes, saturated accents, and distinct day/night forms.
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(null);

            if (night) {
                paint.setShader(moonGradient);
                canvas.drawCircle(57, 27, 14, paint);
                paint.setShader(null);
                paint.setColor(0xff19386f);
                canvas.drawCircle(64, 21, 13, paint);
                paint.setColor(0xfff3f4ff);
                canvas.drawCircle(28, 18, 1.5f, paint);
                canvas.drawCircle(39, 29, 1.1f, paint);
                canvas.drawCircle(20, 33, 0.9f, paint);
            } else if (sunny) {
                drawSun(canvas, 55, 27, 13);
            }

            if (cloudy || (!sunny && !windyCondition())) drawCloud(canvas);

            if (windyCondition() && !rain && !snow && !thunder) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(3f);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setColor(0xff54b9ee);
                canvas.drawLine(12, 57, 59, 57, paint);
                canvas.drawLine(22, 67, 69, 67, paint);
                canvas.drawArc(49, 48, 70, 65, 190, 150, false, paint);
                paint.setStyle(Paint.Style.FILL);
            }

            if (thunder) {
                detailPath.reset();
                detailPath.moveTo(45, 47);
                detailPath.lineTo(35, 61);
                detailPath.lineTo(42, 61);
                detailPath.lineTo(37, 73);
                detailPath.lineTo(54, 55);
                detailPath.lineTo(46, 55);
                detailPath.close();
                paint.setShader(boltGradient);
                canvas.drawPath(detailPath, paint);
                paint.setShader(null);
            } else if (rain) {
                drawDrop(canvas, 24, 66);
                drawDrop(canvas, 40, 70);
                drawDrop(canvas, 56, 65);
            } else if (snow) {
                drawSnowflake(canvas, 24, 67);
                drawSnowflake(canvas, 42, 70);
                drawSnowflake(canvas, 59, 66);
            } else if (fog) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.5f);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setColor(0xffbdefff);
                canvas.drawLine(12, 65, 67, 65, paint);
                canvas.drawLine(18, 72, 61, 72, paint);
                paint.setStyle(Paint.Style.FILL);
            }
            canvas.restore();
        }
    }

    private String weatherEmoji(String f, boolean isDaytime){
        String v=f==null?"":f.toLowerCase(Locale.US);
        if(v.contains("thunder"))return "⛈️";if(v.contains("snow")||v.contains("sleet")||v.contains("ice")||v.contains("blizzard"))return "🌨️";
        if(v.contains("rain")||v.contains("shower")||v.contains("drizzle"))return "🌧️";if(v.contains("fog")||v.contains("haze")||v.contains("smoke"))return "🌫️";
        if(!isDaytime) { if(v.contains("cloud")||v.contains("overcast")||v.contains("partly")||v.contains("mostly"))return "☁️"; return "🌙"; }
        if(v.contains("partly")||v.contains("mostly sunny"))return "🌤️";if(v.contains("cloud")||v.contains("overcast"))return "☁️";
        if(v.contains("sun")||v.contains("clear"))return "☀️";if(v.contains("wind"))return "💨";return "🌡️";
    }

    private JSONObject getJson(String url)throws Exception{return new JSONObject(getText(url));}
    private String getText(String url)throws Exception{
        URLConnection opened=new URL(url).openConnection();if(opened instanceof javax.net.ssl.HttpsURLConnection)LegacyTlsCompat.configure(this,(javax.net.ssl.HttpsURLConnection)opened);
        HttpURLConnection conn=(HttpURLConnection)opened;conn.setConnectTimeout(12000);conn.setReadTimeout(12000);
        conn.setRequestProperty("User-Agent","YWLauncher/1.2 (Android weather client)");conn.setRequestProperty("Accept","application/geo+json, application/json");
        try{int code=conn.getResponseCode();InputStream in=code>=400?conn.getErrorStream():conn.getInputStream();if(in==null)throw new IOException("HTTP "+code);
            ByteArrayOutputStream out=new ByteArrayOutputStream();try{byte[] b=new byte[4096];int n;while((n=in.read(b))>0)out.write(b,0,n);}finally{in.close();}
            String body=new String(out.toByteArray(),"UTF-8");if(code>=400)throw new IOException("Server returned HTTP "+code);return body;
        }finally{conn.disconnect();}
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if ("discussion".equals(currentPage) && event.getAction() == KeyEvent.ACTION_DOWN && forecastScroller != null) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN) { forecastScroller.smoothScrollBy(0, dp(42)); return true; }
            if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP) { forecastScroller.smoothScrollBy(0, -dp(42)); return true; }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override public boolean onKeyDown(int keyCode,KeyEvent event){
        if ("discussion".equals(currentPage) && forecastScroller != null) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) { forecastScroller.smoothScrollBy(0, dp(42)); return true; }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) { forecastScroller.smoothScrollBy(0, -dp(42)); return true; }
        }
        if("day-detail".equals(currentPage)){
            if(keyCode==KeyEvent.KEYCODE_DPAD_LEFT){showDayDetail(Math.max(0,detailIndex-1));return true;}
            if(keyCode==KeyEvent.KEYCODE_DPAD_RIGHT){showDayDetail(Math.min(dailyDays.size()-1,detailIndex+1));return true;}
            if(keyCode==KeyEvent.KEYCODE_BACK){showForecastPage();return true;}
        }
        if(keyCode==KeyEvent.KEYCODE_BACK){
            if("add-location".equals(currentPage)){showLocationsPage();return true;}
            if ("settings-font".equals(currentPage) || "settings-refresh".equals(currentPage)) { showWeatherSettings(); return true; }
            if("locations".equals(currentPage)||"settings".equals(currentPage)||"discussion".equals(currentPage)){showForecastPage();return true;}
            if("forecast".equals(currentPage)){finish();return true;}
        }
        return super.onKeyDown(keyCode,event);
    }

    @Override protected void onDestroy(){
        if(locationTimeout!=null)handler.removeCallbacks(locationTimeout);
        if(autoRefreshRunnable!=null)handler.removeCallbacks(autoRefreshRunnable);
        if(pendingLocationListener!=null)try{((LocationManager)getSystemService(LOCATION_SERVICE)).removeUpdates(pendingLocationListener);}catch(Exception ignored){}
        super.onDestroy();
    }
}