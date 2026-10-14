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
 * Two gentle nudges, checked every 15 minutes by {@link StepSyncJob}: no steps for a while during active hours,
 * and an evening "almost there" when you're close to your goal (once a day). Times are configurable; the
 * defaults (8 am to 9 pm, after 60 minutes; 7 pm, 2,000 steps or fewer) are the automatic setting.
 */
final class Reminders {
    private static final String CHANNEL="kadamm_reminders";
    private Reminders() {}

    static boolean inactiveOn(Context c){return c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE).getBoolean("remindInactive",true);}
    static final int DEF_START=8*60,DEF_END=21*60,DEF_AFTER=60,DEF_EVENING=19*60,DEF_LEFT=2000;
    static int get(Context c,String key,int def){return c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE).getInt(key,def);}
    static boolean automatic(Context c){return get(c,"remStart",DEF_START)==DEF_START&&get(c,"remEnd",DEF_END)==DEF_END&&get(c,"remAfter",DEF_AFTER)==DEF_AFTER&&get(c,"remEvening",DEF_EVENING)==DEF_EVENING&&get(c,"remLeft",DEF_LEFT)==DEF_LEFT;}
    static void resetToAutomatic(Context c){c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE).edit().remove("remStart").remove("remEnd").remove("remAfter").remove("remEvening").remove("remLeft").apply();}
    static boolean eveningOn(Context c){return c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE).getBoolean("remindEvening",true);}

    static void check(Context c){
        if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        if(c.getSharedPreferences("stride_state",Context.MODE_PRIVATE).getBoolean("active",false))return;
        SharedPreferences steps=c.getSharedPreferences(DaySteps.PREFS,Context.MODE_PRIVATE);
        long now=System.currentTimeMillis();Calendar cal=Calendar.getInstance();int minute=cal.get(Calendar.HOUR_OF_DAY)*60+cal.get(Calendar.MINUTE);String today=DaySteps.todayKey();
        int start=get(c,"remStart",DEF_START),end=get(c,"remEnd",DEF_END),after=get(c,"remAfter",DEF_AFTER),evening=get(c,"remEvening",DEF_EVENING),leftMax=get(c,"remLeft",DEF_LEFT);
        int goal=c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE).getInt("stepGoal",10000);long total=DaySteps.todayTotal(c);
        if(eveningOn(c)&&minute>=evening&&minute<Math.min(24*60,evening+180)&&total<goal&&goal-total<=leftMax&&!today.equals(steps.getString("eveningNudge",""))){
            steps.edit().putString("eveningNudge",today).apply();long left=goal-total;
            post(c,31,"Almost there",String.format(Locale.US,"Only %,d steps to your goal. A %d-minute walk will do it.",left,Math.max(5,Math.round(left/100.0))));
            return;
        }
        long lastMove=steps.getLong("lastMoveAt",0);
        boolean inHours=start<=end?minute>=start&&minute<end:minute>=start||minute<end;
        if(inactiveOn(c)&&inHours&&lastMove>0&&now-lastMove>=after*60000L&&now-steps.getLong("inactiveNudgeAt",0)>=Math.max(2*after,60)*60000L&&total<goal){
            steps.edit().putLong("inactiveNudgeAt",now).apply();
            post(c,32,"Time to move",(after>=60&&after%60==0?"You've been still for "+(after==60?"an hour":after/60+" hours"):"You've been still for "+after+" minutes")+". A 5-minute walk adds about 500 steps.");
        }
    }
    /** Posts one sample of every Kadamm notification, labelled as a preview, so the user can see them in advance. */
    static void preview(Context c){
        post(c,41,"Preview · Daily goal achieved!","Great work! You've reached your daily activity goal today.");
        post(c,42,"Preview · You're making progress!","You've completed 6,000 steps today. Just 4,000 more to reach your daily goal. Keep moving!");
        post(c,43,"Preview · Time to move","You've been still for an hour. A 5-minute walk adds about 500 steps.");
        post(c,44,"Preview · Almost there","Only 1,240 steps to your goal. A 12-minute walk will do it.");
        previewLive(c);
    }
    /** The live-session card with sample numbers; not ongoing, and dismissable. */
    private static void previewLive(Context c){
        NotificationManager nm=c.getSystemService(NotificationManager.class);if(nm==null)return;
        if(Build.VERSION.SDK_INT>=26){NotificationChannel ch=new NotificationChannel("kadamm_live","Live session",NotificationManager.IMPORTANCE_DEFAULT);ch.setSound(null,null);ch.enableVibration(false);ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);nm.createNotificationChannel(ch);}
        android.widget.RemoteViews small=new android.widget.RemoteViews(c.getPackageName(),R.layout.notif_live_small),big=new android.widget.RemoteViews(c.getPackageName(),R.layout.notif_live_big);
        long base=android.os.SystemClock.elapsedRealtime()-754000;
        for(android.widget.RemoteViews v:new android.widget.RemoteViews[]{small,big}){v.setTextViewText(R.id.n_title,"Preview · Walking");v.setChronometer(R.id.n_time,base,null,true);v.setTextViewText(R.id.n_distance,v==small?"1.24 km":"1.24");v.setTextViewText(R.id.n_pace,v==small?"10:08 /km":"10:08");}
        big.setTextViewText(R.id.n_status,"GPS ±4 m");big.setTextViewText(R.id.n_steps,"1,650");big.setTextViewText(R.id.n_kcal,"75");big.setTextViewText(R.id.n_speed,"5.9");big.setTextViewText(R.id.n_today,"6,120");
        big.setProgressBar(R.id.n_goal,1000,612,false);big.setTextViewText(R.id.n_goal_text,"3,880 steps to your 10,000 goal");
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,"kadamm_live"):new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_stat_kadamm).setColor(Color.rgb(26,115,232)).setContentTitle("Preview · Walking").setStyle(new Notification.DecoratedCustomViewStyle())
            .setCustomContentView(small).setCustomBigContentView(big).setAutoCancel(true).setShowWhen(false).setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(PendingIntent.getActivity(c,7,new Intent(c,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
        b.addAction(new Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(c,R.drawable.ic_pause),"Pause",null).build());
        b.addAction(new Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(c,R.drawable.ic_stop),"Finish",null).build());
        nm.notify(45,b.build());
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
