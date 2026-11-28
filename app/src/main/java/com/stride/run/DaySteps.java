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
                        long gapFrom=rebooted?Math.max(lastAt,now-elapsed):lastAt;
                        if(lastAt>0&&now-gapFrom>GAP_MS){
                            // A long gap: Android didn't let Kadamm read the sensor (phone asleep, battery saver). The
                            // steps are real but their time isn't known: they're placed over the gap's waking hours
                            // for now, and re-placed by Health Connect's hour-by-hour record once it syncs.
                            JSONObject hours=new JSONObject(p.getString("hours","{}"));java.util.LinkedHashMap<String,Long> a=allocate(wakingWeights(gapFrom,now),delta);
                            apply(days,hours,a,1);e.putString("hours",hours.toString());
                            JSONArray gaps=new JSONArray(p.getString("gaps","[]"));JSONObject alloc=new JSONObject();for(java.util.Map.Entry<String,Long> x:a.entrySet())alloc.put(x.getKey(),x.getValue());
                            gaps.put(new JSONObject().put("f",gapFrom).put("t",now).put("d",delta).put("a",alloc).put("hc",false));
                            JSONArray keep=new JSONArray();for(int i=0;i<gaps.length();i++){JSONObject g=gaps.optJSONObject(i);if(g!=null&&g.optLong("t")>now-8L*86400000L)keep.put(g);}e.putString("gaps",keep.toString());
                        }else{
                        distribute(days,lastAt,now,delta);
                        try{JSONObject hours=new JSONObject(p.getString("hours","{}"));spreadHours(hours,lastAt,now,delta);e.putString("hours",hours.toString());}catch(Exception ignored){}
                        }
                        prune(days);
                        e.putString("days",days.toString()).putLong("lastMoveAt",now);
                        // Sitting: 10+ steps after 30+ minutes without walking counts as getting up from a sit.
                        // The steps happened somewhere between the last reading and now. Only the span the readings show still
                        // counts as a sit: after a long gap (phone asleep), when you got up isn't known, so the sit ends at the last reading.
                        if(delta>=10){long prev=p.getLong("activeAt",0);if(prev>0&&now-prev>=30*60000L){long end=now-lastAt<=20*60000L?Math.max(lastAt,now-delta*600L):lastAt;if(end-prev>=30*60000L)recordSit(e,p,prev,end);}e.putLong("activeAt",now);}
                        changed=true;
                    }catch(Exception ignored){}
                }
            }
            e.putLong("lastCounter",counter).putLong("lastAt",now).putLong("lastElapsed",elapsed).apply();
        }
        if(changed){checkGoal(c);StepsWidget.refresh(c);}
    }

    /** Readings further apart than this are a gap: the steps' times aren't known. */
    static final long GAP_MS=2*3600000L;
    /** Weight of each hour slot ("yyyy-MM-dd|h") in [from, to]: minutes inside it, a tenth as much between 11 pm and 7 am. */
    static java.util.LinkedHashMap<String,Double> wakingWeights(long from,long to){
        java.util.LinkedHashMap<String,Double> w=new java.util.LinkedHashMap<>();Calendar c=Calendar.getInstance();long cur=from;
        while(cur<to){c.setTimeInMillis(cur);int hr=c.get(Calendar.HOUR_OF_DAY);String k=key(cur)+"|"+hr;c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);c.add(Calendar.HOUR_OF_DAY,1);
            long end=Math.min(c.getTimeInMillis(),to);w.put(k,w.getOrDefault(k,0d)+(end-cur)/60000.0*(hr>=7&&hr<23?1:.1));cur=end;}
        return w;
    }
    /** Whole steps in proportion to the weights, the remainders going to the largest fractions. */
    static java.util.LinkedHashMap<String,Long> allocate(java.util.LinkedHashMap<String,Double> w,long n){
        double tot=0;for(double v:w.values())tot+=v;java.util.LinkedHashMap<String,Long> out=new java.util.LinkedHashMap<>();if(tot<=0||w.isEmpty()){return out;}
        long given=0;java.util.List<Object[]> frac=new java.util.ArrayList<>();
        for(java.util.Map.Entry<String,Double> e:w.entrySet()){double x=n*e.getValue()/tot;long f=(long)Math.floor(x);out.put(e.getKey(),f);given+=f;frac.add(new Object[]{e.getKey(),x-f});}
        frac.sort((a,b)->Double.compare((double)b[1],(double)a[1]));for(int i=0;given<n&&i<frac.size();i++){String k=(String)frac.get(i)[0];out.put(k,out.get(k)+1);given++;}
        return out;
    }
    /** Adds (sign 1) or takes back (sign −1) an allocation in the day totals and the hourly record. */
    private static void apply(JSONObject days,JSONObject hours,java.util.Map<String,Long> a,int sign)throws Exception{
        for(java.util.Map.Entry<String,Long> x:a.entrySet()){String[] k=x.getKey().split("\\|");long n=sign*x.getValue();days.put(k[0],Math.max(0,days.optLong(k[0],0)+n));
            JSONArray h=hours.optJSONArray(k[0]);if(h==null){h=new JSONArray();for(int i=0;i<24;i++)h.put(0);hours.put(k[0],h);}int hr=Integer.parseInt(k[1]);h.put(hr,Math.max(0,h.optDouble(hr)+n));}
    }
    /**
     * Re-places gap steps by Health Connect's record of when you walked (its steps per hour, 100+ in the gap needed),
     * once it has synced. The phone's own count is kept; only its days and hours move.
     */
    static void rebalance(Context context){
        Context c=context.getApplicationContext();JSONObject md=minuteDays(c);if(md.length()==0)return;boolean changed=false;
        synchronized(LOCK){
            SharedPreferences p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
            try{JSONArray gaps=new JSONArray(p.getString("gaps","[]"));JSONObject days=new JSONObject(p.getString("days","{}")),hours=new JSONObject(p.getString("hours","{}"));
                for(int i=0;i<gaps.length();i++){JSONObject g=gaps.getJSONObject(i);if(g.optBoolean("hc"))continue;long f=g.getLong("f"),t=g.getLong("t");
                    // Only once Health Connect has caught up past the gap's end (its record for that day exists).
                    if(md.optJSONObject(key(t))==null)continue;
                    java.util.LinkedHashMap<String,Double> w=new java.util.LinkedHashMap<>();double tot=0;
                    for(String k:wakingWeights(f,t).keySet()){String[] kk=k.split("\\|");JSONObject d=md.optJSONObject(kk[0]);JSONArray hh=d==null?null:d.optJSONArray("h");double v=hh==null?0:hh.optDouble(Integer.parseInt(kk[1]),0);
                        // Part-hours at the gap's ends count in proportion to the minutes inside the gap.
                        Calendar st=Calendar.getInstance();st.setTimeInMillis(f);Calendar en=Calendar.getInstance();en.setTimeInMillis(t);double frac=1;
                        if(k.equals(key(f)+"|"+st.get(Calendar.HOUR_OF_DAY)))frac=Math.min(frac,(60-st.get(Calendar.MINUTE))/60.0);if(k.equals(key(t)+"|"+en.get(Calendar.HOUR_OF_DAY)))frac=Math.min(frac,en.get(Calendar.MINUTE)/60.0);
                        w.put(k,v*frac);tot+=v*frac;}
                    if(tot<100)continue;
                    JSONObject old=g.getJSONObject("a");java.util.Map<String,Long> o=new java.util.HashMap<>();for(Iterator<String> it=old.keys();it.hasNext();){String k=it.next();o.put(k,old.getLong(k));}
                    java.util.LinkedHashMap<String,Long> a=allocate(w,g.getLong("d"));apply(days,hours,o,-1);apply(days,hours,a,1);
                    JSONObject na=new JSONObject();for(java.util.Map.Entry<String,Long> x:a.entrySet())na.put(x.getKey(),x.getValue());g.put("a",na).put("hc",true);changed=true;}
                // Before 10.0 a gap's steps all went into the hour of the next reading. Where Health Connect's hours show
                // the steps were really taken earlier (in hours Kadamm has as empty), they're moved there, once.
                for(int back=0;back<4;back++){String d=key(System.currentTimeMillis()-back*86400000L);JSONArray own=hours.optJSONArray(d);JSONObject hd=md.optJSONObject(d);if(own==null||hd==null||hd.optJSONArray("h")==null)continue;
                    for(int H=0;H<24;H++){double block=own.optDouble(H);String fk="fixed_"+d+"|"+H;if(block<800||p.getBoolean(fk,false))continue;
                        // The empty hours before it (back to Kadamm's previous reading), possibly into the day before.
                        java.util.List<String> span=new java.util.ArrayList<>();Calendar cc=Calendar.getInstance();cc.setTime(new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.US).parse(d));cc.set(Calendar.HOUR_OF_DAY,H);
                        for(int k=0;k<30;k++){cc.add(Calendar.HOUR_OF_DAY,-1);String dk=key(cc.getTimeInMillis());JSONArray oh=hours.optJSONArray(dk);double ov=oh==null?-1:oh.optDouble(cc.get(Calendar.HOUR_OF_DAY));if(ov!=0)break;span.add(dk+"|"+cc.get(Calendar.HOUR_OF_DAY));}
                        if(span.size()<3)continue;span.add(d+"|"+H);
                        java.util.LinkedHashMap<String,Double> w=new java.util.LinkedHashMap<>();double tot=0,before=0;
                        for(String k:span){String[] kk=k.split("\\|");JSONObject x=md.optJSONObject(kk[0]);JSONArray hh=x==null?null:x.optJSONArray("h");double v=hh==null?0:hh.optDouble(Integer.parseInt(kk[1]),0);w.put(k,v);tot+=v;if(!k.equals(d+"|"+H))before+=v;}
                        if(tot<100||before<.25*tot){p.edit().putBoolean(fk,true).apply();continue;}
                        java.util.Map<String,Long> o=new java.util.HashMap<>();o.put(d+"|"+H,Math.round(block));apply(days,hours,o,-1);apply(days,hours,allocate(w,Math.round(block)),1);p.edit().putBoolean(fk,true).apply();changed=true;}}
                if(changed)p.edit().putString("gaps",gaps.toString()).putString("days",days.toString()).putString("hours",hours.toString()).apply();}catch(Exception ignored){}
        }
        if(changed)StepsWidget.refresh(c);
    }
    /** Steps on a day that came from a gap in readings: {steps, placed by Health Connect ? 1 : 0}; null when none. */
    static long[] gapSteps(Context c,String day){
        try{JSONArray gaps=new JSONArray(c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString("gaps","[]"));long n=0;boolean all=true;
            for(int i=0;i<gaps.length();i++){JSONObject g=gaps.getJSONObject(i);JSONObject a=g.getJSONObject("a");for(Iterator<String> it=a.keys();it.hasNext();){String k=it.next();if(k.startsWith(day+"|")){n+=a.getLong(k);if(!g.optBoolean("hc"))all=false;}}}
            return n>0?new long[]{n,all?1:0}:null;}catch(Exception e){return null;}
    }

    /** Splits a delta across local days in proportion to the time spent in each. */
    private static void distribute(JSONObject days,long from,long to,long delta) throws Exception{
        // After more than 6 hours without a reading (overnight), the steps belong to now, as in the hourly record.
        if(to<=from||to-from>6*3600000L||key(from).equals(key(to))){add(days,key(to),delta);return;}
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
    /**
     * Kadamm's own steps per hour {day: [24]}, from the phone's step counter: each reading's steps are spread
     * evenly over the time since the last reading (minutes apart while Kadamm is open or recording, up to 15
     * when it's closed). Kept for 35 days.
     */
    private static void spreadHours(JSONObject hours,long from,long to,long delta)throws Exception{
        if(from<=0||to-from>6*3600000L)from=to-60000L;long span=Math.max(1,to-from);double left=delta;
        Calendar c=Calendar.getInstance();c.setTimeInMillis(from);
        while(from<to&&left>0){
            c.setTimeInMillis(from);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);c.add(Calendar.HOUR_OF_DAY,1);
            long end=Math.min(c.getTimeInMillis(),to);double share=end>=to?left:delta*(end-from)/(double)span;
            Calendar at=Calendar.getInstance();at.setTimeInMillis(from);String k=key(from);JSONArray h=hours.optJSONArray(k);if(h==null){h=new JSONArray();for(int i=0;i<24;i++)h.put(0);hours.put(k,h);}
            int hr=at.get(Calendar.HOUR_OF_DAY);h.put(hr,h.optDouble(hr)+share);left-=share;from=end;
        }
        if(hours.length()>36){String cutoff=key(System.currentTimeMillis()-35L*86400000L);for(Iterator<String> it=hours.keys();it.hasNext();)if(it.next().compareTo(cutoff)<0)it.remove();}
    }
    /** Kadamm's own steps per hour for a day, or null before Kadamm counted that day. */
    static double[] hoursOn(Context c,String day){
        try{JSONArray h=new JSONObject(c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString("hours","{}")).optJSONArray(day);if(h==null)return null;double[] out=new double[24];for(int i=0;i<24;i++)out[i]=Math.round(h.optDouble(i));return out;}catch(Exception e){return null;}
    }
    /** Saves one sitting stretch [start, end] under its day, keeping 35 days. Stored as "sits2" since 10.4.0: the
     *  older "sits" guessed when you got up after a reading gap and showed walks as sitting, so they aren't used. */
    private static void recordSit(SharedPreferences.Editor e,SharedPreferences p,long start,long end){
        try{JSONObject all=new JSONObject(p.getString("sits2","{}"));String k=key(end);JSONArray a=all.optJSONArray(k);if(a==null){a=new JSONArray();all.put(k,a);}
            a.put(new JSONArray().put(start).put(end));
            if(all.length()>36){String cutoff=key(System.currentTimeMillis()-35L*86400000L);for(Iterator<String> it=all.keys();it.hasNext();)if(it.next().compareTo(cutoff)<0)it.remove();}
            e.putString("sits2",all.toString());}catch(Exception ignored){}
    }
    /**
     * Today's sitting stretches as {start minute of day, length in minutes}, 30 minutes or longer, between 7 am
     * and 11 pm: from Health Connect's minute steps when that is on (exact), else Kadamm's own readings (to
     * within its sampling, a few minutes while open, up to 15 when closed).
     */
    static java.util.List<int[]> sitsToday(Context c){return withoutSessions(c,sitsRaw(c));}
    /** A Kadamm session is walking, so no sit can cover it: sits are cut around sessions, and pieces under 30 minutes dropped. */
    private static java.util.List<int[]> withoutSessions(Context c,java.util.List<int[]> sits){
        Calendar mid=Calendar.getInstance();mid.set(Calendar.HOUR_OF_DAY,0);mid.set(Calendar.MINUTE,0);mid.set(Calendar.SECOND,0);mid.set(Calendar.MILLISECOND,0);long m0=mid.getTimeInMillis();
        java.util.List<int[]> walks=new java.util.ArrayList<>();JSONArray all=sessions(c);String today=todayKey();
        for(int i=0;i<all.length();i++){JSONObject o=all.optJSONObject(i);if(o==null||!today.equals(o.optString("day","")))continue;long st=o.optLong("id",0);if(st<m0)continue;int a=(int)((st-m0)/60000);walks.add(new int[]{a,a+(int)Math.max(1,o.optLong("durationMs",0)/60000)});}
        java.util.List<int[]> cur=sits;
        for(int[] w:walks){java.util.List<int[]> next=new java.util.ArrayList<>();
            for(int[] x:cur){int s=x[0],e=x[0]+x[1];if(w[1]<=s||w[0]>=e){next.add(x);continue;}if(w[0]-s>=30)next.add(new int[]{s,w[0]-s});if(e-w[1]>=30)next.add(new int[]{w[1],e-w[1]});}
            cur=next;}
        return cur;
    }
    private static java.util.List<int[]> sitsRaw(Context c){
        java.util.List<int[]> out=new java.util.ArrayList<>();String today=todayKey();
        JSONObject m=minuteDays(c).optJSONObject(today);JSONArray hc=m!=null?m.optJSONArray("sits"):null;
        if(hc!=null){for(int i=0;i<hc.length();i++){JSONArray r=hc.optJSONArray(i);if(r!=null)out.add(new int[]{r.optInt(0),r.optInt(1)});}return out;}
        try{JSONArray a=new JSONObject(c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString("sits2","{}")).optJSONArray(today);if(a==null)return out;
            Calendar mid=Calendar.getInstance();mid.set(Calendar.HOUR_OF_DAY,0);mid.set(Calendar.MINUTE,0);mid.set(Calendar.SECOND,0);mid.set(Calendar.MILLISECOND,0);long m0=mid.getTimeInMillis();
            double[] hrs=hoursOn(c,today);
            for(int i=0;i<a.length();i++){JSONArray r=a.optJSONArray(i);if(r==null)continue;int s=(int)Math.max(420,(r.optLong(0)-m0)/60000),e=(int)Math.min(1380,(r.optLong(1)-m0)/60000);if(e-s<30)continue;
                // A sit with 60+ steps counted inside one of its whole hours wasn't a sit (saved before 10.4.0 across a reading gap).
                boolean moved=false;for(int h=(s+59)/60;hrs!=null&&(h+1)*60<=e&&h<24;h++)if(hrs[h]>=60)moved=true;if(!moved)out.add(new int[]{s,e-s});}
        }catch(Exception ignored){}
        return out;
    }
    /** Minutes since you last walked (10+ steps), or -1 if unknown. */
    /** Minutes since you last walked: the later of this phone's step sensor and Health Connect's minute steps (a watch's, when shared). */
    static long stillMinutes(Context c){SharedPreferences sp=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);long a=sp.getLong("activeAt",0),lastAt=sp.getLong("lastAt",0);
        JSONObject m=minuteDays(c).optJSONObject(todayKey());int lm=m==null?-1:m.optInt("lastMove",-1);
        if(lm>=0){Calendar mid=Calendar.getInstance();mid.set(Calendar.HOUR_OF_DAY,0);mid.set(Calendar.MINUTE,0);mid.set(Calendar.SECOND,0);mid.set(Calendar.MILLISECOND,0);a=Math.max(a,mid.getTimeInMillis()+(lm+1)*60000L);}
        // Still only as far as a reading confirms it: with no reading for 20+ minutes, you may have walked since.
        long now=System.currentTimeMillis(),seen=now-lastAt<=20*60000L?now:Math.max(lastAt,a);
        return a<=0?-1:Math.max(0,(seen-a)/60000);}
    /** Where sitting and moving come from: the Health Connect app whose minute steps are used, or this phone. */
    static String sitSource(Context c){JSONObject m=minuteDays(c).optJSONObject(todayKey());if(m!=null&&m.has("sits")){String o=c.getSharedPreferences("kadamm_health",Context.MODE_PRIVATE).getString("healthOrigin","");return (o.isEmpty()?"Health Connect":HealthSync.label(o))+", minute by minute";}return "this phone's step sensor";}
    /** The phone step counter's last raw reading (since boot), or 0. */
    static long rawCounter(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getLong("lastCounter",0);}
    /** Whether Health Connect's step figures are used at all (Settings: Step sources). Off means Kadamm counts on its own. */
    static boolean hcSteps(Context c){return c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE).getBoolean("hcSteps",true);}
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

    /** Per-day movement quality from Health Connect minute steps: {day: {p30, brisk, sit, h[24]}} (see HealthSync.dailyMinutes). */
    static JSONObject minuteDays(Context c){if(!hcSteps(c))return new JSONObject();try{return new JSONObject(c.getSharedPreferences("kadamm_health",Context.MODE_PRIVATE).getString("minuteDays","{}"));}catch(Exception e){return new JSONObject();}}
    private static String parsedRaw;private static JSONArray parsed=new JSONArray();
    /**
     * Saved sessions, parsed once and reused until the stored text changes. The history is about a megabyte of
     * JSON; parsing it on every screen and every second of the live view was what made the app stutter.
     * Callers must treat the result as read-only unless they save it back.
     */
    static synchronized JSONArray sessions(Context c){
        String raw=c.getSharedPreferences("kadamm_history",Context.MODE_PRIVATE).getString("sessions","[]");
        if(raw==parsedRaw||raw.equals(parsedRaw))return parsed;
        try{parsed=new JSONArray(raw);}catch(Exception e){parsed=new JSONArray();}
        parsedRaw=raw;return parsed;
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
    /** Minutes spent in Kadamm sessions today, including the one in progress. */
    static long activeMinutesToday(Context c){
        long ms=0;String today=todayKey();JSONArray all=sessions(c);
        for(int i=0;i<all.length();i++){JSONObject s=all.optJSONObject(i);if(s!=null&&today.equals(s.optString("day","")))ms+=s.optLong("durationMs",0);}
        SharedPreferences live=c.getSharedPreferences("stride_state",Context.MODE_PRIVATE);
        if(live.getBoolean("active",false))ms+=live.getLong("elapsed",0);
        return ms/60000;
    }
    /** Today's full-day Health Connect total from the preferred source (Samsung Health first), or -1 when not synced today. */
    static long healthImportedToday(Context c){
        SharedPreferences h=c.getSharedPreferences("kadamm_health",Context.MODE_PRIVATE);
        if(!hcSteps(c)||!h.getBoolean("healthConnected",false)||!todayKey().equals(h.getString("healthDate","")))return -1;
        return h.getLong("healthDayTotal",h.getLong("healthImportedSteps",0));
    }
    /**
     * Health Connect's figure plus the steps the phone sensor has counted since that sync, so the
     * total keeps rising between syncs instead of waiting for Samsung Health to write again.
     */
    static long healthLiveToday(Context c){
        long hc=healthImportedToday(c);if(hc<0)return -1;
        long atSync=c.getSharedPreferences("kadamm_health",Context.MODE_PRIVATE).getLong("healthPhoneAtSync",-1);
        return hc+(atSync<0?0:Math.max(0,phoneOn(c,todayKey())-atSync));
    }
    /** Today's steps: the most complete whole-day count. Session steps are part of it, never added on top. */
    static long todayTotal(Context c){
        String today=todayKey();
        return Math.max(phoneOn(c,today),Math.max(healthLiveToday(c),sessionOn(c,today)));
    }
    /** Estimated calories from today's Kadamm sessions, including the one in progress. */
    static long sessionCaloriesToday(Context c){
        long n=0;String today=todayKey();JSONArray all=sessions(c);
        for(int i=0;i<all.length();i++){JSONObject s=all.optJSONObject(i);if(s!=null&&today.equals(s.optString("day","")))n+=s.optLong("calories",Math.round(s.optDouble("distanceKm",0)*65));}
        SharedPreferences live=c.getSharedPreferences("stride_state",Context.MODE_PRIVATE);
        n+=Calories.live(c);
        return n;
    }
    /** Move minutes today: Kadamm sessions, or Health Connect's active minutes when that is more. */
    static long moveMinutesToday(Context c){
        long n=activeMinutesToday(c);SharedPreferences h=c.getSharedPreferences("kadamm_health",Context.MODE_PRIVATE);
        if(healthImportedToday(c)>=0)n=Math.max(n,h.getInt("healthActiveMin",0));return n;
    }
    /** Today's steps taken outside Kadamm sessions: everyday walking around. */
    static long stepsOutsideSessionsToday(Context c){return Math.max(0,todayTotal(c)-sessionOn(c,todayKey()));}
    /** Kilometres for a number of everyday walking steps, from the configured stride length. */
    static double stepKm(Context c,long steps){return steps*c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE).getInt("strideLengthCm",72)/100000d;}
    /** Session distance today, including the session in progress. */
    static double sessionKmToday(Context c){
        double km=0;String today=todayKey();JSONArray all=sessions(c);
        for(int i=0;i<all.length();i++){JSONObject s=all.optJSONObject(i);if(s!=null&&today.equals(s.optString("day","")))km+=s.optDouble("distanceKm",0);}
        SharedPreferences live=c.getSharedPreferences("stride_state",Context.MODE_PRIVATE);if(live.getBoolean("active",false))km+=live.getFloat("distance",0);
        return km;
    }
    /**
     * Today's whole-day distance as {km, estimated (1/0)}. Health Connect's measured distance when it has one,
     * never less than the sessions; otherwise sessions plus everyday steps at your stride length, marked estimated.
     */
    static double[] distanceToday(Context c){
        double sessions=sessionKmToday(c);SharedPreferences h=c.getSharedPreferences("kadamm_health",Context.MODE_PRIVATE);
        if(healthImportedToday(c)>=0&&h.contains("healthDistanceM")){double hc=h.getFloat("healthDistanceM",0)/1000d;if(hc>=sessions)return new double[]{hc,0};}
        long outside=stepsOutsideSessionsToday(c);
        return new double[]{sessions+stepKm(c,outside),outside>0?1:0};
    }
    /**
     * Today's active calories: what moving burned, without the resting metabolism that Health Connect's
     * "total calories" includes (that figure is over 1,000 kcal by midday even sitting still). Sessions use their
     * MET estimate; everyday steps cost about 0.5 kcal per kg per km of walking, the same net rate {@link Calories}
     * gives a 5 km/h walk. Health Connect's active-calories figure is used when it is higher.
     */
    static long activeKcalToday(Context c){
        double est=sessionCaloriesToday(c)+stepKm(c,stepsOutsideSessionsToday(c))*Calories.weightKg(c)*.5;
        SharedPreferences h=c.getSharedPreferences("kadamm_health",Context.MODE_PRIVATE);
        if(healthImportedToday(c)>=0&&h.contains("healthActiveKcal"))est=Math.max(est,h.getFloat("healthActiveKcal",0));
        return Math.round(est);
    }
    /** "phone", "health", "sessions" or "none": where today's figure comes from. */
    static String todaySource(Context c){
        String today=todayKey();long live=healthLiveToday(c),phone=phoneOn(c,today);
        if(live>=0&&live>=phone)return "health";
        if(ready(c)&&startedAt(c)>0)return "phone";
        if(live>=0)return "health";
        return sessionOn(c,today)>0?"sessions":"none";
    }
    static boolean hasTodayData(Context c){return !"none".equals(todaySource(c));}

    /** Every day Kadamm knows a step total for. Built once per screen so charts and streaks agree. */
    static Map<String,Long> allDayTotals(Context c){
        HashMap<String,Long> out=new HashMap<>();
        merge(out,c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString("days","{}"));
        if(hcSteps(c))merge(out,c.getSharedPreferences("kadamm_health",Context.MODE_PRIVATE).getString("healthDays","{}"));
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
        SharedPreferences st=c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE);
        if(n>=goal){
            if(today.equals(p.getString("goalNotified","")))return;
            if(!st.getBoolean("notifyGoal",true)){p.edit().putString("goalNotified",today).putString("progressNotified",today).apply();return;}
            p.edit().putString("goalNotified",today).putString("progressNotified",today).apply();
            String[] m=Messages.pick(c,"goal",Messages.GOAL,n);if(m==null)return;title=m[0];text=m[1];
        }else if(n>=Math.ceil(goal*.6)){
            if(today.equals(p.getString("progressNotified","")))return;
            if(!st.getBoolean("notifyProgress",true)){p.edit().putString("progressNotified",today).apply();return;}
            p.edit().putString("progressNotified",today).apply();
            String[] m=Messages.pick(c,"progress",Messages.PROGRESS,n,goal-n);if(m==null)return;title=m[0];text=m[1];
        }else return;
        NotificationManager nm=c.getSystemService(NotificationManager.class);
        if(nm==null)return;
        if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(GOAL_CHANNEL,"Daily goal",NotificationManager.IMPORTANCE_DEFAULT));
        nm.notify(GOAL_NOTIFICATION,Reminders.rich(c,GOAL_CHANNEL,GOAL_NOTIFICATION,title,text,"Today",true).build());
    }
}
