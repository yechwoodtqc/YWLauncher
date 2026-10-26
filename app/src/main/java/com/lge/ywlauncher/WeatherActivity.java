package com.lge.ywlauncher;

import android.Manifest;
import android.app.*;
import android.content.pm.PackageManager;
import android.content.*;
import android.location.*;
import android.os.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.net.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class WeatherActivity extends Activity {
    EditText location; TextView result,updated; Button refresh; Handler handler=new Handler(); LocationListener pendingLocationListener; Runnable locationTimeout;
    @Override public void onCreate(Bundle b){super.onCreate(b);ThemeUtils.apply(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(12,12,12,12);root.setBackgroundColor(ThemeUtils.surface(this));setContentView(root);
        TextView title=new TextView(this);title.setText("YW WEATHER · weather.gov");title.setTextColor(ThemeUtils.text(this));title.setTextSize(20);root.addView(title);
        location=new EditText(this);location.setSingleLine(true);location.setHint("ZIP code or city, state");location.setTextColor(ThemeUtils.text(this));location.setHintTextColor(ThemeUtils.isDark(this)?0xffbbbbbb:0xff666666);root.addView(location);
        Button detect=new Button(this);detect.setText("Use current location");ThemeUtils.tint(detect,this);detect.setOnClickListener(v->detectLocation());root.addView(detect);
        refresh=new Button(this);refresh.setText("↻  Refresh forecast");ThemeUtils.tint(refresh,this);refresh.setOnClickListener(v->fetchForecast());root.addView(refresh);
        updated=new TextView(this);updated.setTextColor(ThemeUtils.isDark(this)?0xffbbbbbb:0xff555555);root.addView(updated);
        ScrollView sc=new ScrollView(this);result=new TextView(this);result.setTextColor(ThemeUtils.text(this));result.setTextSize(16);result.setPadding(4,12,4,12);sc.addView(result);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        location.setText(getPreferences(0).getString("location","Pomona, NY"));result.setText("Enter a location, then press Refresh.");}
    static final int LOCATION_PERMISSION_REQUEST=71;
    void detectLocation(){
        if(android.os.Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},LOCATION_PERMISSION_REQUEST); return;
        }
        findLastLocation();
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==LOCATION_PERMISSION_REQUEST){boolean granted=false;for(int g:grantResults)if(g==PackageManager.PERMISSION_GRANTED)granted=true;if(granted)findLastLocation();else Toast.makeText(this,"Location permission denied; enter a location manually.",Toast.LENGTH_LONG).show();}
    }
    void findLastLocation(){
        try {
            LocationManager lm=(LocationManager)getSystemService(LOCATION_SERVICE);
            Location best=null;
            for(String provider:lm.getProviders(true)) {
                try {
                    Location candidate=lm.getLastKnownLocation(provider);
                    if(candidate!=null && (best==null || candidate.getTime()>best.getTime())) best=candidate;
                } catch(IllegalArgumentException | SecurityException ignored) {}
            }
            if(best!=null) { resolveLocation(best); return; }
            List<String> providers=lm.getProviders(true);
            if(providers.isEmpty()) {
                Toast.makeText(this,"No location provider is enabled. Enter a city or ZIP.",Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(this,"Finding current location…",Toast.LENGTH_SHORT).show();
            if(pendingLocationListener!=null) lm.removeUpdates(pendingLocationListener);
            pendingLocationListener=new LocationListener() {
                @Override public void onLocationChanged(Location location) {
                    if(pendingLocationListener==null) return;
                    lm.removeUpdates(this); pendingLocationListener=null;
                    if(locationTimeout!=null) handler.removeCallbacks(locationTimeout);
                    resolveLocation(location);
                }
                @Override public void onStatusChanged(String provider,int status,Bundle extras) {}
                @Override public void onProviderEnabled(String provider) {}
                @Override public void onProviderDisabled(String provider) {}
            };
            for(String provider:providers) {
                try { lm.requestLocationUpdates(provider,0,0,pendingLocationListener); }
                catch(IllegalArgumentException | SecurityException ignored) {}
            }
            locationTimeout=()->{
                if(pendingLocationListener!=null) {
                    try { lm.removeUpdates(pendingLocationListener); } catch(SecurityException ignored) {}
                    pendingLocationListener=null;
                    Toast.makeText(this,"Could not get a location fix. Enter a city or ZIP.",Toast.LENGTH_LONG).show();
                }
            };
            handler.postDelayed(locationTimeout,20000);
        } catch(SecurityException e) {
            Toast.makeText(this,"Location permission is not granted; enter a location manually.",Toast.LENGTH_LONG).show();
        }
    }
    void resolveLocation(Location loc) {
        new Thread(()->{
            try {
                Geocoder g=new Geocoder(this,Locale.US);
                List<Address> addresses=g.getFromLocation(loc.getLatitude(),loc.getLongitude(),1);
                if(addresses!=null&&!addresses.isEmpty()) {
                    Address a=addresses.get(0);
                    String city=a.getLocality();
                    if(city==null || city.trim().isEmpty()) city=a.getSubAdminArea();
                    String state=a.getAdminArea();
                    final String place=(city==null?"":city)+(state==null||state.isEmpty()?"":", "+state);
                    if(!place.trim().isEmpty()) handler.post(()->location.setText(place));
                    else handler.post(()->Toast.makeText(this,"Location found, but could not resolve its city name.",Toast.LENGTH_LONG).show());
                } else handler.post(()->Toast.makeText(this,"Location found, but no city name was returned.",Toast.LENGTH_LONG).show());
            } catch(Exception e) {
                handler.post(()->Toast.makeText(this,"Could not resolve location name. Enter a city or ZIP.",Toast.LENGTH_LONG).show());
            }
        }).start();
    }
    void fetchForecast(){
        String place=location.getText().toString().trim();if(place.isEmpty()){Toast.makeText(this,"Enter a location first",Toast.LENGTH_SHORT).show();return;}
        getPreferences(0).edit().putString("location",place).apply();refresh.setEnabled(false);result.setText("Loading forecast…");
        new Thread(()->{try{
            double[] ll=geocode(place);if(ll[0]<-90||ll[0]>90||ll[1]<-180||ll[1]>180)throw new Exception("Invalid latitude/longitude.");String point="https://api.weather.gov/points/"+ll[0]+","+ll[1];JSONObject p=getJson(point);String forecast=p.getJSONObject("properties").getString("forecast");
            JSONObject f=getJson(forecast);JSONArray periods=f.getJSONObject("properties").getJSONArray("periods");StringBuilder sb=new StringBuilder();
            for(int i=0;i<Math.min(8,periods.length());i++){JSONObject x=periods.getJSONObject(i);sb.append(x.getString("name")).append("\n").append(x.optString("temperature","?")).append("° ").append(x.optString("temperatureUnit","F")).append(" · ").append(x.optString("shortForecast","")).append("\n").append("Wind: ").append(x.optString("windSpeed","")).append(" ").append(x.optString("windDirection","")).append("\n\n");}
            String output=sb.toString();handler.post(()->{result.setText(output);updated.setText("Last updated: "+new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US).format(new Date()));refresh.setEnabled(true);});
        }catch(Exception e){handler.post(()->{result.setText("Could not load weather.gov forecast. Check your connection and location format.\n\n"+e.getMessage());refresh.setEnabled(true);});}}).start();
    }
    double[] geocode(String q)throws Exception{
        // Accept explicit coordinates and ZIPs, then try address and place-name geocoders.
        if(q.matches("-?\\d+(\\.\\d+)?,\\s*-?\\d+(\\.\\d+)?")){
            String[] p=q.split(",");
            double lat=Double.parseDouble(p[0].trim()), lon=Double.parseDouble(p[1].trim());
            if(lat < -90 || lat > 90 || lon < -180 || lon > 180) throw new Exception("Coordinates are outside the valid range.");
            return new double[]{lat,lon};
        }
        if(q.matches("\\d{5}")) {
            JSONObject zip=getJson("https://api.zippopotam.us/us/"+q);
            JSONArray places=zip.getJSONArray("places");
            if(places.length()==0) throw new Exception("ZIP code not found.");
            JSONObject place=places.getJSONObject(0);
            return new double[]{Double.parseDouble(place.getString("latitude")),Double.parseDouble(place.getString("longitude"))};
        }

        // The Census geocoder is good for street addresses, but often cannot resolve city/state alone.
        try {
            String url="https://geocoding.geo.census.gov/geocoder/locations/onelineaddress?address="+URLEncoder.encode(q,"UTF-8")+"&benchmark=Public_AR_Current&format=json";
            JSONObject j=getJson(url);
            JSONArray matches=j.getJSONObject("result").getJSONObject("addressMatches").getJSONArray("matches");
            if(matches.length()>0) {
                JSONObject coords=matches.getJSONObject(0).getJSONObject("coordinates");
                return new double[]{coords.getDouble("y"),coords.getDouble("x")};
            }
        } catch(Exception ignored) {
            // Fall through to place-name lookup below.
        }

        String placeUrl="https://nominatim.openstreetmap.org/search?q="+URLEncoder.encode(q,"UTF-8")+"&format=jsonv2&limit=1&countrycodes=us";
        JSONArray places=new JSONArray(getText(placeUrl));
        if(places.length()==0) throw new Exception("Location not found. Try a US ZIP code, city and state, or full address.");
        JSONObject match=places.getJSONObject(0);
        double lat=Double.parseDouble(match.getString("lat"));
        double lon=Double.parseDouble(match.getString("lon"));
        if(lat < -90 || lat > 90 || lon < -180 || lon > 180) throw new Exception("The location service returned invalid coordinates.");
        return new double[]{lat,lon};
    }

    JSONObject getJson(String url)throws Exception {
        return new JSONObject(getText(url));
    }

    String getText(String url)throws Exception {
        java.net.URLConnection opened = new URL(url).openConnection();
        if (opened instanceof javax.net.ssl.HttpsURLConnection) {
            LegacyTlsCompat.configure(this, (javax.net.ssl.HttpsURLConnection) opened);
        }
        HttpURLConnection connection = (HttpURLConnection) opened;
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(12000);
        connection.setRequestProperty("User-Agent", "YWLauncher/1.0 (Android weather client)");
        connection.setRequestProperty("Accept", "application/geo+json, application/json");
        try {
            int code=connection.getResponseCode();
            InputStream input=code>=400?connection.getErrorStream():connection.getInputStream();
            if(input==null) throw new IOException("HTTP "+code);
            ByteArrayOutputStream output=new ByteArrayOutputStream();
            try {
                byte[] buffer=new byte[4096];
                int count;
                while((count=input.read(buffer))>0) output.write(buffer,0,count);
            } finally {
                input.close();
            }
            String body=new String(output.toByteArray(),"UTF-8");
            if(code>=400) throw new IOException("Server returned HTTP "+code);
            return body;
        } finally {
            connection.disconnect();
        }
    }

    @Override protected void onDestroy() {
        if(locationTimeout!=null) handler.removeCallbacks(locationTimeout);
        if(pendingLocationListener!=null) {
            try {
                LocationManager manager=(LocationManager)getSystemService(LOCATION_SERVICE);
                manager.removeUpdates(pendingLocationListener);
            } catch(SecurityException ignored) {
            }
            pendingLocationListener=null;
        }
        super.onDestroy();
    }
}
