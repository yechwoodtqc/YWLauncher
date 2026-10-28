package com.lge.ywlauncher;

import android.Manifest;
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
    private ImageButton refreshButton, settingsButton;
    private LocationListener pendingLocationListener;
    private Runnable locationTimeout, autoRefreshRunnable;
    private boolean configureMode, loading, hourlyMode, detailMode;
    private JSONArray dailyPeriods = new JSONArray(), hourlyPeriods = new JSONArray();
    private JSONArray discussionProducts = new JSONArray();
    private int detailIndex = 0;
    private String currentPage = "forecast";
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
        else {
            SavedPlace selected = selectedPlace();
            if (selected != null) fetchForecastAt(selected.lat, selected.lon);
            else detectLocation();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        scheduleAutoRefresh();
    }
    @Override protected void onPause() {
        handler.removeCallbacks(autoRefreshRunnable);
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

        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        forecastList = new LinearLayout(this);
        forecastList.setOrientation(LinearLayout.VERTICAL);
        scroller.addView(forecastList, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroller, new LinearLayout.LayoutParams(-1, 0, 1));
        showForecastPageHeader();
    }

    private void rebuildToolbar(String titleText, boolean showRefresh, boolean showSettings) {
        toolbar.removeAllViews();
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
            settingsButton.setImageResource(android.R.drawable.ic_menu_preferences);
            settingsButton.setColorFilter(ThemeUtils.accent(this));
            settingsButton.setContentDescription("Weather settings");
            settingsButton.setBackground(outlineFocusBackground());
            settingsButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            settingsButton.setPadding(dp(7), dp(7), dp(7), dp(7));
            settingsButton.setFocusable(true);
            settingsButton.setOnClickListener(v -> showWeatherSettings());
            toolbar.addView(settingsButton, new LinearLayout.LayoutParams(dp(46), dp(46)));
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
        locationTitle.setText(place == null ? "Choose location ▾" : place.name + "  ▾");
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
        SavedPlace place = selectedPlace();
        if (place != null) fetchForecastAt(place.lat, place.lon); else detectLocation();
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
                String generated = daily.getJSONObject("properties").optString("generatedAt", "");
                String office = properties.optString("cwa", "");
                String pointName = properties.optString("relativeLocation", "");
                JSONArray finalHours = hours;
                String finalGenerated = generated;
                handler.post(() -> {
                    dailyPeriods = days; hourlyPeriods = finalHours;
                    setLoading(false, null);
                    renderForecast();
                    String now = new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(new Date());
                    updated.setText("Last updated: " + now);
                    String forecastTime = finalGenerated;
                    if (!forecastTime.isEmpty()) {
                        try {
                            java.text.SimpleDateFormat input = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
                            Date date = input.parse(forecastTime);
                            forecastTime = date == null ? forecastTime : new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(date);
                        } catch (Exception ignored) {}
                    }
                    forecasted.setText("Forecast issued: " + (forecastTime.isEmpty() ? "time not provided" : forecastTime));
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
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        Button daily = new Button(this); daily.setText("Daily");
        Button hourly = new Button(this); hourly.setText("Hourly");
        ThemeUtils.tint(daily, this); ThemeUtils.tint(hourly, this);
        daily.setTextSize(textSize(12)); hourly.setTextSize(textSize(12));
        daily.setAlpha(hourlyMode ? 0.65f : 1f); hourly.setAlpha(hourlyMode ? 1f : 0.65f);
        tabs.addView(daily,new LinearLayout.LayoutParams(0,dp(42),1));
        tabs.addView(hourly,new LinearLayout.LayoutParams(0,dp(42),1));
        daily.setOnClickListener(v->{hourlyMode=false;renderForecast();});
        hourly.setOnClickListener(v->{hourlyMode=true;renderForecast();});
        forecastList.addView(tabs,rowMargins());
        if (hourlyMode) {
            addSectionTitle("Next hours");
            int limit=Math.min(24,hourlyPeriods.length());
            for(int i=0;i<limit;i++) addHourlyPeriod(hourlyPeriods.optJSONObject(i),i);
        } else {
            addCurrentSummary();
            addSectionTitle("Daily forecast · select a day for details");
            int limit=Math.min(14,dailyPeriods.length());
            for(int i=0;i<limit;i++) addDailyPeriod(dailyPeriods.optJSONObject(i),i);
        }
        if (!hourlyMode) addDiscussionPreview();
    }

    private void addCurrentSummary() {
        JSONObject current = hourlyPeriods.length()>0 ? hourlyPeriods.optJSONObject(0) : (dailyPeriods.length()>0 ? dailyPeriods.optJSONObject(0) : null);
        if(current==null)return;
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(14),dp(12),dp(14),dp(12)); card.setBackground(cardBackground());
        TextView heading = text("NOW  "+weatherEmoji(current.optString("shortForecast",""))+"  "+current.optString("temperature","—")+"°"+current.optString("temperatureUnit","F"),22,true);
        card.addView(heading);
        card.addView(text(current.optString("shortForecast","Current conditions"),14,false));
        String humidity="—",precip="—";
        JSONObject hum=current.optJSONObject("relativeHumidity"); if(hum!=null&&!hum.isNull("value"))humidity=hum.optString("value")+"%";
        JSONObject pp=current.optJSONObject("probabilityOfPrecipitation"); if(pp!=null&&!pp.isNull("value"))precip=pp.optString("value")+"%";
        card.addView(text("Wind "+current.optString("windSpeed","—")+" "+current.optString("windDirection","")+"   Humidity "+humidity+"   Precip. "+precip,12,false));
        forecastList.addView(card,rowMargins());
    }

    private void addDailyPeriod(JSONObject period,int index) {
        if(period==null)return;
        LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.HORIZONTAL); card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(8),dp(7),dp(8),dp(7)); card.setBackground(ThemeUtils.focusBackground(this)); card.setFocusable(true); card.setClickable(true);
        TextView icon=text(weatherEmoji(period.optString("shortForecast","")),26,true); icon.setGravity(Gravity.CENTER);
        card.addView(icon,new LinearLayout.LayoutParams(dp(44),dp(46)));
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(dp(8),0,dp(3),0);
        info.addView(text(period.optString("name","Forecast"),14,true));
        info.addView(text(period.optString("temperature","—")+"°"+period.optString("temperatureUnit","F")+" · "+period.optString("shortForecast",""),12,false));
        String precip="—";JSONObject pp=period.optJSONObject("probabilityOfPrecipitation");if(pp!=null&&!pp.isNull("value"))precip=pp.optString("value")+"%";
        info.addView(text("Wind "+period.optString("windSpeed","—")+" "+period.optString("windDirection","")+" · Precip. "+precip,11,false));
        card.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        TextView arrow=text("›",24,false);card.addView(arrow,new LinearLayout.LayoutParams(dp(22),-2));
        card.setOnClickListener(v->{detailIndex=index;showDayDetail(index);});
        card.setOnFocusChangeListener((v,focused)->{
            card.setBackground(focused?focusOutlineBackground():ThemeUtils.focusBackground(this));
            if(focused){icon.setTextColor(ThemeUtils.accent(this));}
            else icon.setTextColor(ThemeUtils.text(this));
        });
        forecastList.addView(card,rowMargins());
    }

    private void addHourlyPeriod(JSONObject period,int index) {
        if(period==null)return;
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.HORIZONTAL);card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(8),dp(8),dp(8),dp(8));card.setBackground(ThemeUtils.focusBackground(this));card.setFocusable(true);card.setClickable(true);
        card.addView(text(weatherEmoji(period.optString("shortForecast","")),25,true),new LinearLayout.LayoutParams(dp(42),dp(44)));
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(dp(7),0,0,0);
        String time=period.optString("startTime",""); if(time.length()>=16)time=time.substring(5,10)+" "+time.substring(11,16);
        info.addView(text(time+" · "+period.optString("temperature","—")+"°"+period.optString("temperatureUnit","F"),14,true));
        info.addView(text(period.optString("shortForecast",""),12,false));
        JSONObject pp=period.optJSONObject("probabilityOfPrecipitation");
        info.addView(text("Wind "+period.optString("windSpeed","—")+" "+period.optString("windDirection","")+" · Precip. "+(pp==null||pp.isNull("value")?"—":pp.optString("value")+"%"),11,false));
        card.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        card.setOnClickListener(v->showHourDetail(index));
        card.setOnFocusChangeListener((v,focused)->card.setBackground(focused?focusOutlineBackground():ThemeUtils.focusBackground(this)));
        forecastList.addView(card,rowMargins());
    }

    private void showDayDetail(int index) {
        detailMode=true; currentPage="day-detail";
        JSONObject p=dailyPeriods.optJSONObject(index); if(p==null)return;
        rebuildToolbar("Forecast details",false,false);
        forecastList.removeAllViews();
        LinearLayout navigation=new LinearLayout(this);navigation.setGravity(Gravity.CENTER_VERTICAL);
        Button previous=new Button(this);previous.setText("‹ Previous");ThemeUtils.tint(previous,this);
        Button next=new Button(this);next.setText("Next ›");ThemeUtils.tint(next,this);
        navigation.addView(previous,new LinearLayout.LayoutParams(0,dp(44),1));navigation.addView(next,new LinearLayout.LayoutParams(0,dp(44),1));
        previous.setEnabled(index>0);next.setEnabled(index<dailyPeriods.length()-1);
        previous.setOnClickListener(v->showDayDetail(Math.max(0,detailIndex-1)));
        next.setOnClickListener(v->showDayDetail(Math.min(dailyPeriods.length()-1,detailIndex+1)));
        forecastList.addView(navigation,rowMargins());
        forecastList.addView(text(weatherEmoji(p.optString("shortForecast","")),52,true));
        forecastList.addView(text(p.optString("name","Forecast"),22,true));
        forecastList.addView(text(p.optString("temperature","—")+"°"+p.optString("temperatureUnit","F")+" · "+p.optString("shortForecast",""),16,false));
        forecastList.addView(text(p.optString("detailedForecast","No additional forecast details available."),textSize(15),false));
        String precip="Not available";JSONObject pp=p.optJSONObject("probabilityOfPrecipitation");if(pp!=null&&!pp.isNull("value"))precip=pp.optString("value")+"%";
        addDetailLine("Wind",p.optString("windSpeed","—")+" "+p.optString("windDirection",""));
        addDetailLine("Precipitation chance",precip);
        addDetailLine("Temperature",p.optString("temperature","—")+"°"+p.optString("temperatureUnit","F"));
        Button back=new Button(this);back.setText("Back to forecast");ThemeUtils.tint(back,this);back.setOnClickListener(v->{detailMode=false;currentPage="forecast";showForecastPage();});
        forecastList.addView(back,rowMargins());
    }

    private void showHourDetail(int index) {
        JSONObject p=hourlyPeriods.optJSONObject(index); if(p==null)return;
        new AlertDialog.Builder(this).setTitle(weatherEmoji(p.optString("shortForecast",""))+" "+p.optString("shortForecast","Hourly details"))
            .setMessage(p.optString("startTime","")+"\n"+p.optString("temperature","—")+"°"+p.optString("temperatureUnit","F")+"\nWind: "+p.optString("windSpeed","—")+" "+p.optString("windDirection","")+"\n"+p.optString("detailedForecast",""))
            .setPositiveButton("OK",null).show();
    }

    private void addDiscussionPreview() {
        TextView title=text("Forecast discussion",14,true);title.setPadding(dp(4),dp(14),dp(4),dp(4));forecastList.addView(title);
        TextView preview=text("Loading local forecast discussion…",12,false);preview.setPadding(dp(6),dp(3),dp(6),dp(8));forecastList.addView(preview);
        title.setFocusable(true);title.setClickable(true);title.setOnClickListener(v->showDiscussionPage());
        preview.setOnClickListener(v->showDiscussionPage());
        if(discussionProducts.length()>0) preview.setText(discussionProducts.optJSONObject(0)==null?"Open discussion":discussionProducts.optJSONObject(0).optString("preview","Open latest forecast discussion"));
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
        currentPage="discussion";rebuildToolbar("Forecast discussion",true,false);forecastList.removeAllViews();
        String body=discussionProducts.length()>0?discussionProducts.optJSONObject(0).optString("text","No discussion available."):"Forecast discussion is not available yet. Refresh the forecast and try again.";
        TextView content=text(body,textSize(13),false);content.setPadding(dp(6),dp(6),dp(6),dp(20));forecastList.addView(content);
        Button back=new Button(this);back.setText("Back to forecast");ThemeUtils.tint(back,this);back.setOnClickListener(v->showForecastPage());forecastList.addView(back,rowMargins());
    }

    private void showForecastPage() {
        currentPage="forecast";removeHeaderRows();
        // Restore the three forecast header rows immediately after the toolbar.
        showForecastPageHeader();
        renderForecast();
        SavedPlace place=selectedPlace();
        if(place!=null){setSelectedPlaceTitle();if(updated!=null&&updated.getText().length()==0)fetchForecastAt(place.lat,place.lon);}
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
            row.setOnClickListener(v->{prefs().edit().putString("weather_selected_location",place.name).putString("weather_location",place.name).putFloat("weather_lat",(float)place.lat).putFloat("weather_lon",(float)place.lon).apply();showForecastPage();fetchForecastAt(place.lat,place.lon);});
            row.setOnFocusChangeListener((v,focused)->row.setBackground(focused?focusOutlineBackground():ThemeUtils.focusBackground(this)));
            forecastList.addView(row,rowMargins());
        }
        Button current=new Button(this);current.setText("Use current location");ThemeUtils.tint(current,this);current.setOnClickListener(v->{prefs().edit().putString("weather_selected_location","").putString("weather_location","Use current location").apply();showForecastPage();detectLocation();});forecastList.addView(current,rowMargins());
        Button back=new Button(this);back.setText("Back to forecast");ThemeUtils.tint(back,this);back.setOnClickListener(v->showForecastPage());forecastList.addView(back,rowMargins());
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
                String q=s.toString().trim();suggestions.removeAllViews();
                if(q.length()<2){TextView hint=text("Type at least 2 characters.",12,false);suggestions.addView(hint);return;}
                TextView searching=text("Searching suggestions…",12,false);suggestions.addView(searching);
                pending[0]=()->searchLocationSuggestions(q,suggestions);
                handler.postDelayed(pending[0],450);
            }
            @Override public void afterTextChanged(Editable e){}
        });
        Button current=new Button(this);current.setText("Use current location");ThemeUtils.tint(current,this);current.setOnClickListener(v->{prefs().edit().putString("weather_location","Use current location").putString("weather_selected_location","").apply();showForecastPage();detectLocation();});forecastList.addView(current,rowMargins());
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
                        for(int i=0;i<matches.length();i++){JSONObject p=matches.optJSONObject(i);String name=p.optString("display_name",query);found.add(new SavedPlace(name,Double.parseDouble(p.getString("lat")),Double.parseDouble(p.getString("lon"))));}
                    }
                }
            }catch(Exception ignored){}
            handler.post(()->{
                if(!"add-location".equals(currentPage))return;
                suggestions.removeAllViews();
                if(found.isEmpty()){suggestions.addView(text("No suggestions found. Try a city and state or a 5-digit ZIP.",12,false));return;}
                for(SavedPlace place:found){
                    LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(10),dp(10),dp(10),dp(10));row.setFocusable(true);row.setClickable(true);row.setBackground(ThemeUtils.focusBackground(this));
                    TextView name=text(place.name,13,true);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));row.addView(text("＋",22,true));
                    row.setOnClickListener(v->saveAndSelectLocation(place));row.setOnFocusChangeListener((v,focused)->row.setBackground(focused?focusOutlineBackground():ThemeUtils.focusBackground(this));
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
        showForecastPage();fetchForecastAt(place.lat,place.lon);
    }

    private void showWeatherSettings() {
        currentPage="settings";removeHeaderRows();rebuildToolbar("Weather settings",true,false);forecastList.removeAllViews();
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
        addSetting("Refresh now","Update forecast immediately",()->{showForecastPage();refreshCurrentPage();});
        addSetting("Back to forecast","Return to the forecast",()->showForecastPage());
    }

    private String fontScaleLabel(){return fontScale<0.95f?"Small":fontScale>1.25f?"Extra large":fontScale>1.05f?"Large":"Normal";}
    private String refreshLabel(){int n=prefs().getInt("weather_refresh_minutes",15);return n==0?"Off":"Every "+n+" minutes";}
    private void addSetting(String title,String summary,Runnable action){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(dp(12),dp(12),dp(12),dp(12));row.setBackground(ThemeUtils.focusBackground(this));row.setFocusable(true);row.setClickable(true);
        row.addView(text(title,15,true));row.addView(text(summary,12,false));row.setOnClickListener(v->action.run());row.setOnFocusChangeListener((v,f)->row.setBackground(f?focusOutlineBackground():ThemeUtils.focusBackground(this));
        forecastList.addView(row);View line=new View(this);line.setBackgroundColor(ThemeUtils.divider(this));forecastList.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
    }

    private void loadSavedPlaces(){
        savedPlaces.clear();
        String raw=prefs().getString("weather_saved_locations","");
        if(!raw.isEmpty())try{JSONArray a=new JSONArray(raw);for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null)savedPlaces.add(new SavedPlace(o.optString("name","Saved location"),o.optDouble("lat"),o.optDouble("lon")));}}catch(Exception ignored){}
        if(savedPlaces.isEmpty()&&prefs().contains("weather_lat")&&prefs().contains("weather_lon")){
            String name=prefs().getString("weather_location","");if(!name.isEmpty()&&!"Use current location".equals(name)&&!"Current location".equals(name))savedPlaces.add(new SavedPlace(name,prefs().getFloat("weather_lat",0),prefs().getFloat("weather_lon",0)));
            persistSavedPlaces();
        }
    }
    private void persistSavedPlaces(){JSONArray a=new JSONArray();for(SavedPlace p:savedPlaces)try{a.put(p.json());}catch(Exception ignored){}prefs().edit().putString("weather_saved_locations",a.toString()).apply();}
    private String selectedPlaceName(){return prefs().getString("weather_selected_location",prefs().getString("weather_location",""));}
    private SavedPlace selectedPlace(){
        String name=selectedPlaceName();for(SavedPlace p:savedPlaces)if(p.name.equalsIgnoreCase(name))return p;
        if(prefs().contains("weather_lat")&&prefs().contains("weather_lon")&&!name.isEmpty()&&!"Use current location".equals(name)&&!"Current location".equals(name))return new SavedPlace(name,prefs().getFloat("weather_lat",0),prefs().getFloat("weather_lon",0));
        return null;
    }

    private void detectLocation(){
        if(android.os.Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},LOCATION_PERMISSION_REQUEST);return;
        }
        findCurrentLocation();
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==LOCATION_PERMISSION_REQUEST){boolean granted=false;for(int r:grantResults)if(r==PackageManager.PERMISSION_GRANTED)granted=true;if(granted)findCurrentLocation();else fallbackIpLocation();}
    }

    private void findCurrentLocation(){
        setLoading(true,"Finding your location…");
        try{
            LocationManager manager=(LocationManager)getSystemService(LOCATION_SERVICE);Location best=null;
            for(String provider:manager.getProviders(true))try{Location l=manager.getLastKnownLocation(provider);if(l!=null&&(best==null||l.getTime()>best.getTime()))best=l;}catch(Exception ignored){}
            if(best!=null&&System.currentTimeMillis()-best.getTime()<6L*60L*60L*1000L){onCurrentLocation(best);return;}
            List<String> providers=manager.getProviders(true);
            if(pendingLocationListener!=null)try{manager.removeUpdates(pendingLocationListener);}catch(Exception ignored){}
            if(providers.isEmpty()){fallbackIpLocation();return;}
            pendingLocationListener=new LocationListener(){
                @Override public void onLocationChanged(Location l){if(pendingLocationListener==null)return;try{manager.removeUpdates(this);}catch(Exception ignored){}pendingLocationListener=null;if(locationTimeout!=null)handler.removeCallbacks(locationTimeout);onCurrentLocation(l);}
                @Override public void onStatusChanged(String p,int s,Bundle b){}
                @Override public void onProviderEnabled(String p){}
                @Override public void onProviderDisabled(String p){}
            };
            for(String p:providers)try{manager.requestLocationUpdates(p,1000L,0f,pendingLocationListener,Looper.getMainLooper());}catch(Exception ignored){}
            locationTimeout=()->{if(pendingLocationListener!=null){try{manager.removeUpdates(pendingLocationListener);}catch(Exception ignored){}pendingLocationListener=null;fallbackIpLocation();}};
            handler.postDelayed(locationTimeout,12000);
        }catch(Exception e){fallbackIpLocation();}
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
        prefs().edit().putString("weather_location",label).putString("weather_selected_location","").putFloat("weather_lat",(float)lat).putFloat("weather_lon",(float)lon).apply();
        if(locationTitle!=null)locationTitle.setText(label+"  ▾");
        fetchForecastAt(lat,lon);
    }

    private void scheduleAutoRefresh(){
        if(autoRefreshRunnable!=null)handler.removeCallbacks(autoRefreshRunnable);
        int minutes=prefs().getInt("weather_refresh_minutes",15);
        if(minutes<=0||isFinishing())return;
        autoRefreshRunnable=()->{if("forecast".equals(currentPage)){SavedPlace p=selectedPlace();if(p!=null)fetchForecastAt(p.lat,p.lon);else if(prefs().contains("weather_lat"))fetchForecastAt(prefs().getFloat("weather_lat",0),prefs().getFloat("weather_lon",0));}scheduleAutoRefresh();};
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

    @Override public boolean onKeyDown(int keyCode,KeyEvent event){
        if("day-detail".equals(currentPage)){
            if(keyCode==KeyEvent.KEYCODE_DPAD_LEFT){showDayDetail(Math.max(0,detailIndex-1));return true;}
            if(keyCode==KeyEvent.KEYCODE_DPAD_RIGHT){showDayDetail(Math.min(dailyPeriods.length()-1,detailIndex+1));return true;}
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
