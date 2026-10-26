package com.yechwoodtqc.ywlauncher;
import android.content.*;import android.app.*;import android.os.Build;
public class BootReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context c,Intent i){if(!Intent.ACTION_BOOT_COMPLETED.equals(i.getAction()))return;android.content.SharedPreferences p=c.getSharedPreferences("launcher",0);if(!p.getBoolean("notify",true))return;
  if(Build.VERSION.SDK_INT>=26)((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel("yw_launcher","Launcher access",NotificationManager.IMPORTANCE_LOW));
  Intent open=new Intent(c,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(c,1,open,PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
  Notification.Builder n=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,"yw_launcher"):new Notification.Builder(c);
  boolean icon=p.getBoolean("status_icon",false);n.setSmallIcon(icon?android.R.drawable.ic_menu_view:android.R.drawable.stat_notify_more).setContentTitle("YW Launcher").setContentText("Press to open your app launcher").setContentIntent(pi).setOngoing(true);
  ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(42,n.build());
 }
}
