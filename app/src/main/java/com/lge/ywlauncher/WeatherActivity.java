package com.lge.ywlauncher;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.location.*;
import android.os.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.*;
import java.net.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class WeatherActivity extends Activity {
    private static final int LOCATION_PERMISSION_REQUEST = 71;
    private final Handler handler = new Handler();
    private TextView locationTitle, updated;
    private LinearLayout forecastList;
    private ImageButton refreshButton;
    private LocationListener pendingLocationListener;
    private Runnable locationTimeout;
    private boolean configureMode;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ThemeUtils.apply(this);
        configureMode = getIntent().getBooleanExtra("configure_location", false);
        buildScreen();
        String saved = prefs().getString("weather_location", "");
        if (configureMode) showLocationDialog();
        else if (!prefs().contains("weather_location") || saved.trim().isEmpty() || "Use current location".equals(saved)) {
            detectLocation();
        } else {
            updateLocationTitle(saved);
            fetchForecast();
        }
    }

    private android.content.SharedPreferences prefs() { return getSharedPreferences("launcher", 0); }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(8), dp(10), dp(8));
        root.setBackgroundColor(ThemeUtils.surface(this));
        setContentView(root);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("Weather");
        title.setTextColor(ThemeUtils.text(this));
        title.setTextSize(21);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        toolbar.addView(title, new LinearLayout.LayoutParams(-2, -2));

        View spacer = new View(this);
        toolbar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));

        refreshButton = new ImageButton(this);
        refreshButton.setImageResource(android.R.drawable.ic_popup_sync);
        refreshButton.setColorFilter(ThemeUtils.accent(this));
        refreshButton.setContentDescription("Refresh weather");
        refreshButton.setBackground(ThemeUtils.focusBackground(this));
        refreshButton.setPadding(dp(8), dp(8), dp(8), dp(8));
        refreshButton.setOnClickListener(v -> fetchForecast());
        toolbar.addView(refreshButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(48)));

        locationTitle = new TextView(this);
        locationTitle.setTextColor(ThemeUtils.secondaryText(this));
        locationTitle.setTextSize(13);
        locationTitle.setPadding(dp(3), 0, dp(3), dp(5));
        root.addView(locationTitle);

        updated = new TextView(this);
        updated.setTextColor(ThemeUtils.secondaryText(this));
        updated.setTextSize(11);
        updated.setPadding(dp(3), 0, dp(3), dp(6));
        root.addView(updated);

        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        forecastList = new LinearLayout(this);
        forecastList.setOrientation(LinearLayout.VERTICAL);
        scroller.addView(forecastList, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroller, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void updateLocationTitle(String place) {
        locationTitle.setText("Forecast for " + (place == null || place.trim().isEmpty() ? "current location" : place));
    }

    private void showLocationDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(8), dp(20), dp(4));
        TextView note = new TextView(this);
        note.setText("Enter a city and state or ZIP code, or use your device's current location.");
        note.setTextColor(ThemeUtils.secondaryText(this));
        layout.addView(note);
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("City, state or ZIP code");
        input.setTextColor(ThemeUtils.text(this));
        input.setHintTextColor(ThemeUtils.secondaryText(this));
        String current = prefs().getString("weather_location", "");
        if (!current.isEmpty() && !"Use current location".equals(current) && !current.startsWith("Current location")) input.setText(current);
        layout.addView(input);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Weather location").setView(layout)
            .setPositiveButton("Use location", (d, which) -> {
                String place = input.getText().toString().trim();
                if (place.isEmpty()) { detectLocation(); return; }
                prefs().edit().putString("weather_location", place).remove("weather_lat").remove("weather_lon").apply();
                updateLocationTitle(place);
                fetchForecast();
            })
            .setNeutralButton("Use current location", null)
            .setNegativeButton("Cancel", (d, which) -> { if (!prefs().contains("weather_location")) detectLocation(); })
            .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            prefs().edit().putString("weather_location", "Use current location").apply();
            dialog.dismiss();
            detectLocation();
        }));
        dialog.show();
        if (configureMode) input.requestFocus();
    }

    private void detectLocation() {
        if (android.os.Build.VERSION.SDK_INT >= 23
            && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION_REQUEST);
            return;
        }
        findCurrentLocation();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            boolean granted = false;
            for (int result : grantResults) if (result == PackageManager.PERMISSION_GRANTED) granted = true;
            if (granted) findCurrentLocation();
            else showLocationError("Location permission was denied. Open Settings → Weather location to enter a city or ZIP.");
        }
    }

    private void findCurrentLocation() {
        forecastList.removeAllViews();
        addMessage("Finding your current location…");
        try {
            LocationManager manager = (LocationManager)getSystemService(LOCATION_SERVICE);
            Location best = null;
            for (String provider : manager.getProviders(true)) {
                try {
                    Location candidate = manager.getLastKnownLocation(provider);
                    if (candidate != null && (best == null || candidate.getTime() > best.getTime())) best = candidate;
                } catch (IllegalArgumentException | SecurityException ignored) {}
            }
            if (best != null && System.currentTimeMillis() - best.getTime() < 24L * 60L * 60L * 1000L) {
                onCurrentLocation(best);
                return;
            }
            List<String> providers = manager.getProviders(true);
            if (providers.isEmpty()) {
                showLocationError("Location is turned off. Enable Location in phone settings, or choose a city/ZIP in Settings.");
                return;
            }
            if (pendingLocationListener != null) {
                try { manager.removeUpdates(pendingLocationListener); } catch (SecurityException ignored) {}
            }
            pendingLocationListener = new LocationListener() {
                @Override public void onLocationChanged(Location location) {
                    if (pendingLocationListener == null) return;
                    try { manager.removeUpdates(this); } catch (SecurityException ignored) {}
                    pendingLocationListener = null;
                    if (locationTimeout != null) handler.removeCallbacks(locationTimeout);
                    onCurrentLocation(location);
                }
                @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
                @Override public void onProviderEnabled(String provider) {}
                @Override public void onProviderDisabled(String provider) {}
            };
            for (String provider : providers) {
                try { manager.requestLocationUpdates(provider, 1000L, 0f, pendingLocationListener, Looper.getMainLooper()); }
                catch (IllegalArgumentException | SecurityException ignored) {}
            }
            locationTimeout = () -> {
                if (pendingLocationListener != null) {
                    try { manager.removeUpdates(pendingLocationListener); } catch (SecurityException ignored) {}
                    pendingLocationListener = null;
                    showLocationError("Couldn't get a current location fix. Turn on Location or choose a city/ZIP in Settings.");
                }
            };
            handler.postDelayed(locationTimeout, 30000);
        } catch (SecurityException e) {
            showLocationError("Location permission is not available. Choose a city or ZIP in Settings.");
        }
    }

    private void onCurrentLocation(Location location) {
        prefs().edit().putFloat("weather_lat", (float)location.getLatitude())
            .putFloat("weather_lon", (float)location.getLongitude())
            .putString("weather_location", "Current location").apply();
        updateLocationTitle("Current location");
        fetchForecastAt(location.getLatitude(), location.getLongitude());
        new Thread(() -> {
            try {
                Geocoder geocoder = new Geocoder(this, Locale.US);
                List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                if (addresses != null && !addresses.isEmpty()) {
                    Address a = addresses.get(0);
                    String city = a.getLocality();
                    if (city == null || city.trim().isEmpty()) city = a.getSubAdminArea();
                    String state = a.getAdminArea();
                    String place = (city == null ? "" : city) + (state == null || state.isEmpty() ? "" : ", " + state);
                    if (!place.trim().isEmpty()) handler.post(() -> {
                        if ("Current location".equals(prefs().getString("weather_location", ""))) {
                            updateLocationTitle(place + " · current location");
                        }
                    });
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void fetchForecast() {
        String place = prefs().getString("weather_location", "");
        if (place.isEmpty() || "Use current location".equals(place)) { detectLocation(); return; }
        updateLocationTitle(place);
        if ("Current location".equals(place) && prefs().contains("weather_lat") && prefs().contains("weather_lon")) {
            fetchForecastAt(prefs().getFloat("weather_lat", 0), prefs().getFloat("weather_lon", 0));
            return;
        }
        refreshButton.setEnabled(false);
        forecastList.removeAllViews();
        addMessage("Loading forecast…");
        new Thread(() -> {
            try {
                double[] coordinates = geocode(place);
                handler.post(() -> fetchForecastAt(coordinates[0], coordinates[1]));
            } catch (Exception e) {
                handler.post(() -> {
                    forecastList.removeAllViews();
                    addMessage("Could not load the forecast. Check the location and internet connection.\n\n" + e.getMessage());
                    refreshButton.setEnabled(true);
                });
            }
        }).start();
    }

    private void fetchForecastAt(double latitude, double longitude) {
        refreshButton.setEnabled(false);
        forecastList.removeAllViews();
        addMessage("Loading forecast…");
        new Thread(() -> {
            try {
                JSONObject point = getJson("https://api.weather.gov/points/" + latitude + "," + longitude);
                JSONObject properties = point.getJSONObject("properties");
                String forecastUrl = properties.getString("forecast");
                String hourlyUrl = properties.optString("forecastHourly", "");
                JSONObject forecast = getJson(forecastUrl);
                JSONArray periods = forecast.getJSONObject("properties").getJSONArray("periods");
                JSONArray hourly = new JSONArray();
                if (!hourlyUrl.isEmpty()) {
                    try { hourly = getJson(hourlyUrl).getJSONObject("properties").getJSONArray("periods"); } catch (Exception ignored) {}
                }
                final JSONArray hourlyData = hourly;
                handler.post(() -> {
                    forecastList.removeAllViews();
                    addCurrentSummary(periods, hourlyData);
                    for (int i = 0; i < Math.min(8, periods.length()); i++) addForecastPeriod(periods.optJSONObject(i));
                    updated.setText("Updated " + new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(new Date()));
                    refreshButton.setEnabled(true);
                });
            } catch (Exception e) {
                handler.post(() -> {
                    forecastList.removeAllViews();
                    addMessage("Could not load the weather forecast. Check your connection or location.\n\n" + e.getMessage());
                    refreshButton.setEnabled(true);
                });
            }
        }).start();
    }

    private void addCurrentSummary(JSONArray periods, JSONArray hourly) {
        JSONObject current = hourly.length() > 0 ? hourly.optJSONObject(0) : (periods.length() > 0 ? periods.optJSONObject(0) : null);
        if (current == null) return;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(cardBackground());
        TextView heading = new TextView(this);
        heading.setText("NOW  " + weatherEmoji(current.optString("shortForecast", "")) + "  " + current.optString("temperature", "—") + "°" + current.optString("temperatureUnit", "F"));
        heading.setTextColor(ThemeUtils.text(this)); heading.setTextSize(22); heading.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(heading);
        TextView desc = new TextView(this);
        desc.setText(current.optString("shortForecast", "Current conditions"));
        desc.setTextColor(ThemeUtils.secondaryText(this)); desc.setTextSize(14);
        card.addView(desc);
        String humidity = "—";
        JSONObject hum = current.optJSONObject("relativeHumidity");
        if (hum != null && !hum.isNull("value")) humidity = hum.optString("value") + "%";
        String precip = "—";
        JSONObject probability = current.optJSONObject("probabilityOfPrecipitation");
        if (probability != null && !probability.isNull("value")) precip = probability.optString("value") + "%";
        TextView details = new TextView(this);
        details.setText("Wind  " + current.optString("windSpeed", "—") + " " + current.optString("windDirection", "") +
            "     Humidity  " + humidity + "     Precip.  " + precip);
        details.setTextColor(ThemeUtils.text(this)); details.setTextSize(12); details.setPadding(0, dp(6), 0, 0);
        card.addView(details);
        forecastList.addView(card, rowMargins());
        addSectionTitle("8-period forecast");
    }

    private void addForecastPeriod(JSONObject period) {
        if (period == null) return;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(10), dp(10), dp(10), dp(10));
        card.setBackground(cardBackground());

        TextView icon = new TextView(this);
        icon.setText(weatherEmoji(period.optString("shortForecast", "")));
        icon.setTextSize(27);
        icon.setGravity(Gravity.CENTER);
        card.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(52)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(8), 0, dp(4), 0);
        TextView name = new TextView(this);
        name.setText(period.optString("name", "Forecast"));
        name.setTextColor(ThemeUtils.text(this)); name.setTextSize(14); name.setTypeface(null, android.graphics.Typeface.BOLD);
        info.addView(name);
        TextView summary = new TextView(this);
        summary.setText(period.optString("temperature", "—") + "°" + period.optString("temperatureUnit", "F") + " · " + period.optString("shortForecast", ""));
        summary.setTextColor(ThemeUtils.text(this)); summary.setTextSize(12);
        info.addView(summary);
        TextView details = new TextView(this);
        String precip = "—";
        JSONObject probability = period.optJSONObject("probabilityOfPrecipitation");
        if (probability != null && !probability.isNull("value")) precip = probability.optString("value") + "%";
        details.setText("Wind " + period.optString("windSpeed", "—") + " " + period.optString("windDirection", "") + " · Precip. " + precip);
        details.setTextColor(ThemeUtils.secondaryText(this)); details.setTextSize(11);
        info.addView(details);
        String detailed = period.optString("detailedForecast", "");
        if (!detailed.isEmpty()) {
            TextView more = new TextView(this);
            more.setText(detailed);
            more.setTextColor(ThemeUtils.secondaryText(this)); more.setTextSize(11);
            more.setPadding(0, dp(4), 0, 0);
            info.addView(more);
        }
        card.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        forecastList.addView(card, rowMargins());
    }

    private void addSectionTitle(String title) {
        TextView text = new TextView(this);
        text.setText(title);
        text.setTextColor(ThemeUtils.accent(this)); text.setTextSize(13);
        text.setPadding(dp(4), dp(14), dp(4), dp(6));
        forecastList.addView(text);
    }

    private void addMessage(String message) {
        TextView text = new TextView(this);
        text.setText(message); text.setTextColor(ThemeUtils.text(this)); text.setTextSize(14);
        text.setPadding(dp(8), dp(18), dp(8), dp(18));
        forecastList.addView(text, new LinearLayout.LayoutParams(-1, -2));
    }

    private LinearLayout.LayoutParams rowMargins() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(8);
        return params;
    }

    private GradientDrawable cardBackground() {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(ThemeUtils.isDark(this) ? 0xff18232f : 0xfff5f8fc);
        shape.setCornerRadius(dp(10));
        shape.setStroke(dp(1), ThemeUtils.divider(this));
        return shape;
    }

    private String weatherEmoji(String forecast) {
        String value = forecast == null ? "" : forecast.toLowerCase(Locale.US);
        if (value.contains("thunder")) return "⛈️";
        if (value.contains("snow") || value.contains("sleet") || value.contains("ice")) return "🌨️";
        if (value.contains("rain") || value.contains("shower") || value.contains("drizzle")) return "🌧️";
        if (value.contains("fog") || value.contains("haze") || value.contains("smoke")) return "🌫️";
        if (value.contains("cloud") && (value.contains("partly") || value.contains("mostly"))) return "🌤️";
        if (value.contains("cloud") || value.contains("overcast")) return "☁️";
        if (value.contains("sun") || value.contains("clear")) return "☀️";
        if (value.contains("wind")) return "💨";
        return "🌡️";
    }

    private double[] geocode(String query) throws Exception {
        if (query.matches("-?\\d+(\\.\\d+)?,\\s*-?\\d+(\\.\\d+)?")) {
            String[] parts = query.split(",");
            return new double[]{Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim())};
        }
        if (query.matches("\\d{5}")) {
            JSONObject zip = getJson("https://api.zippopotam.us/us/" + query);
            JSONObject place = zip.getJSONArray("places").getJSONObject(0);
            return new double[]{Double.parseDouble(place.getString("latitude")), Double.parseDouble(place.getString("longitude"))};
        }
        try {
            String url = "https://geocoding.geo.census.gov/geocoder/locations/onelineaddress?address=" +
                URLEncoder.encode(query, "UTF-8") + "&benchmark=Public_AR_Current&format=json";
            JSONArray matches = getJson(url).getJSONObject("result").getJSONObject("addressMatches").getJSONArray("matches");
            if (matches.length() > 0) {
                JSONObject coords = matches.getJSONObject(0).getJSONObject("coordinates");
                return new double[]{coords.getDouble("y"), coords.getDouble("x")};
            }
        } catch (Exception ignored) {}
        String url = "https://nominatim.openstreetmap.org/search?q=" + URLEncoder.encode(query, "UTF-8") + "&format=jsonv2&limit=1&countrycodes=us";
        JSONArray matches = new JSONArray(getText(url));
        if (matches.length() == 0) throw new Exception("Location not found. Try a US ZIP code or city and state.");
        JSONObject match = matches.getJSONObject(0);
        return new double[]{Double.parseDouble(match.getString("lat")), Double.parseDouble(match.getString("lon"))};
    }

    private JSONObject getJson(String url) throws Exception { return new JSONObject(getText(url)); }

    private String getText(String url) throws Exception {
        URLConnection opened = new URL(url).openConnection();
        if (opened instanceof javax.net.ssl.HttpsURLConnection) LegacyTlsCompat.configure(this, (javax.net.ssl.HttpsURLConnection)opened);
        HttpURLConnection connection = (HttpURLConnection)opened;
        connection.setConnectTimeout(15000); connection.setReadTimeout(15000);
        connection.setRequestProperty("User-Agent", "YWLauncher/1.1 (Android weather client)");
        connection.setRequestProperty("Accept", "application/geo+json, application/json");
        try {
            int code = connection.getResponseCode();
            InputStream input = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (input == null) throw new IOException("HTTP " + code);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) > 0) output.write(buffer, 0, count);
            } finally { input.close(); }
            String body = new String(output.toByteArray(), "UTF-8");
            if (code >= 400) throw new IOException("Server returned HTTP " + code);
            return body;
        } finally { connection.disconnect(); }
    }

    private void showLocationError(String message) {
        forecastList.removeAllViews();
        addMessage(message);
        updated.setText("");
        refreshButton.setEnabled(true);
    }

    @Override protected void onDestroy() {
        if (locationTimeout != null) handler.removeCallbacks(locationTimeout);
        if (pendingLocationListener != null) {
            try { ((LocationManager)getSystemService(LOCATION_SERVICE)).removeUpdates(pendingLocationListener); }
            catch (SecurityException ignored) {}
            pendingLocationListener = null;
        }
        super.onDestroy();
    }
}
