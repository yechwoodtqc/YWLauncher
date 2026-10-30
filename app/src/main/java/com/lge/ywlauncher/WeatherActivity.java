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
    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private android.content.SharedPreferences prefs() { return getSharedPreferences(PREFS, 0); }
    private float textSize(float base) { return base * fontScale; }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ThemeUtils.apply(this);
        fontScale = prefs().getFloat("weather_font_scale", 1.0f);
        loadSavedPlaces();
        buildScreen();
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
        scheduleAutoRefresh();
    }
    @Override protected void onPause() {
        if(autoRefreshRunnable!=null)handler.removeCallbacks(autoRefreshRunnable);
        super.onPause();
    }

    private void buildScreen() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(6), dp(10), dp(6));
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
            back.setOnClickListener(v -> { if ("add-location".equals(currentPage)) showLocationsPage(); else showForecastPage(); });
            toolbar.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44)));
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
        locationTitle.setTextSize(textSize(16));
        locationTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        locationTitle.setPadding(dp(4), dp(3), dp(4), dp(3));
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
        updated.setPadding(dp(4), 0, dp(4), dp(2));
        root.addView(updated, root.indexOfChild(toolbar) + 2, new LinearLayout.LayoutParams(-1, -2));
        forecasted = new TextView(this);
        forecasted.setTextColor(ThemeUtils.secondaryText(this));
        forecasted.setTextSize(textSize(11));
        forecasted.setPadding(dp(4), 0, dp(4), dp(5));
        root.addView(forecasted, root.indexOfChild(toolbar) + 3, new LinearLayout.LayoutParams(-1, -2));
        setSelectedPlaceTitle();
    }

    private void removeHeaderRows() {
        if (locationTitle != null) root.removeView(locationTitle);
        if (updated != null) root.removeView(updated);
        if (forecasted != null) root.removeView(forecasted);
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
        if ("discussion".equals(currentPage)) { SavedPlace p=selectedPlace(); if(p!=null)fetchForecastAt(p.lat,p.lon); return; }
        SavedPlace place = selectedPlace();
        if (place != null) fetchForecastAt(place.lat, place.lon);
        else if (prefs().contains("weather_lat") && prefs().contains("weather_lon")) fetchForecastAt(prefs().getFloat("weather_lat",0),prefs().getFloat("weather_lon",0));
        else detectLocation();
    }

    private void fetchForecastAt(double latitude, double longitude) {
        setLoading(true, "Checking the sky…");
        new Thread(() -> {
            try {
                JSONObject point = getJson("https://api.weather.gov/points/" + latitude + "," + longitude);
                JSONObject properties = point.getJSONObject("properties");
                JSONObject daily = getJson(properties.getString("forecast"));
                String hourlyUrl = properties.optString("forecastHourly", "");
                JSONArray days = daily.getJSONObject("properties").getJSONArray("periods");
                JSONArray hours = new JSONArray();
                if (!hourlyUrl.isEmpty()) hours = getJson(hourlyUrl).getJSONObject("properties").getJSONArray("periods");
                String generated = daily.getJSONObject("properties").optString("updateTime", "");
                if (generated.trim().isEmpty()) generated = daily.getJSONObject("properties").optString("generatedAt", "");
                String office = properties.optString("cwa", "");
                JSONArray alerts = new JSONArray();
                try {
                    JSONArray features = getJson("https://api.weather.gov/alerts/active?point=" + latitude + "," + longitude).optJSONArray("features");
                    if (features != null) for (int i = 0; i < features.length(); i++) {
                        JSONObject feature = features.optJSONObject(i);
                        JSONObject alert = feature == null ? null : feature.optJSONObject("properties");
                        if (alert != null) alerts.put(alert);
                    }
                } catch (Exception ignored) { }
                JSONArray finalAlerts = alerts;
                JSONArray finalHours = hours;
                String finalGenerated = generated;
                handler.post(() -> {
                    dailyPeriods = days; hourlyPeriods = finalHours; activeAlerts = finalAlerts; buildDailyDays(days);
                    setLoading(false, null);
                    renderForecast();
                    String now = new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(new Date());
                    if (updated != null) updated.setText("Last updated: " + now);
                    String forecastTime = finalGenerated;
                    if (!forecastTime.isEmpty()) {
                        try {
                            String normalizedTime = forecastTime.replaceFirst("([+-]\\d{2}):(\\d{2})$", "$1$2").replaceFirst("Z$", "+0000");
                            normalizedTime = normalizedTime.replaceFirst("\\.\\d+(?=[+-]\\d{4}$)", "");
                            java.text.SimpleDateFormat input = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US);
                            Date date = input.parse(normalizedTime);
                            forecastTime = date == null ? forecastTime : new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(date);
                        } catch (Exception ignored) {}
                    }
                    if (forecasted != null) forecasted.setText("Forecast issued/updated: " + (forecastTime.isEmpty() ? "time not provided" : forecastTime));
                    prefs().edit().putString("weather_widget_summary", days.optJSONObject(0)==null?"Forecast ready":days.optJSONObject(0).optString("shortForecast","Forecast ready"))
                        .putString("weather_widget_temperature", days.optJSONObject(0)==null?"—":days.optJSONObject(0).optString("temperature","—")+"°"+(days.optJSONObject(0)==null?"F":days.optJSONObject(0).optString("temperatureUnit","F")))
                        .putString("weather_widget_icon", days.optJSONObject(0)==null?"🌤️":weatherEmoji(days.optJSONObject(0).optString("shortForecast","")))
                        .apply();
                    WeatherWidgetProvider.updateAll(this);
                    fetchDiscussion(office);
                    scheduleAutoRefresh();
                });
            } catch (Exception e) {
                handler.post(() -> { setLoading(false, null); showWeatherError("Couldn't load the forecast. Check your connection or choose another saved location.\n\n" + e.getMessage()); });
            }
        }).start();
    }

    private void renderForecast() {
        if (!"forecast".equals(currentPage)) return;
        forecastList.removeAllViews();
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
                warning.setOnClickListener(v -> new AlertDialog.Builder(this)
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
            card.addView(text(time,12,true));card.addView(weatherIcon(period.optString("shortForecast",""),40),new LinearLayout.LayoutParams(dp(52),dp(46)));
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
        nowRow.addView(weatherIcon(current.optString("shortForecast",""),48),new LinearLayout.LayoutParams(dp(58),dp(58)));
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
        if(day==null)return;
        JSONObject primary=day.day!=null?day.day:day.night;
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.HORIZONTAL);card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(8),dp(7),dp(8),dp(7));card.setBackground(ThemeUtils.focusBackground(this));card.setFocusable(true);card.setClickable(true);
        View icon=weatherIcon(primary.optString("shortForecast",""),40);
        card.addView(icon,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(dp(8),0,dp(3),0);
        info.addView(text(day.label,14,true));
        if(day.day!=null) info.addView(text("High "+day.day.optString("temperature","—")+"°"+day.day.optString("temperatureUnit","F")+" · "+day.day.optString("shortForecast",""),12,false));
        if(day.night!=null) info.addView(text("Low "+day.night.optString("temperature","—")+"°"+day.night.optString("temperatureUnit","F")+" · "+day.night.optString("shortForecast",""),12,false));
        String wind=primary.optString("windSpeed","—")+" "+primary.optString("windDirection","");
        JSONObject pp=primary.optJSONObject("probabilityOfPrecipitation");
        String precip=pp==null||pp.isNull("value")?"—":pp.optString("value")+"%";
        info.addView(text("Wind "+wind+" · Precip. "+precip,11,false));
        card.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        TextView arrow=text("›",24,false);card.addView(arrow,new LinearLayout.LayoutParams(dp(22),-2));
        card.setOnClickListener(v->{detailIndex=index;showDayDetail(index);});
        card.setOnFocusChangeListener((v,focused)->{
            card.setBackground(focused?ThemeUtils.focusOutlineBackground(this):ThemeUtils.focusBackground(this));
            
        });
        forecastList.addView(card,rowMargins());
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
        card.addView(text(weatherEmoji(period.optString("shortForecast","")),25,true),new LinearLayout.LayoutParams(dp(42),dp(44)));
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
        hero.addView(weatherIcon(primary.optString("shortForecast",""),76),new LinearLayout.LayoutParams(dp(90),dp(86)));
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
        new AlertDialog.Builder(this).setTitle(weatherEmoji(p.optString("shortForecast",""))+" "+p.optString("shortForecast","Hourly details"))
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
                handler.post(()->{discussionProducts=result;if("forecast".equals(currentPage)&&!hourlyMode)renderForecast();});
            }catch(Exception ignored){}
        }).start();
    }

    private void showDiscussionPage() {
        currentPage="discussion";rebuildToolbar("Forecast discussion",false,false);forecastList.removeAllViews();
        if(forecastScroller!=null)forecastScroller.post(()->forecastScroller.scrollTo(0,0));
        String body=discussionProducts.length()>0?discussionProducts.optJSONObject(0).optString("text","No discussion available."):"Forecast discussion is not available yet. Refresh the forecast and try again.";
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
                new AlertDialog.Builder(this).setTitle("Remove saved location?")
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

    private void showWeatherSettings() {
        currentPage="settings";removeHeaderRows();rebuildToolbar("Weather settings",false,false);forecastList.removeAllViews();
        addSetting("Choose location","Manage saved locations",()->showLocationsPage());
        addSetting("Font size",fontScaleLabel(),()->{
            String[] labels={"Small","Normal","Large","Extra large"};float[] values={0.85f,1f,1.15f,1.3f};
            int selected=1;for(int i=0;i<values.length;i++)if(Math.abs(values[i]-fontScale)<0.04f)selected=i;
            new AlertDialog.Builder(this).setTitle("Weather font size").setSingleChoiceItems(labels,selected,(d,w)->{fontScale=values[w];prefs().edit().putFloat("weather_font_scale",fontScale).apply();d.dismiss();showWeatherSettings();}).setNegativeButton("Cancel",null).show();
        });
        addSetting("Auto refresh",refreshLabel(),()->{
            String[] labels={"Off","5 minutes","10 minutes","15 minutes","30 minutes","60 minutes"};int[] values={0,5,10,15,30,60};int selected=3;
            int current=prefs().getInt("weather_refresh_minutes",15);for(int i=0;i<values.length;i++)if(values[i]==current)selected=i;
            new AlertDialog.Builder(this).setTitle("Auto refresh interval").setSingleChoiceItems(labels,selected,(d,w)->{prefs().edit().putInt("weather_refresh_minutes",values[w]).apply();d.dismiss();scheduleAutoRefresh();showWeatherSettings();}).setNegativeButton("Cancel",null).show();
        });
        addSetting("Refresh now","Update forecast immediately",()->{
            currentPage="forecast";removeHeaderRows();showForecastPageHeader();
            if ("current".equals(prefs().getString("weather_location_source", ""))) detectLocation();
            else {
                SavedPlace p=selectedPlace();
                if(p!=null)fetchForecastAt(p.lat,p.lon);
                else if(prefs().contains("weather_lat")&&prefs().contains("weather_lon"))fetchForecastAt(prefs().getFloat("weather_lat",0),prefs().getFloat("weather_lon",0));
                else detectLocation();
            }
        });
        addSetting("Back to forecast","Return to the forecast",()->showForecastPage());
    }

    private String fontScaleLabel(){return fontScale<0.95f?"Small":fontScale>1.25f?"Extra large":fontScale>1.05f?"Large":"Normal";}
    private String refreshLabel(){int n=prefs().getInt("weather_refresh_minutes",15);return n==0?"Off":"Every "+n+" minutes";}
    private void addSetting(String title,String summary,Runnable action){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(dp(12),dp(12),dp(12),dp(12));row.setBackground(ThemeUtils.focusBackground(this));row.setFocusable(true);row.setClickable(true);
        row.addView(text(title,15,true));row.addView(text(summary,12,false));row.setOnClickListener(v->action.run());row.setOnFocusChangeListener((v,f)->row.setBackground(f?ThemeUtils.focusOutlineBackground(this):ThemeUtils.focusBackground(this)));
        forecastList.addView(row);View line=new View(this);line.setBackgroundColor(ThemeUtils.divider(this));forecastList.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
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
        if(android.os.Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},LOCATION_PERMISSION_REQUEST);return;
        }
        LocationManager lm = (LocationManager)getSystemService(LOCATION_SERVICE);
        boolean enabled = false;
        try { enabled = lm != null && (lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)); }
        catch (Exception ignored) { }
        if (!enabled) {
            setLoading(false, null);
            new AlertDialog.Builder(this).setTitle("Turn on device location")
                .setMessage("Location is turned off. Enable it in Android settings for a more accurate forecast. Network-based location can be approximate.")
                .setPositiveButton("Open settings", (d,w) -> {
                    try { startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)); }
                    catch (Exception e) { fallbackIpLocation(); }
                })
                .setNegativeButton("Use approximate location", (d,w) -> fallbackIpLocation())
                .show();
            return;
        }
        findCurrentLocation();
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==LOCATION_PERMISSION_REQUEST){boolean granted=false;for(int r:grantResults)if(r==PackageManager.PERMISSION_GRANTED)granted=true;if(granted)findCurrentLocation();else fallbackIpLocation();}
    }

    @SuppressLint("MissingPermission")
    private void findCurrentLocation(){
        if(android.os.Build.VERSION.SDK_INT>=23
            && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED
            && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            fallbackIpLocation();
            return;
        }
        final LocationManager manager=(LocationManager)getSystemService(LOCATION_SERVICE);
        if(manager==null){fallbackIpLocation();return;}
        setLoading(true,"Getting a fresh GPS/network fix…");
        if(locationTimeout!=null)handler.removeCallbacks(locationTimeout);
        if(pendingLocationListener!=null)try{manager.removeUpdates(pendingLocationListener);}catch(Exception ignored){}
        pendingLocationListener=null;

        final Location[] best={null};
        final long requestStarted=System.currentTimeMillis();

        // Keep a recent cached fix only as a timeout fallback. Always request
        // a new provider update first; a cached fix can still point to the wrong area.
        try{
            for(String provider:manager.getProviders(true)){
                try{
                    Location cached=manager.getLastKnownLocation(provider);
                    if(cached==null)continue;
                    long age=requestStarted-cached.getTime();
                    if(age>=0&&age<=30000L&&cached.hasAccuracy()&&cached.getAccuracy()<=1500f)
                        best[0]=betterLocation(best[0],cached);
                }catch(SecurityException|IllegalArgumentException ignored){}
            }
        }catch(Exception ignored){}

        final boolean[] finished={false};
        pendingLocationListener=new LocationListener(){
            @Override public void onLocationChanged(Location location){
                if(finished[0]||location==null)return;
                long age=System.currentTimeMillis()-location.getTime();
                if(age<0||age>30000L)return;
                if(location.hasAccuracy()&&location.getAccuracy()>1500f)return;
                best[0]=betterLocation(best[0],location);
                // Prefer a genuinely accurate fix. Do not immediately accept
                // the first coarse network estimate while GPS is still resolving.
                if(location.hasAccuracy()&&location.getAccuracy()<=100f){
                    finished[0]=true;
                    stopLocationUpdates(manager);
                    onCurrentLocation(location);
                }
            }
            @Override public void onStatusChanged(String provider,int status,Bundle extras){}
            @Override public void onProviderEnabled(String provider){}
            @Override public void onProviderDisabled(String provider){}
        };
        boolean requested=false;
        try{
            for(String provider:manager.getProviders(true)){
                try{
                    manager.requestLocationUpdates(provider,1000L,0f,pendingLocationListener,Looper.getMainLooper());
                    requested=true;
                }catch(SecurityException|IllegalArgumentException ignored){}
            }
        }catch(Exception ignored){}
        if(!requested){
            stopLocationUpdates(manager);
            fallbackIpLocation();
            return;
        }
        locationTimeout=()->{
            if(finished[0])return;
            finished[0]=true;
            stopLocationUpdates(manager);
            Location result=best[0];
            // A fresh fix within 1.5 km is usable as a last resort; otherwise
            // IP geolocation is more honest than presenting a bad GPS result.
            if(result!=null&&result.hasAccuracy()&&result.getAccuracy()<=1500f)
                onCurrentLocation(result);
            else fallbackIpLocation();
        };
        handler.postDelayed(locationTimeout,20000L);
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

    private void onCurrentLocation(Location location){
        double lat=location.getLatitude(),lon=location.getLongitude();
        new Thread(()->{
            String label="Current location";
            try{Geocoder g=new Geocoder(this,Locale.US);List<Address>a=g.getFromLocation(lat,lon,1);if(a!=null&&!a.isEmpty()){Address x=a.get(0);String city=x.getLocality();if(city==null||city.trim().isEmpty())city=x.getSubAdminArea();String state=x.getAdminArea();if(city!=null&&!city.isEmpty())label=city+(state==null||state.isEmpty()?"":", "+state);}}catch(Exception ignored){}
            String finalLabel=label;
            handler.post(()->useCurrentCoordinates(lat,lon,finalLabel));
        }).start();
    }

    private void fallbackIpLocation(){
        setLoading(true,"Finding an approximate location…");
        new Thread(()->{
            try{
                JSONObject ip=getJson("https://ipapi.co/json/");
                double lat=ip.getDouble("latitude"),lon=ip.getDouble("longitude");
                String city=ip.optString("city","Nearby area"),region=ip.optString("region_code",ip.optString("region",""));
                handler.post(()->useCurrentCoordinates(lat,lon,city+(region.isEmpty()?"":", "+region)));
            }catch(Exception e){handler.post(()->{setLoading(false,null);showWeatherError("Couldn't determine your location. Turn on Location permission and device location, or add a city/ZIP under Weather settings.\n\n"+e.getMessage());});}
        }).start();
    }

    private void useCurrentCoordinates(double lat,double lon,String label){
        prefs().edit().putString("weather_location",label).putString("weather_selected_location","").putString("weather_location_source","current").putFloat("weather_lat",(float)lat).putFloat("weather_lon",(float)lon).apply();
        if(locationTitle!=null)locationTitle.setText(label+"  ▾");
        fetchForecastAt(lat,lon);
    }

    private void scheduleAutoRefresh(){
        if(autoRefreshRunnable!=null)handler.removeCallbacks(autoRefreshRunnable);
        int minutes=prefs().getInt("weather_refresh_minutes",15);
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
        private final Path boltPath = new Path();
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
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float scale = Math.min(getWidth(), getHeight()) / 64f;
            canvas.save();
            canvas.translate((getWidth()-64f*scale)/2f,(getHeight()-64f*scale)/2f);
            canvas.scale(scale,scale);
            boolean thunder=condition.contains("thunder");
            boolean rain=condition.contains("rain")||condition.contains("shower")||condition.contains("drizzle")||thunder;
            boolean snow=condition.contains("snow")||condition.contains("sleet")||condition.contains("ice")||condition.contains("blizzard");
            boolean fog=condition.contains("fog")||condition.contains("haze")||condition.contains("smoke");
            boolean cloudy=condition.contains("cloud")||condition.contains("overcast")||condition.contains("partly")||condition.contains("mostly")||rain||snow||fog;
            boolean sunny=condition.contains("sun")||condition.contains("clear")||condition.contains("partly")||condition.contains("mostly");
            // Soft tinted medallion gives the forecast art a more polished,
            // illustrated look while keeping the artwork crisp at small sizes.
            paint.setStyle(Paint.Style.FILL);
            int halo = thunder ? 0x333f51b5 : (rain || snow || fog ? 0x332196f3 : 0x33ffb74d);
            paint.setColor(halo);
            canvas.drawCircle(32,32,30,paint);
            paint.setColor(0x22ffffff);
            canvas.drawCircle(22,18,12,paint);
            if (sunny) {
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3.2f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(0xffffb83d);
                for(int i=0;i<8;i++){double a=Math.PI*i/4;float x=(float)Math.cos(a),y=(float)Math.sin(a);canvas.drawLine(23+x*14,23+y*14,23+x*19,23+y*19,paint);}
                paint.setStyle(Paint.Style.FILL);canvas.drawCircle(23,23,11,paint);
            }
            if (cloudy || !sunny) {
                paint.setStyle(Paint.Style.FILL);paint.setColor(0xff9baec2);
                canvas.drawCircle(25,35,13,paint);canvas.drawCircle(39,32,15,paint);canvas.drawCircle(49,39,9,paint);
                canvas.drawRoundRect(15,34,55,47,7,7,paint);
                paint.setColor(0xffdce6ef);
                canvas.drawCircle(25,32,10,paint);canvas.drawCircle(38,29,12,paint);canvas.drawCircle(48,37,7,paint);
                canvas.drawRoundRect(18,34,53,43,5,5,paint);
            }
            if (thunder) {
                paint.setStyle(Paint.Style.FILL);paint.setColor(0xffffc247);
                boltPath.reset();boltPath.moveTo(36,42);boltPath.lineTo(27,53);boltPath.lineTo(34,53);boltPath.lineTo(30,62);boltPath.lineTo(43,48);boltPath.lineTo(36,48);boltPath.close();canvas.drawPath(boltPath,paint);
            } else if (rain) {
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3.2f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(0xff42a5f5);
                for(int i=0;i<3;i++)canvas.drawLine(25+i*10,49,21+i*10,56,paint);
            } else if (snow) {
                paint.setStyle(Paint.Style.FILL);paint.setColor(0xff65c7f7);
                canvas.drawCircle(23,53,2.6f,paint);canvas.drawCircle(36,55,2.6f,paint);canvas.drawCircle(48,52,2.6f,paint);
            } else if (fog) {
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2.5f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(0xff90a4ae);
                canvas.drawLine(13,51,52,51,paint);canvas.drawLine(19,57,46,57,paint);
            }
            canvas.restore();
        }
    }

    private String weatherEmoji(String f){
        String v=f==null?"":f.toLowerCase(Locale.US);
        if(v.contains("thunder"))return "⛈️";if(v.contains("snow")||v.contains("sleet")||v.contains("ice")||v.contains("blizzard"))return "🌨️";
        if(v.contains("rain")||v.contains("shower")||v.contains("drizzle"))return "🌧️";if(v.contains("fog")||v.contains("haze")||v.contains("smoke"))return "🌫️";
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
