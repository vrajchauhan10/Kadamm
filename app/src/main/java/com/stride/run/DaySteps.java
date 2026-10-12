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
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * All-day step totals from the phone's own hardware step counter, plus the single place that
 * decides today's step total from every source Kadamm has (phone counter, Health Connect, sessions).
 *
 * The hardware counter is cumulative since boot, so each reading is turned into a delta against the
 * previous reading and credited to the local day(s) it covers.
 */
final class DaySteps {
    static final String PREFS="kadamm_steps";
    private static final String GOAL_CHANNEL="kadamm_goals";
    private static final int GOAL_NOTIFICATION=21;
    private static final Object LOCK=new Object();
    private DaySteps() {}

    static boolean sensorPresent(Context c){
        SensorManager sm=(SensorManager)c.getSystemService(Context.SENSOR_SERVICE);
        return sm!=null&&sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)!=null;
    }
    static boolean permitted(Context c){
        return Build.VERSION.SDK_INT<29||c.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)==PackageManager.PERMISSION_GRANTED;
    }
    /** True when Kadamm can count steps all day without any other app. */
    static boolean ready(Context c){return sensorPresent(c)&&permitted(c);}

    static String key(long timeMs){return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date(timeMs));}
    static String todayKey(){return key(System.currentTimeMillis());}

    /** Records one raw TYPE_STEP_COUNTER value. Safe to call from any listener, as often as it fires. */
    static void ingest(Context context,float raw){
        Context c=context.getApplicationContext();
        boolean changed=false;
        synchronized(LOCK){
            SharedPreferences p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
            long now=System.currentTimeMillis(),elapsed=SystemClock.elapsedRealtime(),counter=(long)raw;
            long last=p.getLong("lastCounter",-1),lastAt=p.getLong("lastAt",0),lastElapsed=p.getLong("lastElapsed",0);
            SharedPreferences.Editor e=p.edit();
            if(last<0){
                e.putLong("startedAt",now);
            }else{
                boolean rebooted=elapsed<lastElapsed||counter<last;
                long delta=rebooted?counter:counter-last;
                // Nobody sustains more than ~4 steps a second; a larger jump is a sensor glitch, not walking.
                long plausible=Math.max(1,(now-lastAt)/1000)*4+20;
                if(now<lastAt)plausible=20;
                delta=Math.max(0,Math.min(delta,plausible));
                if(delta>0){
                    try{
                        JSONObject days=new JSONObject(p.getString("days","{}"));
                        distribute(days,lastAt,now,delta);
                        prune(days);
                        e.putString("days",days.toString());
                        changed=true;
                    }catch(Exception ignored){}
                }
            }
            e.putLong("lastCounter",counter).putLong("lastAt",now).putLong("lastElapsed",elapsed).apply();
        }
        if(changed)checkGoal(c);
    }

    /** Splits a delta across local days in proportion to the time spent in each. */
    private static void distribute(JSONObject days,long from,long to,long delta) throws Exception{
        if(to<=from||key(from).equals(key(to))){add(days,key(to),delta);return;}
        long total=to-from,cursor=from,remaining=delta;
        while(cursor<to&&remaining>0){
            Calendar next=Calendar.getInstance();next.setTimeInMillis(cursor);
            next.set(Calendar.HOUR_OF_DAY,0);next.set(Calendar.MINUTE,0);next.set(Calendar.SECOND,0);next.set(Calendar.MILLISECOND,0);
            next.add(Calendar.DAY_OF_YEAR,1);
            long end=Math.min(next.getTimeInMillis(),to);
            long share=end>=to?remaining:Math.min(remaining,Math.round(delta*((end-cursor)/(double)total)));
            add(days,key(cursor),share);remaining-=share;cursor=end;
        }
    }
    private static void add(JSONObject days,String day,long n) throws Exception{days.put(day,days.optLong(day,0)+n);}
    private static void prune(JSONObject days){
        if(days.length()<=420)return;
        String cutoff=key(System.currentTimeMillis()-400L*86400000L);
        for(Iterator<String> it=days.keys();it.hasNext();){if(it.next().compareTo(cutoff)<0)it.remove();}
    }

    /** Takes one reading from the sensor and stops listening. Used when the app is not on screen. */
    static void sample(Context context,Runnable done){
        final Context c=context.getApplicationContext();
        final SensorManager sm=(SensorManager)c.getSystemService(Context.SENSOR_SERVICE);
        final Sensor counter=sm==null?null:sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        if(counter==null||!permitted(c)){if(done!=null)done.run();return;}
        final Handler handler=new Handler(Looper.getMainLooper());
        final boolean[] finished={false};
        final SensorEventListener listener=new SensorEventListener(){
            @Override public void onSensorChanged(SensorEvent e){
                if(finished[0])return;
                finished[0]=true;sm.unregisterListener(this);handler.removeCallbacksAndMessages(null);
                ingest(c,e.values[0]);
                if(done!=null)done.run();
            }
            @Override public void onAccuracyChanged(Sensor sensor,int accuracy){}
        };
        sm.registerListener(listener,counter,SensorManager.SENSOR_DELAY_NORMAL,handler);
        handler.postDelayed(()->{if(finished[0])return;finished[0]=true;sm.unregisterListener(listener);if(done!=null)done.run();},4000);
    }

    /** Steps the phone counter credited to a day. */
    static long phoneOn(Context c,String day){
        try{return new JSONObject(c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString("days","{}")).optLong(day,0);}catch(Exception e){return 0;}
    }
    static long startedAt(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getLong("startedAt",0);}

    private static JSONArray sessions(Context c){
        try{return new JSONArray(c.getSharedPreferences("kadamm_history",Context.MODE_PRIVATE).getString("sessions","[]"));}catch(Exception e){return new JSONArray();}
    }
    /** Steps recorded inside Kadamm sessions on a day, including the session in progress. */
    static long sessionOn(Context c,String day){
        long n=0;JSONArray all=sessions(c);
        for(int i=0;i<all.length();i++){
            JSONObject s=all.optJSONObject(i);
            if(s!=null&&s.optBoolean("stepsAvailable",false)&&day.equals(s.optString("day","")))n+=s.optLong("steps",0);
        }
        SharedPreferences live=c.getSharedPreferences("stride_state",Context.MODE_PRIVATE);
        if(day.equals(todayKey())&&live.getBoolean("active",false))n+=live.getInt("steps",0);
        return n;
    }
    /** Today's Health Connect steps from outside Kadamm sessions, or -1 when nothing was synced today. */
    static long healthImportedToday(Context c){
        SharedPreferences h=c.getSharedPreferences("kadamm_health",Context.MODE_PRIVATE);
        if(!h.getBoolean("healthConnected",false)||!todayKey().equals(h.getString("healthDate","")))return -1;
        return h.getLong("healthImportedSteps",Math.max(0,h.getLong("healthSteps",0)-h.getLong("healthSessionSteps",0)));
    }

    /**
     * Today's steps. The phone counter and Health Connect both measure the whole day, so the larger
     * of the two is used rather than their sum; session steps are already inside the phone count.
     */
    static long todayTotal(Context c){
        String today=todayKey();
        long session=sessionOn(c,today),imported=healthImportedToday(c);
        long health=imported<0?0:imported+session;
        return Math.max(phoneOn(c,today),Math.max(health,session));
    }
    /** "phone", "health", "sessions" or "none": where today's figure comes from. */
    static String todaySource(Context c){
        String today=todayKey();
        long session=sessionOn(c,today),imported=healthImportedToday(c),phone=phoneOn(c,today);
        long health=imported<0?0:imported+session;
        if(imported>0&&health>=phone)return "health";
        if(ready(c)&&startedAt(c)>0)return "phone";
        if(imported>=0)return "health";
        return session>0?"sessions":"none";
    }
    static boolean hasTodayData(Context c){return !"none".equals(todaySource(c));}

    /** Every day Kadamm knows a step total for. Built once per screen so charts and streaks agree. */
    static Map<String,Long> allDayTotals(Context c){
        HashMap<String,Long> out=new HashMap<>();
        merge(out,c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString("days","{}"));
        merge(out,c.getSharedPreferences("kadamm_health",Context.MODE_PRIVATE).getString("healthDays","{}"));
        HashMap<String,Long> bySession=new HashMap<>();JSONArray all=sessions(c);
        for(int i=0;i<all.length();i++){
            JSONObject s=all.optJSONObject(i);
            if(s==null||!s.optBoolean("stepsAvailable",false))continue;
            String day=s.optString("day","");
            Long old=bySession.get(day);bySession.put(day,(old==null?0:old)+s.optLong("steps",0));
        }
        for(Map.Entry<String,Long> e:bySession.entrySet()){Long old=out.get(e.getKey());if(old==null||e.getValue()>old)out.put(e.getKey(),e.getValue());}
        long today=todayTotal(c);
        if(today>0||hasTodayData(c))out.put(todayKey(),today);
        return out;
    }
    private static void merge(HashMap<String,Long> out,String json){
        try{
            JSONObject o=new JSONObject(json);
            for(Iterator<String> it=o.keys();it.hasNext();){String k=it.next();long v=o.optLong(k,0);Long old=out.get(k);if(old==null||v>old)out.put(k,v);}
        }catch(Exception ignored){}
    }

    /** {current streak, longest streak, days the goal was reached}. A day still in progress never breaks a streak. */
    static int[] streaks(Map<String,Long> totals,int goal){
        int current=0,longest=0,achieved=0,run=0;
        String earliest=null;
        for(String k:totals.keySet())if(earliest==null||k.compareTo(earliest)<0)earliest=k;
        if(earliest==null)return new int[]{0,0,0};
        Calendar d=Calendar.getInstance(),end=Calendar.getInstance();
        try{d.setTime(new SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(earliest));}catch(Exception e){return new int[]{0,0,0};}
        for(int guard=0;!d.after(end)&&guard<4000;guard++,d.add(Calendar.DAY_OF_YEAR,1)){
            Long v=totals.get(key(d.getTimeInMillis()));
            if(v!=null&&v>=goal){run++;achieved++;longest=Math.max(longest,run);}else run=0;
        }
        Calendar back=Calendar.getInstance();
        Long today=totals.get(key(back.getTimeInMillis()));
        if(today==null||today<goal)back.add(Calendar.DAY_OF_YEAR,-1);
        for(int guard=0;guard<4000;guard++,back.add(Calendar.DAY_OF_YEAR,-1)){
            Long v=totals.get(key(back.getTimeInMillis()));
            if(v==null||v<goal)break;
            current++;
        }
        return new int[]{current,longest,achieved};
    }

    /** Posts the "making progress" and "goal achieved" notifications, each at most once a day. */
    static void checkGoal(Context c){
        if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        int goal=c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE).getInt("stepGoal",10000);
        long n=todayTotal(c);
        SharedPreferences p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        String today=todayKey(),title,text;
        if(n>=goal){
            if(today.equals(p.getString("goalNotified","")))return;
            p.edit().putString("goalNotified",today).putString("progressNotified",today).apply();
            title="Daily goal achieved!";text="Great work! You've reached your daily activity goal today.";
        }else if(n>=Math.ceil(goal*.6)){
            if(today.equals(p.getString("progressNotified","")))return;
            p.edit().putString("progressNotified",today).apply();
            title="You're making progress!";
            text=String.format(Locale.US,"You've completed %,d steps today. Just %,d more to reach your daily goal. Keep moving!",n,goal-n);
        }else return;
        NotificationManager nm=c.getSystemService(NotificationManager.class);
        if(nm==null)return;
        if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(GOAL_CHANNEL,"Daily goal",NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent open=PendingIntent.getActivity(c,3,new Intent(c,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,GOAL_CHANNEL):new Notification.Builder(c);
        b.setContentTitle(title).setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_menu_directions).setContentIntent(open).setAutoCancel(true);
        nm.notify(GOAL_NOTIFICATION,b.build());
    }
}
