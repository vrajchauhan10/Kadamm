package com.stride.run;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;

import java.util.Calendar;
import java.util.Locale;

/**
 * Two gentle nudges, checked every 15 minutes by {@link StepSyncJob}: no steps for an hour during the day
 * (at most every two hours), and an evening "almost there" when 2,000 steps or fewer are left (once a day).
 */
final class Reminders {
    private static final String CHANNEL="kadamm_reminders";
    private Reminders() {}

    static boolean inactiveOn(Context c){return c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE).getBoolean("remindInactive",true);}
    static boolean eveningOn(Context c){return c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE).getBoolean("remindEvening",true);}

    static void check(Context c){
        if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        if(c.getSharedPreferences("stride_state",Context.MODE_PRIVATE).getBoolean("active",false))return;
        SharedPreferences steps=c.getSharedPreferences(DaySteps.PREFS,Context.MODE_PRIVATE);
        long now=System.currentTimeMillis();int hour=Calendar.getInstance().get(Calendar.HOUR_OF_DAY);String today=DaySteps.todayKey();
        int goal=c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE).getInt("stepGoal",10000);long total=DaySteps.todayTotal(c);
        if(eveningOn(c)&&hour>=19&&hour<22&&total<goal&&goal-total<=2000&&!today.equals(steps.getString("eveningNudge",""))){
            steps.edit().putString("eveningNudge",today).apply();long left=goal-total;
            post(c,31,"Almost there",String.format(Locale.US,"Only %,d steps to your goal. A %d-minute walk will do it.",left,Math.max(5,Math.round(left/100.0))));
            return;
        }
        long lastMove=steps.getLong("lastMoveAt",0);
        if(inactiveOn(c)&&hour>=8&&hour<21&&lastMove>0&&now-lastMove>=3600000L&&now-steps.getLong("inactiveNudgeAt",0)>=2*3600000L&&total<goal){
            steps.edit().putLong("inactiveNudgeAt",now).apply();
            post(c,32,"Time to move","You've been still for an hour. A 5-minute walk adds about 500 steps.");
        }
    }
    private static void post(Context c,int id,String title,String text){
        NotificationManager nm=c.getSystemService(NotificationManager.class);if(nm==null)return;
        if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Reminders",NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent open=PendingIntent.getActivity(c,6,new Intent(c,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,CHANNEL):new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_stat_kadamm).setColor(Color.rgb(26,115,232)).setContentTitle(title).setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text)).setContentIntent(open).setAutoCancel(true);
        nm.notify(id,b.build());
    }
}
