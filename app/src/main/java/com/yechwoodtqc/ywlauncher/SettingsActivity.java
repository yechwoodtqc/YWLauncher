package com.yechwoodtqc.ywlauncher;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;

public class SettingsActivity extends Activity {
    LinearLayout box; SharedPreferences prefs;
    @Override public void onCreate(Bundle b){super.onCreate(b);prefs=getSharedPreferences("launcher",0);box=new LinearLayout(this);box.setOrientation(1);box.setPadding(12,12,12,12);box.setBackgroundColor(Color.rgb(16,24,32));setContentView(box);TextView h=new TextView(this);h.setText("LAUNCHER SETTINGS");h.setTextColor(-1);h.setTextSize(20);box.addView(h);
        check("Show launch notification", "notify",true,()->{});check("Show notification status icon","status_icon",false,()->{});
        item("Choose apps and ordering",()->chooseApps());
        item("Layout: Compact list / large list",()->new AlertDialog.Builder(this).setItems(new String[]{"Compact list","Large list"},(d,w)->prefs.edit().putInt("row_height",w==0?48:64).apply()).show());
        item("Background",()->new AlertDialog.Builder(this).setItems(new String[]{"System default dark","Blue","Black"},(d,w)->prefs.edit().putInt("background",w).apply()).show());
        item("Add date widget",()->new AlertDialog.Builder(this).setMessage("Add the English/Hebrew date widget from your phone's home-screen widget picker. Widget support depends on the installed home app.").setPositiveButton("OK",null).show());
        item("Export logcat to Downloads",()->exportLogcat());
        item("Open Weather",()->startActivity(new Intent(this,WeatherActivity.class)));
    }
    void check(String label,String key,boolean def,Runnable r){CheckBox c=new CheckBox(this);c.setText(label);c.setTextColor(-1);c.setChecked(prefs.getBoolean(key,def));c.setOnCheckedChangeListener((v,checked)->{prefs.edit().putBoolean(key,checked).apply();r.run();});box.addView(c);}
    void item(String label,Runnable run){Button b=new Button(this);b.setText(label);b.setOnClickListener(v->run.run());box.addView(b);}
    void chooseApps(){
        Intent q=new Intent(Intent.ACTION_MAIN);q.addCategory(Intent.CATEGORY_LAUNCHER);List<ResolveInfo> apps=getPackageManager().queryIntentActivities(q,0);
        Collections.sort(apps,(a,b)->String.CASE_INSENSITIVE_ORDER.compare(a.loadLabel(getPackageManager()).toString(),b.loadLabel(getPackageManager()).toString()));
        Set<String> old=prefs.getStringSet("included",null); boolean[] checks=new boolean[apps.size()];String[] labels=new String[apps.size()];
        for(int i=0;i<apps.size();i++){ResolveInfo a=apps.get(i);labels[i]=a.loadLabel(getPackageManager()).toString();boolean sys=(a.activityInfo.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)!=0;checks[i]=old==null?!sys:old.contains(a.activityInfo.packageName);}
        new AlertDialog.Builder(this).setTitle("Apps to show").setMultiChoiceItems(labels,checks,(d,which,on)->checks[which]=on).setPositiveButton("Save",(d,w)->{Set<String>s=new LinkedHashSet<>();for(int i=0;i<apps.size();i++)if(checks[i])s.add(apps.get(i).activityInfo.packageName);prefs.edit().putStringSet("included",s).apply();new AlertDialog.Builder(this).setMessage("Apps saved. Display order is alphabetical; custom manual ordering is planned for a later iteration.").setPositiveButton("OK",null).show();}).setNegativeButton("Cancel",null).show();
    }
    void exportLogcat(){
        try {
            if(android.os.Build.VERSION.SDK_INT>=23 && checkSelfPermission("android.permission.READ_LOGS")!=PackageManager.PERMISSION_GRANTED){ }
            Process p=Runtime.getRuntime().exec(new String[]{"logcat","-d","-t","2000"});java.io.BufferedReader br=new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));StringBuilder sb=new StringBuilder();String line;while((line=br.readLine())!=null)sb.append(line).append('\n');
            java.io.File dir=getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS);if(dir==null)dir=getFilesDir();dir.mkdirs();java.io.File f=new java.io.File(dir,"ywlauncher-logcat.txt");java.io.FileOutputStream out=new java.io.FileOutputStream(f);out.write(sb.toString().getBytes("UTF-8"));out.close();
            new AlertDialog.Builder(this).setMessage("Logcat saved to app Documents storage:\n"+f.getAbsolutePath()).setPositiveButton("OK",null).show();
        }catch(Exception e){new AlertDialog.Builder(this).setMessage("Could not export logcat: "+e.getMessage()).setPositiveButton("OK",null).show();}
    }
}
