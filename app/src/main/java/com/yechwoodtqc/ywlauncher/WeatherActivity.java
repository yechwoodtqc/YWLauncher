package com.yechwoodtqc.ywlauncher;

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
    EditText location; TextView result,updated; Button refresh; Handler handler=new Handler();
    @Override public void onCreate(Bundle b){super.onCreate(b);LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(12,12,12,12);root.setBackgroundColor(Color.rgb(16,24,32));setContentView(root);
        TextView title=new TextView(this);title.setText("YW WEATHER · weather.gov");title.setTextColor(-1);title.setTextSize(20);root.addView(title);
        location=new EditText(this);location.setSingleLine(true);location.setHint("ZIP code or city, state");location.setTextColor(-1);location.setHintTextColor(0xffbbbbbb);root.addView(location);
        Button detect=new Button(this);detect.setText("Use current location");detect.setOnClickListener(v->detectLocation());root.addView(detect);
        refresh=new Button(this);refresh.setText("↻  Refresh forecast");refresh.setOnClickListener(v->fetchForecast());root.addView(refresh);
        updated=new TextView(this);updated.setTextColor(0xffbbbbbb);root.addView(updated);
        ScrollView sc=new ScrollView(this);result=new TextView(this);result.setTextColor(-1);result.setTextSize(16);result.setPadding(4,12,4,12);sc.addView(result);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
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
        try {LocationManager lm=(LocationManager)getSystemService(LOCATION_SERVICE);Location loc=lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);if(loc==null)loc=lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);if(loc==null){Toast.makeText(this,"No recent location available. Enter a city or ZIP.",Toast.LENGTH_LONG).show();return;}
            new Thread(()->{try{Geocoder g=new Geocoder(this,Locale.US);List<Address>a=g.getFromLocation(loc.getLatitude(),loc.getLongitude(),1);if(a!=null&&!a.isEmpty()){String s=a.get(0).getLocality()+", "+a.get(0).getAdminArea();handler.post(()->location.setText(s));}}catch(Exception ignored){} }).start();
        }catch(SecurityException e){Toast.makeText(this,"Location permission is not granted; enter a location manually.",Toast.LENGTH_LONG).show();}
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
        // Accept "lat,lon" for power users, otherwise use the US Census geocoder for ZIP/address lookup.
        if(q.matches("-?\\d+(\\.\\d+)?,\\s*-?\\d+(\\.\\d+)?")){String[]p=q.split(",");return new double[]{Double.parseDouble(p[0]),Double.parseDouble(p[1])};}
        if(q.matches("\\d{5}")) {
            JSONObject zip=getJson("https://api.zippopotam.us/us/"+q);
            JSONArray places=zip.getJSONArray("places");
            if(places.length()==0) throw new Exception("ZIP code not found.");
            JSONObject place=places.getJSONObject(0);
            return new double[]{Double.parseDouble(place.getString("latitude")),Double.parseDouble(place.getString("longitude"))};
        }
        String url="https://geocoding.geo.census.gov/geocoder/locations/onelineaddress?address="+URLEncoder.encode(q,"UTF-8")+"&benchmark=Public_AR_Current&format=json";
        JSONObject j=getJson(url);JSONArray m=j.getJSONObject("result").getJSONObject("addressMatches").getJSONArray("matches");if(m.length()==0)throw new Exception("Location not found. Try a ZIP code or full street address.");JSONObject c=m.getJSONObject(0).getJSONObject("coordinates");return new double[]{c.getDouble("y"),c.getDouble("x")};
    }
    JSONObject getJson(String s)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(s).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(12000);c.setRequestProperty("User-Agent","YWLauncher/1.0 (Android weather client)");c.setRequestProperty("Accept","application/geo+json, application/json");try{int code=c.getResponseCode();InputStream in=code>=400?c.getErrorStream():c.getInputStream();if(in==null)throw new IOException("HTTP "+code);ByteArrayOutputStream b=new ByteArrayOutputStream();byte[]buf=new byte[4096];int n;while((n=in.read(buf))>0)b.write(buf,0,n);String body=new String(b.toByteArray(),"UTF-8");if(code>=400)throw new IOException("Server returned HTTP "+code);return new JSONObject(body);}finally{c.disconnect();}}
}
