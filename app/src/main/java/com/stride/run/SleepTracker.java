package com.stride.run;

import android.Manifest;
import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;

import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.SleepSegmentEvent;
import com.google.android.gms.location.SleepSegmentRequest;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Sleep for every night, from the best source there is, in this order:
 * <ol>
 * <li>what you entered yourself (you know best),</li>
 * <li>a watch or app through Health Connect (with light, deep and REM when the watch measures them),</li>
 * <li>Google's Sleep API on this phone (it learns from motion, light and phone use; no stages),</li>
 * <li>an estimate from the longest stretch the screen stayed off overnight (needs Usage access).</li>
 * </ol>
 * Phone-based nights give duration and timing only and are always labelled as estimates.
 */
final class SleepTracker {
    private SleepTracker() {}
    private static SharedPreferences p(Context c){return c.getSharedPreferences("kadamm_sleep",Context.MODE_PRIVATE);}

    static final class Night {
        String day,source,kind;long start,end;int inBed,asleep,light=-1,deep=-1,rem=-1,awake=-1;boolean staged,estimate;JSONArray stages;
        /** Bedtime and wake time as minutes from midnight, bedtime negative before midnight (−60 = 11 pm). */
        int bedMin(){Calendar d=Calendar.getInstance();d.setTimeInMillis(start);int m=d.get(Calendar.HOUR_OF_DAY)*60+d.get(Calendar.MINUTE);return m>=12*60?m-1440:m;}
        int wakeMin(){Calendar d=Calendar.getInstance();d.setTimeInMillis(end);return d.get(Calendar.HOUR_OF_DAY)*60+d.get(Calendar.MINUTE);}
    }

    // ---- Google's Sleep API ----
    private static PendingIntent intent(Context c){
        return PendingIntent.getBroadcast(c,31,new Intent(c,Receiver.class).setAction("com.stride.run.SLEEP"),PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0));
    }
    /** Starts (or renews) sleep detection; needs the Physical activity permission Kadamm already asks for steps. */
    static void register(Context c){
        if(Build.VERSION.SDK_INT>=29&&c.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)!=PackageManager.PERMISSION_GRANTED)return;
        try{ActivityRecognition.getClient(c).requestSleepSegmentUpdates(intent(c),SleepSegmentRequest.getDefaultSleepSegmentRequest())
            .addOnSuccessListener(x->p(c).edit().putLong("apiSince",p(c).getLong("apiSince",System.currentTimeMillis())).apply());}catch(Throwable ignored){}
    }
    static boolean apiActive(Context c){return p(c).getLong("apiSince",0)>0;}
    public static class Receiver extends BroadcastReceiver {
        @Override public void onReceive(Context c,Intent i){
            if(!SleepSegmentEvent.hasEvents(i))return;
            try{JSONArray a=new JSONArray(p(c).getString("api","[]"));
                for(SleepSegmentEvent e:SleepSegmentEvent.extractEvents(i)){if(e.getStatus()!=SleepSegmentEvent.STATUS_SUCCESSFUL)continue;long s=e.getStartTimeMillis(),en=e.getEndTimeMillis();
                    boolean dup=false;for(int k=0;k<a.length();k++){JSONArray x=a.getJSONArray(k);if(x.getLong(0)==s&&x.getLong(1)==en)dup=true;}if(!dup)a.put(new JSONArray().put(s).put(en));}
                JSONArray keep=new JSONArray();long cut=System.currentTimeMillis()-40L*86400000L;for(int k=0;k<a.length();k++)if(a.getJSONArray(k).getLong(1)>cut)keep.put(a.get(k));
                p(c).edit().putString("api",keep.toString()).apply();}catch(Exception ignored){}
        }
    }

    // ---- Screen-off estimate ----
    static boolean usageAccess(Context c){
        try{AppOpsManager o=(AppOpsManager)c.getSystemService(Context.APP_OPS_SERVICE);int m=Build.VERSION.SDK_INT>=29?o.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,Process.myUid(),c.getPackageName()):o.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,Process.myUid(),c.getPackageName());return m==AppOpsManager.MODE_ALLOWED;}catch(Exception e){return false;}
    }
    /**
     * The longest stretch the screen stayed off between 7 pm and 2 pm the next day, ending on {@code day}. Glances
     * under 3 minutes (checking the time) don't break it. Kept only if it starts between 7 pm and 5 am and lasts 3 to
     * 14 hours. Returns {start, end} or null.
     */
    static long[] screenOff(Context c,Calendar day){
        if(Build.VERSION.SDK_INT<28||!usageAccess(c))return null;
        Calendar from=(Calendar)day.clone();from.set(Calendar.HOUR_OF_DAY,0);from.set(Calendar.MINUTE,0);from.set(Calendar.SECOND,0);from.set(Calendar.MILLISECOND,0);from.add(Calendar.HOUR_OF_DAY,-5);
        Calendar to=(Calendar)from.clone();to.add(Calendar.HOUR_OF_DAY,19);long now=System.currentTimeMillis();long end=Math.min(to.getTimeInMillis(),now);if(end<=from.getTimeInMillis())return null;
        try{UsageStatsManager u=(UsageStatsManager)c.getSystemService(Context.USAGE_STATS_SERVICE);UsageEvents ev=u.queryEvents(from.getTimeInMillis()-6*3600000L,end);UsageEvents.Event e=new UsageEvents.Event();
            List<long[]> off=new ArrayList<>();long offAt=-1;boolean seen=false;
            while(ev.hasNextEvent()){ev.getNextEvent(e);int t=e.getEventType();long ts=e.getTimeStamp();
                if(t==UsageEvents.Event.SCREEN_NON_INTERACTIVE){offAt=ts;seen=true;}
                else if(t==UsageEvents.Event.SCREEN_INTERACTIVE){if(offAt>0)off.add(new long[]{offAt,ts});offAt=-1;seen=true;}}
            if(!seen)return null;if(offAt>0)off.add(new long[]{offAt,end});
            // Join off-stretches separated by a glance of under 3 minutes.
            List<long[]> joined=new ArrayList<>();for(long[] o:off){if(!joined.isEmpty()&&o[0]-joined.get(joined.size()-1)[1]<3*60000L)joined.get(joined.size()-1)[1]=o[1];else joined.add(o.clone());}
            long[] best=null;for(long[] o:joined){long s=Math.max(o[0],from.getTimeInMillis()),en=Math.min(o[1],end);if(en-s<=0)continue;if(best==null||en-s>best[1]-best[0])best=new long[]{s,en};}
            if(best==null)return null;long len=best[1]-best[0];Calendar st=Calendar.getInstance();st.setTimeInMillis(best[0]);int h=st.get(Calendar.HOUR_OF_DAY);
            if(len<3*3600000L||len>14*3600000L||(h>=5&&h<19))return null;
            if(best[1]>=now-10*60000L)return null; // still asleep (or the screen is just off): wait for the morning
            return best;}catch(Exception ex){return null;}
    }

    // ---- Your own entries ----
    static void addManual(Context c,String day,long start,long end){
        try{JSONObject o=new JSONObject(p(c).getString("manual","{}"));o.put(day,new JSONObject().put("s",start).put("e",end));p(c).edit().putString("manual",o.toString()).apply();}catch(Exception ignored){}
    }
    static void removeManual(Context c,String day){try{JSONObject o=new JSONObject(p(c).getString("manual","{}"));o.remove(day);p(c).edit().putString("manual",o.toString()).apply();}catch(Exception ignored){}}

    /** The night that ended on {@code day} (yyyy-MM-dd), from the best source; null when nothing knows. */
    static Night night(Context c,String day){
        Night n=new Night();n.day=day;
        try{JSONObject m=new JSONObject(p(c).getString("manual","{}")).optJSONObject(day);
            if(m!=null){n.start=m.optLong("s");n.end=m.optLong("e");n.inBed=n.asleep=(int)((n.end-n.start)/60000);n.source="You";n.kind="manual";return n;}}catch(Exception ignored){}
        JSONObject d=Vitals.all(c).optJSONObject("sleepDetail");JSONObject x=d==null?null:d.optJSONObject(day);
        if(x!=null){n.start=x.optLong("s");n.end=x.optLong("e");n.inBed=x.optInt("inBed");n.asleep=x.optInt("asleep");n.staged=x.optBoolean("staged");
            if(n.staged){n.light=x.optInt("light");n.deep=x.optInt("deep");n.rem=x.optInt("rem");n.awake=x.optInt("awake");n.stages=x.optJSONArray("st");}
            n.source=HealthSync.label(x.optString("src"));n.kind="health";return n;}
        // Sleep API: the segments ending that day (after 2 pm the day before), the longest one.
        try{JSONArray a=new JSONArray(p(c).getString("api","[]"));long[] best=null;
            for(int k=0;k<a.length();k++){JSONArray s=a.getJSONArray(k);long st=s.getLong(0),en=s.getLong(1);if(!day.equals(DaySteps.key(en)))continue;if(best==null||en-st>best[1]-best[0])best=new long[]{st,en};}
            if(best!=null&&best[1]-best[0]>=2*3600000L){n.start=best[0];n.end=best[1];n.inBed=n.asleep=(int)((n.end-n.start)/60000);n.source="Google Sleep API";n.kind="sleepapi";n.estimate=true;return n;}}catch(Exception ignored){}
        try{Calendar cal=Calendar.getInstance();cal.setTime(new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(day));
            // Past nights don't change: their screen-off estimate is worked out once and kept.
            boolean past=day.compareTo(DaySteps.todayKey())<0;String ck="screen_"+day;long[] s;
            if(past&&p(c).contains(ck)){String v=p(c).getString(ck,"");s=v.isEmpty()?null:new long[]{Long.parseLong(v.split(",")[0]),Long.parseLong(v.split(",")[1])};}
            else{s=screenOff(c,cal);if(past&&usageAccess(c))p(c).edit().putString(ck,s==null?"":s[0]+","+s[1]).apply();}
            if(s!=null){n.start=s[0];n.end=s[1];n.inBed=n.asleep=(int)((n.end-n.start)/60000);n.source="Screen-off time";n.kind="screen";n.estimate=true;return n;}}catch(Exception ignored){}
        return null;
    }
    /** The last {@code days} nights that have data, oldest first. Screen-off estimates are cached per past day. */
    static List<Night> nights(Context c,int days){
        List<Night> out=new ArrayList<>();Calendar d=Calendar.getInstance();
        for(int i=0;i<days;i++){Night n=night(c,DaySteps.key(d.getTimeInMillis()));if(n!=null)out.add(0,n);d.add(Calendar.DAY_OF_YEAR,-1);}
        return out;
    }

    static final class Consistency { int score=-1,nights;double sdBed,sdWake;int avgBed,avgWake; }
    /**
     * Sleep consistency over the last 14 nights (5+ needed): how much bedtime and wake time vary (standard deviation,
     * minutes). 100 = the same times every night; each 1.2 minutes of average spread takes a point off, so a 30-minute
     * spread scores 75 and a 2-hour spread scores 0.
     */
    static Consistency consistency(Context c){
        Consistency r=new Consistency();List<Night> ns=nights(c,14);r.nights=ns.size();if(ns.size()<5)return r;
        double[] b=new double[ns.size()],w=new double[ns.size()];for(int i=0;i<ns.size();i++){b[i]=ns.get(i).bedMin();w[i]=ns.get(i).wakeMin();}
        r.sdBed=sd(b);r.sdWake=sd(w);r.avgBed=(int)Math.round(mean(b));r.avgWake=(int)Math.round(mean(w));
        r.score=(int)Math.round(Math.max(0,Math.min(100,100-(r.sdBed+r.sdWake)/2/1.2)));return r;
    }
    static double mean(double[] a){double s=0;for(double v:a)s+=v;return s/a.length;}
    static double sd(double[] a){double m=mean(a),s=0;for(double v:a)s+=(v-m)*(v-m);return Math.sqrt(s/a.length);}
    static String clock(int minutes){int m=((minutes%1440)+1440)%1440;int h=m/60;return String.format(Locale.US,"%d:%02d %s",h%12==0?12:h%12,m%60,h<12?"am":"pm");}
}
