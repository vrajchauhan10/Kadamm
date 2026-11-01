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
            String[] m=Messages.pick(c,"close",Messages.CLOSE,left,Math.max(5,Math.round(left/100.0)));if(m!=null)post(c,31,m[0],m[1]);
            return;
        }
        // Daily summary: once a day from the chosen time (default 9 pm).
        int sumAt=get(c,"sumAt",21*60);
        if(c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE).getBoolean("notifySummary",true)&&minute>=sumAt&&!today.equals(steps.getString("summarySent",""))){
            steps.edit().putString("summarySent",today).apply();postSummary(c,34,"");return;
        }
        // Weekly report: Monday from 9 am, once per week.
        String weekKey=DaySteps.key(Progress.week(0).start.getTimeInMillis());
        if(c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE).getBoolean("notifyWeekly",true)&&cal.get(Calendar.DAY_OF_WEEK)==Calendar.MONDAY&&minute>=9*60&&!weekKey.equals(steps.getString("weeklySent",""))){
            steps.edit().putString("weeklySent",weekKey).apply();
            org.json.JSONArray hist;try{hist=new org.json.JSONArray(c.getSharedPreferences("kadamm_history",Context.MODE_PRIVATE).getString("sessions","[]"));}catch(Exception e){hist=new org.json.JSONArray();}
            java.util.Map<String,Long> totals=DaySteps.allDayTotals(c);Progress.Summary last=Progress.summarize(Progress.week(-1),hist,totals,goal),before=Progress.summarize(Progress.week(-2),hist,totals,goal);
            String change=before.steps>0?String.format(Locale.US," (%+d%% vs the week before)",Math.round((last.steps-before.steps)*100.0/before.steps)):"";
            postTo(c,33,"Your week in Kadamm",String.format(Locale.US,"%,d steps%s, %d of 7 goal days, %d sessions. Tap for your report.",last.steps,change,last.goalDays,last.sessions),"Reports");
            return;
        }
        long lastMove=steps.getLong("lastMoveAt",0);
        boolean inHours=start<=end?minute>=start&&minute<end:minute>=start||minute<end;
        if(inactiveOn(c)&&inHours&&Messages.countToday(c,"move")<3&&lastMove>0&&now-lastMove>=after*60000L&&now-steps.getLong("inactiveNudgeAt",0)>=Math.max(2*after,60)*60000L&&total<goal){
            steps.edit().putLong("inactiveNudgeAt",now).apply();
            String span=after>=60&&after%60==0?(after==60?"an hour":after/60+" hours"):after+" minutes";
            String[] m=Messages.pick(c,"move",Messages.MOVE,span);if(m!=null)post(c,32+Messages.countToday(c,"move"),m[0],Character.toUpperCase(m[1].charAt(0))+m[1].substring(1));
        }
    }
    /** Posts one sample of every Kadamm notification, labelled as a preview, so the user can see them in advance. */
    static void preview(Context c){
        post(c,41,"Preview · Daily goal achieved!","Great work! You've reached your daily activity goal today.");
        post(c,42,"Preview · You're making progress!","You've completed 6,000 steps today. Just 4,000 more to reach your daily goal. Keep moving!");
        postSummary(c,47,"Preview · ");
        postTo(c,46,"Preview · Your week in Kadamm","52,340 steps (+8% vs the week before), 5 of 7 goal days, 4 sessions. Tap for your report.","Reports");
        post(c,43,"Preview · Time to move","You've been still for an hour. A 5-minute walk adds about 500 steps.");
        post(c,44,"Preview · Close to your goal","Only 1,240 steps to your goal. A 12-minute walk will do it.");
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
    /** Icon and colour for each kind of summary row, matching the Today card. */
    private static int[] look(String kind){
        switch(kind){
            case "steps":return new int[]{R.drawable.ic_walk,Color.rgb(26,115,232)};case "up":return new int[]{R.drawable.ic_chart,Color.rgb(30,142,62)};
            case "down":return new int[]{R.drawable.ic_chart,Color.rgb(232,113,10)};case "sessions":return new int[]{R.drawable.ic_place,Color.rgb(147,52,230)};
            case "active":return new int[]{R.drawable.ic_timer,Color.rgb(30,142,62)};case "left":return new int[]{R.drawable.ic_flag,Color.rgb(18,158,175)};
            case "streak":return new int[]{R.drawable.ic_bolt,Color.rgb(242,153,0)};default:return new int[]{R.drawable.ic_chart,Color.rgb(128,134,139)};
        }
    }
    /** The daily summary as a designed card: headline and steps when collapsed; goal bar and icon rows when expanded. */
    static void postSummary(Context c,int id,String prefix){
        NotificationManager nm=c.getSystemService(NotificationManager.class);if(nm==null)return;
        if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Reminders",NotificationManager.IMPORTANCE_DEFAULT));
        Coach.Summary sum=Coach.today(c);
        android.widget.RemoteViews small=new android.widget.RemoteViews(c.getPackageName(),R.layout.notif_summary_small),big=new android.widget.RemoteViews(c.getPackageName(),R.layout.notif_summary_big);
        small.setTextViewText(R.id.s_head,prefix+sum.headline);small.setTextViewText(R.id.s_line,sum.insight.isEmpty()?(sum.items.isEmpty()?sum.title:sum.items.get(0).text):sum.insight);
        big.setTextViewText(R.id.s_title,(prefix+sum.title).toUpperCase(Locale.getDefault())+"  ·  ✦ KADAMM INSIGHT");big.setTextViewText(R.id.s_head,sum.insight.isEmpty()?sum.headline:sum.insight);big.setFloat(R.id.s_head,"setTextSize",sum.insight.isEmpty()?20f:15f);
        big.setProgressBar(R.id.s_goal,1000,(int)Math.min(1000,sum.steps*1000L/Math.max(1,sum.goal)),false);
        int[] rowIds={R.id.s_row1,R.id.s_row2,R.id.s_row3,R.id.s_row4,R.id.s_row5,R.id.s_row6,R.id.s_row7},icIds={R.id.s_ic1,R.id.s_ic2,R.id.s_ic3,R.id.s_ic4,R.id.s_ic5,R.id.s_ic6,R.id.s_ic7},txIds={R.id.s_tx1,R.id.s_tx2,R.id.s_tx3,R.id.s_tx4,R.id.s_tx5,R.id.s_tx6,R.id.s_tx7};
        for(int i=0;i<rowIds.length&&i<sum.items.size();i++){
            int[] l=look(sum.items.get(i).kind);big.setViewVisibility(rowIds[i],android.view.View.VISIBLE);
            big.setImageViewResource(icIds[i],l[0]);big.setInt(icIds[i],"setColorFilter",l[1]);big.setTextViewText(txIds[i],sum.items.get(i).text);
        }
        PendingIntent open=PendingIntent.getActivity(c,6+id,new Intent(c,MainActivity.class).putExtra("page","Today"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent walk=PendingIntent.getActivity(c,60+id,new Intent(c,MainActivity.class).putExtra("page","Today").putExtra("startWalk",true),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,CHANNEL):new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_stat_kadamm).setColor(Color.rgb(26,115,232)).setContentTitle(prefix+sum.headline).setContentText(sum.text().replace("\n"," "))
            .setStyle(new Notification.DecoratedCustomViewStyle()).setCustomContentView(small).setCustomBigContentView(big).setContentIntent(open).setAutoCancel(true)
            .addAction(new Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(c,R.drawable.ic_chart),"View today",open).build());
        if(sum.steps<sum.goal)b.addAction(new Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(c,R.drawable.ic_walk),"Start a walk",walk).build());
        nm.notify(id,b.build());
    }
    private static void post(Context c,int id,String title,String text){postTo(c,id,title,text,null);}
    private static void postTo(Context c,int id,String title,String text,String page){
        NotificationManager nm=c.getSystemService(NotificationManager.class);if(nm==null)return;
        if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Reminders",NotificationManager.IMPORTANCE_DEFAULT));
        nm.notify(id,rich(c,CHANNEL,id,title,text,page,!"Reports".equals(page)).build());
    }
    /**
     * A Kadamm notification with today's progress built in: a step ring with the percentage as the large icon,
     * steps against the goal under the expanded text, and one-tap actions to open the app or start a walk.
     */
    static Notification.Builder rich(Context c,String channel,int id,String title,String text,String page,boolean walkAction){
        int goal=c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE).getInt("stepGoal",10000);long steps=DaySteps.todayTotal(c);boolean done=steps>=goal;
        PendingIntent open=PendingIntent.getActivity(c,6+id,new Intent(c,MainActivity.class).putExtra("page",page==null?"Today":page),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,channel):new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_stat_kadamm).setColor(done?Color.rgb(30,142,62):Color.rgb(26,115,232)).setContentTitle(title).setContentText(text)
            .setStyle(new Notification.BigTextStyle().bigText(text+String.format(Locale.US,"\n\n%,d of %,d steps today",steps,goal))).setContentIntent(open).setAutoCancel(true)
            .setLargeIcon(ring(c,steps,goal));
        if("Reports".equals(page))b.addAction(new Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(c,R.drawable.ic_chart),"Open report",open).build());
        else b.addAction(new Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(c,R.drawable.ic_home),"View today",open).build());
        if(walkAction&&!done){PendingIntent walk=PendingIntent.getActivity(c,60+id,new Intent(c,MainActivity.class).putExtra("page","Today").putExtra("startWalk",true),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            b.addAction(new Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(c,R.drawable.ic_walk),"Start a walk",walk).build());}
        return b;
    }
    /** The large icon: a ring filled toward the goal with the percentage inside, or a green tick once it's reached. */
    static android.graphics.Bitmap ring(Context c,long steps,int goal){
        int size=192;android.graphics.Bitmap bm=android.graphics.Bitmap.createBitmap(size,size,android.graphics.Bitmap.Config.ARGB_8888);android.graphics.Canvas cv=new android.graphics.Canvas(bm);
        android.graphics.Paint p=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);float f=Math.min(1f,steps/(float)Math.max(1,goal));boolean done=steps>=goal;
        int col=done?Color.rgb(30,142,62):Color.rgb(26,115,232);
        p.setColor(Color.rgb(17,17,20));cv.drawCircle(size/2f,size/2f,size/2f,p);
        android.graphics.RectF o=new android.graphics.RectF(26,26,size-26,size-26);p.setStyle(android.graphics.Paint.Style.STROKE);p.setStrokeWidth(18);p.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        p.setColor(Color.argb(70,255,255,255));cv.drawArc(o,0,360,false,p);p.setColor(done?Color.rgb(129,201,149):Color.rgb(77,144,254));if(f>0)cv.drawArc(o,-90,Math.max(6,f*360),false,p);
        p.setStyle(android.graphics.Paint.Style.FILL);p.setColor(Color.WHITE);p.setTextAlign(android.graphics.Paint.Align.CENTER);p.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));
        if(done){p.setStyle(android.graphics.Paint.Style.STROKE);p.setStrokeWidth(13);cv.drawLine(66,98,86,118,p);cv.drawLine(86,118,128,74,p);}
        else{p.setTextSize(44);cv.drawText(Math.round(f*100)+"%",size/2f,size/2f+15,p);}
        return bm;
    }
}
