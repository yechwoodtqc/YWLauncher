package com.lge.ywlauncher;

import android.app.PendingIntent;
import android.appwidget.*;
import android.content.*;
import android.widget.*;
import java.text.*;
import java.util.*;

public class DateWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        Date now=new Date();
        String english=new SimpleDateFormat("EEEE, MMM d, yyyy",Locale.US).format(now);
        String hebrew;
        try {
            Locale hebrewLocale=Locale.forLanguageTag("he-IL-u-ca-hebrew");
            Calendar calendar=Calendar.getInstance(hebrewLocale);
            calendar.setTime(now);
            hebrew=new SimpleDateFormat("d MMMM yyyy",hebrewLocale).format(now);
            if(hebrew==null || hebrew.trim().isEmpty()) hebrew="Hebrew date unavailable";
        } catch(Exception e) {
            hebrew="Hebrew date unavailable on this device";
        }
        for(int id:ids) {
            RemoteViews views=new RemoteViews(context.getPackageName(),R.layout.date_widget);
            views.setTextViewText(R.id.widgetText,english+"\n"+hebrew);
            Intent open=new Intent(context,MainActivity.class);
            PendingIntent pi=PendingIntent.getActivity(context,id,open,PendingIntent.FLAG_UPDATE_CURRENT|(android.os.Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
            views.setOnClickPendingIntent(R.id.widgetText,pi);
            manager.updateAppWidget(id,views);
        }
    }
}
