package com.yechwoodtqc.ywlauncher;
import android.appwidget.*;import android.content.*;import android.widget.*;import java.text.*;import java.util.*;
public class DateWidgetProvider extends AppWidgetProvider {
 @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){Calendar now=Calendar.getInstance();String english=new SimpleDateFormat("EEEE, MMM d, yyyy",Locale.US).format(now.getTime());String hebrew;
  try{Calendar h=Calendar.getInstance(new Locale("he","IL","Hebrew"));h.setTime(now.getTime());hebrew=new SimpleDateFormat("d MMMM yyyy",new Locale("he","IL")).format(h.getTime());}catch(Exception e){hebrew="Hebrew date requires device calendar support";}
  for(int id:ids){RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.date_widget);v.setTextViewText(R.id.widgetText,english+"\n"+hebrew);Intent i=new Intent(c,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(c,id,i,PendingIntent.FLAG_UPDATE_CURRENT|(android.os.Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));v.setOnClickPendingIntent(R.id.widgetText,pi);m.updateAppWidget(id,v);}
 }
}
