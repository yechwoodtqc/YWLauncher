package com.yechwoodtqc.ywlauncher;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    LinearLayout root, list; ArrayList<ResolveInfo> apps = new ArrayList<>();
    int selected = 0;
    @Override public void onCreate(Bundle b) { super.onCreate(b); build(); loadApps(); postLaunchNotification(); }
    void build() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(12,10,12,10); root.setBackgroundColor(Color.rgb(16,24,32));
        TextView title = new TextView(this); title.setText("YW LAUNCHER"); title.setTextSize(20); title.setTextColor(Color.WHITE); title.setPadding(8,8,8,12); root.addView(title);
        LinearLayout actions = new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        addAction(actions,"Weather",v->startActivity(new Intent(this,WeatherActivity.class)));
        addAction(actions,"Settings",v->startActivity(new Intent(this,SettingsActivity.class)));
        root.addView(actions);
        ScrollView scroll = new ScrollView(this); list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); scroll.setFillViewport(true); scroll.addView(list); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
    }
    void addAction(LinearLayout parent,String text, View.OnClickListener l) { Button b=new Button(this); b.setText(text); b.setTextColor(Color.WHITE); b.setOnClickListener(l); parent.addView(b,new LinearLayout.LayoutParams(0,48,1)); }
    void loadApps() {
        list.removeAllViews(); apps.clear();
        Intent query = new Intent(Intent.ACTION_MAIN); query.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> found = getPackageManager().queryIntentActivities(query,0);
        Collections.sort(found,(a,b)->String.CASE_INSENSITIVE_ORDER.compare(a.loadLabel(getPackageManager()).toString(),b.loadLabel(getPackageManager()).toString()));
        Set<String> chosen = getSharedPreferences("launcher",0).getStringSet("included",null);
        ArrayList<ResolveInfo> visible = new ArrayList<>();
        for (ResolveInfo ri:found) {
            String pkg=ri.activityInfo.packageName;
            if (pkg.equals(getPackageName())) { /* keep launcher and weather entry below */ }
            boolean system=(ri.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM)!=0;
            if (chosen==null && system && !pkg.equals(getPackageName())) continue;
            if (chosen!=null && !chosen.contains(pkg)) continue;
            visible.add(ri);
        }
        apps.addAll(visible);
        for (ResolveInfo ri:apps) {
            LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(8,3,8,3); row.setFocusable(true); row.setBackgroundResource(android.R.drawable.list_selector_background);
            ImageView icon=new ImageView(this); Drawable d=ri.loadIcon(getPackageManager()); icon.setImageDrawable(d); row.addView(icon,new LinearLayout.LayoutParams(48,48));
            TextView label=new TextView(this); label.setText(ri.loadLabel(getPackageManager())); label.setTextColor(Color.WHITE); label.setTextSize(16); label.setPadding(12,4,4,4); row.addView(label,new LinearLayout.LayoutParams(0,56,1));
            row.setOnClickListener(v->{try { Intent i=new Intent(Intent.ACTION_MAIN); i.addCategory(Intent.CATEGORY_LAUNCHER); i.setClassName(ri.activityInfo.packageName,ri.activityInfo.name); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); } catch(Exception e){Toast.makeText(this,"Unable to launch app",Toast.LENGTH_SHORT).show();}});
            row.setOnLongClickListener(v->{new AlertDialog.Builder(this).setTitle(ri.loadLabel(getPackageManager())).setItems(new String[]{"Launch","Uninstall","Hide from launcher"},(dialog,which)->{if(which==0)row.performClick();else if(which==1){Intent i=new Intent(Intent.ACTION_DELETE,android.net.Uri.parse("package:"+ri.activityInfo.packageName));startActivity(i);}else{Set<String>s=new HashSet<>(getSharedPreferences("launcher",0).getStringSet("included",new HashSet<String>()));s.remove(ri.activityInfo.packageName);getSharedPreferences("launcher",0).edit().putStringSet("included",s).apply();loadApps();}}).show();return true;});
            list.addView(row);
        }
        Button all = new Button(this); all.setText("Customize app list and order"); all.setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class))); list.addView(all);
        if(list.getChildCount()>0) list.getChildAt(0).requestFocus();
    }
    void postLaunchNotification() {
        if(!getSharedPreferences("launcher",0).getBoolean("notify",true)) return;
        if(android.os.Build.VERSION.SDK_INT>=26) { NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE); nm.createNotificationChannel(new NotificationChannel("yw_launcher","Launcher access",NotificationManager.IMPORTANCE_LOW)); }
        Intent open=new Intent(this,MainActivity.class); PendingIntent pi=PendingIntent.getActivity(this,1,open,PendingIntent.FLAG_UPDATE_CURRENT | (android.os.Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
        Notification.Builder nb=android.os.Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"yw_launcher"):new Notification.Builder(this);
        nb.setContentTitle("YW Launcher").setContentText("Press to open your app launcher").setContentIntent(pi).setAutoCancel(false).setOngoing(true);
        boolean status=getSharedPreferences("launcher",0).getBoolean("status_icon",false);
        nb.setSmallIcon(status?android.R.drawable.ic_menu_view:android.R.drawable.stat_notify_more);
        if(!status && android.os.Build.VERSION.SDK_INT>=21) nb.setVisibility(Notification.VISIBILITY_SECRET);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(42,nb.build());
    }
    @Override public boolean onKeyDown(int keyCode,KeyEvent event) {
        if(keyCode==KeyEvent.KEYCODE_MENU){startActivity(new Intent(this,SettingsActivity.class));return true;}
        if(keyCode==KeyEvent.KEYCODE_BACK){return super.onKeyDown(keyCode,event);}
        return super.onKeyDown(keyCode,event);
    }
    @Override protected void onResume(){super.onResume(); if(root!=null)loadApps();}
}
