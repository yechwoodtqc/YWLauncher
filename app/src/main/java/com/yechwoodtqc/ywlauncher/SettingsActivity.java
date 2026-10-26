package com.yechwoodtqc.ywlauncher;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

public class SettingsActivity extends Activity {
    LinearLayout box;
    SharedPreferences prefs;
    static final int CREATE_LOG_FILE = 501;
    String pendingLogcat;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        ThemeUtils.apply(this);
        prefs=getSharedPreferences("launcher",0);
        box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(12,12,12,12);
        box.setBackgroundColor(ThemeUtils.surface(this)); setContentView(box);
        TextView h=new TextView(this); h.setText("LAUNCHER SETTINGS"); h.setTextColor(ThemeUtils.text(this)); h.setTextSize(20); h.setPadding(4,4,4,12); box.addView(h);
        check("Show launch notification", "notify", true, this::refreshNotification);
        check("Show notification status icon", "status_icon", false, this::refreshNotification);
        item("Theme: System / Light / Dark", () -> {
            String mode=prefs.getString("theme_mode","system");
            int selected="dark".equals(mode)?2:("light".equals(mode)?1:0);
            new AlertDialog.Builder(this).setTitle("Theme")
                .setSingleChoiceItems(new String[]{"System","Light","Dark"},selected,(d,w)->{
                    String[] modes={"system","light","dark"};
                    prefs.edit().putString("theme_mode",modes[w]).apply();
                    d.dismiss();
                    recreate();
                }).setNegativeButton("Cancel",null).show();
        });
        item("Accent color", () -> {
            String[] names={"Blue","Deep Purple","Purple","Teal","Green","Red","Orange","Yellow","Grey","Retro"};
            int[] colors={Color.rgb(26,115,232),Color.rgb(94,53,177),Color.rgb(123,31,162),Color.rgb(0,150,136),Color.rgb(56,142,60),Color.rgb(211,47,47),Color.rgb(245,124,0),Color.rgb(251,192,45),Color.rgb(117,117,117),Color.rgb(0,230,118)};
            int current=prefs.getInt("accent_color",Color.rgb(26,115,232)); int selected=0;
            for(int i=0;i<colors.length;i++) if(colors[i]==current) selected=i;
            new AlertDialog.Builder(this).setTitle("Accent color").setSingleChoiceItems(names,selected,(d,w)->{
                prefs.edit().putInt("accent_color",colors[w]).apply(); d.dismiss(); recreate();
            }).setNegativeButton("Cancel",null).show();
        });
        item("Choose apps", this::chooseApps);
        item("Reorder apps", this::reorderApps);
        item("Layout size", () -> new AlertDialog.Builder(this).setTitle("App tile size")
            .setSingleChoiceItems(new String[]{"Compact","Comfortable","Large"}, currentLayout(), (d,w)->{
                prefs.edit().putInt("row_height",new int[]{48,64,80}[w]).apply(); d.dismiss();
            }).setNegativeButton("Cancel",null).show());
        item("Background", () -> new AlertDialog.Builder(this).setTitle("Background")
            .setSingleChoiceItems(new String[]{"System wallpaper","Blue","Black"}, prefs.getInt("background",0), (d,w)->{
                prefs.edit().putInt("background",w).apply(); d.dismiss();
            }).setNegativeButton("Cancel",null).show());
        item("Date widget", () -> new AlertDialog.Builder(this)
            .setMessage("To add the date widget, open the phone's home-screen widget picker and choose “English and Hebrew Date”. Widget support depends on the installed home app.")
            .setPositiveButton("OK",null).show());
        item("Export logcat text file", this::exportLogcat);
        item("Open Weather", () -> startActivity(new Intent(this,WeatherActivity.class)));
    }

    int currentLayout() {
        int v=prefs.getInt("row_height",64);
        return v<=48?0:(v>=80?2:1);
    }
    void refreshNotification() {
        if(prefs.getBoolean("notify",true)) {
            Intent i=new Intent(this,MainActivity.class);
            PendingIntent pi=PendingIntent.getActivity(this,1,i,PendingIntent.FLAG_UPDATE_CURRENT|(android.os.Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
            if(android.os.Build.VERSION.SDK_INT>=26) {
                NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
                nm.createNotificationChannel(new NotificationChannel("yw_launcher","Launcher access",NotificationManager.IMPORTANCE_LOW));
            }
            Notification.Builder n=android.os.Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"yw_launcher"):new Notification.Builder(this);
            n.setSmallIcon(prefs.getBoolean("status_icon",false)?android.R.drawable.ic_menu_view:R.drawable.ic_notification_hidden)
             .setContentTitle("YW Launcher").setContentText("Press to open your app launcher").setContentIntent(pi).setOngoing(true);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(42,n.build());
        } else ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancel(42);
    }
    void check(String label,String key,boolean def,Runnable changed) {
        CheckBox c=new CheckBox(this); c.setText(label); c.setTextColor(ThemeUtils.text(this)); c.setButtonTintList(android.content.res.ColorStateList.valueOf(ThemeUtils.accent(this))); c.setChecked(prefs.getBoolean(key,def));
        c.setOnCheckedChangeListener((v,checked)->{prefs.edit().putBoolean(key,checked).apply();changed.run();}); box.addView(c);
    }
    void item(String label,Runnable run) { Button b=new Button(this); b.setText(label); ThemeUtils.tint(b,this); b.setOnClickListener(v->run.run()); box.addView(b); }

    ArrayList<ResolveInfo> queryApps() {
        Intent q=new Intent(Intent.ACTION_MAIN); q.addCategory(Intent.CATEGORY_LAUNCHER);
        ArrayList<ResolveInfo> apps=new ArrayList<>(getPackageManager().queryIntentActivities(q,0));
        Collections.sort(apps,(a,b)->String.CASE_INSENSITIVE_ORDER.compare(a.loadLabel(getPackageManager()).toString(),b.loadLabel(getPackageManager()).toString()));
        return apps;
    }
    void chooseApps() {
        ArrayList<ResolveInfo> apps=queryApps();
        Set<String> old=prefs.getStringSet("included",null);
        boolean[] checks=new boolean[apps.size()]; String[] labels=new String[apps.size()];
        for(int i=0;i<apps.size();i++) {
            ResolveInfo a=apps.get(i); labels[i]=a.loadLabel(getPackageManager()).toString();
            boolean sys=(a.activityInfo.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)!=0;
            checks[i]=old==null?!sys:(old.contains(a.activityInfo.name)||old.contains(a.activityInfo.packageName));
        }
        new AlertDialog.Builder(this).setTitle("Apps to show")
            .setMultiChoiceItems(labels,checks,(d,which,on)->checks[which]=on)
            .setPositiveButton("Save",(d,w)->{
                LinkedHashSet<String> included=new LinkedHashSet<>();
                for(int i=0;i<apps.size();i++) if(checks[i]) included.add(apps.get(i).activityInfo.name);
                String previous=prefs.getString("ordered_apps","");
                Set<String> oldOrder=new LinkedHashSet<>(Arrays.asList(previous.split("\\n")));
                ArrayList<String> ordered=new ArrayList<>();
                for(ResolveInfo a:apps) if((included.contains(a.activityInfo.name)||included.contains(a.activityInfo.packageName))&&oldOrder.contains(a.activityInfo.name)&&!ordered.contains(a.activityInfo.name)) ordered.add(a.activityInfo.name);
                for(ResolveInfo a:apps) if((included.contains(a.activityInfo.name)||included.contains(a.activityInfo.packageName))&&!ordered.contains(a.activityInfo.name)) ordered.add(a.activityInfo.name);
                prefs.edit().putStringSet("included",included).putString("ordered_apps",joinLines(ordered)).apply();
                Toast.makeText(this,"App list saved",Toast.LENGTH_SHORT).show();
            }).setNegativeButton("Cancel",null).show();
    }

    void reorderApps() {
        ArrayList<ResolveInfo> all=queryApps();
        Set<String> included=prefs.getStringSet("included",null);
        ArrayList<ResolveInfo> chosen=new ArrayList<>();
        for(ResolveInfo a:all) {
            boolean system=(a.activityInfo.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)!=0;
            if(included==null ? !system : (included.contains(a.activityInfo.name)||included.contains(a.activityInfo.packageName))) chosen.add(a);
        }
        final Map<String,ResolveInfo> byPackage=new HashMap<>();
        for(ResolveInfo a:chosen) byPackage.put(a.activityInfo.name,a);
        ArrayList<String> order=new ArrayList<>();
        String raw=prefs.getString("ordered_apps","");
        if(raw!=null) for(String p:raw.split("\\n")) if(byPackage.containsKey(p)&&!order.contains(p)) order.add(p);
        for(ResolveInfo a:chosen) if(!order.contains(a.activityInfo.name)) order.add(a.activityInfo.name);
        if(order.isEmpty()) { new AlertDialog.Builder(this).setMessage("No apps are selected. Use Choose apps first.").setPositiveButton("OK",null).show(); return; }

        LinearLayout layout=new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(12,8,12,8);
        TextView help=new TextView(this); help.setText("Select an app, then move it up or down."); layout.addView(help);
        ListView list=new ListView(this); ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_activated_1);
        for(String p:order) adapter.add(labelFor(byPackage.get(p)));
        list.setAdapter(adapter); list.setChoiceMode(ListView.CHOICE_MODE_SINGLE); list.setItemChecked(0,true); layout.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout controls=new LinearLayout(this);
        Button up=new Button(this); up.setText("Move up"); ThemeUtils.tint(up,this); Button down=new Button(this); down.setText("Move down"); ThemeUtils.tint(down,this);
        controls.addView(up,new LinearLayout.LayoutParams(0,-2,1)); controls.addView(down,new LinearLayout.LayoutParams(0,-2,1)); layout.addView(controls);
        final int[] selected={0};
        list.setOnItemClickListener((parent,view,position,id)->selected[0]=position);
        up.setOnClickListener(v->{int i=selected[0];if(i>0){Collections.swap(order,i,i-1);selected[0]=i-1;refreshOrderAdapter(adapter,order,byPackage);list.setItemChecked(selected[0],true);}});
        down.setOnClickListener(v->{int i=selected[0];if(i<order.size()-1){Collections.swap(order,i,i+1);selected[0]=i+1;refreshOrderAdapter(adapter,order,byPackage);list.setItemChecked(selected[0],true);}});
        new AlertDialog.Builder(this).setTitle("App order").setView(layout)
            .setPositiveButton("Save",(d,w)->prefs.edit().putString("ordered_apps",joinLines(order)).apply())
            .setNegativeButton("Cancel",null).show();
    }
    String labelFor(ResolveInfo r) { return r==null?"Unknown app":r.loadLabel(getPackageManager()).toString(); }
    void refreshOrderAdapter(ArrayAdapter<String> adapter,ArrayList<String> order,Map<String,ResolveInfo> map) {
        adapter.clear(); for(String p:order) adapter.add(labelFor(map.get(p))); adapter.notifyDataSetChanged();
    }
    String joinLines(List<String> values) { StringBuilder b=new StringBuilder(); for(String s:values){if(b.length()>0)b.append('\n');b.append(s);}return b.toString(); }

    void exportLogcat() {
        try {
            Process p=Runtime.getRuntime().exec(new String[]{"logcat","-d","-t","3000"});
            BufferedReader br=new BufferedReader(new InputStreamReader(p.getInputStream())); StringBuilder sb=new StringBuilder(); String line;
            while((line=br.readLine())!=null) sb.append(line).append('\n');
            br.close(); pendingLogcat=sb.toString();
            Intent create=new Intent(Intent.ACTION_CREATE_DOCUMENT); create.addCategory(Intent.CATEGORY_OPENABLE);
            create.setType("text/plain"); create.putExtra(Intent.EXTRA_TITLE,"ywlauncher-logcat.txt");
            startActivityForResult(create,CREATE_LOG_FILE);
        } catch(Exception e) { showError("Could not collect logcat: "+e.getMessage()); }
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==CREATE_LOG_FILE && resultCode==RESULT_OK && data!=null && data.getData()!=null) {
            try {
                Uri uri=data.getData(); OutputStream out=getContentResolver().openOutputStream(uri);
                if(out==null) throw new IOException("Could not open selected file");
                out.write((pendingLogcat==null?"":pendingLogcat).getBytes("UTF-8")); out.close();
                Toast.makeText(this,"Logcat file saved",Toast.LENGTH_LONG).show();
            } catch(Exception e) { showError("Could not save logcat: "+e.getMessage()); }
        }
        pendingLogcat=null;
    }
    void showError(String message) { new AlertDialog.Builder(this).setMessage(message).setPositiveButton("OK",null).show(); }
}
