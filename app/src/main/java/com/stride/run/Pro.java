package com.stride.run;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The body metrics of Kadamm 10: recovery, strain, stress, movement load, one VO2max for the whole app, resting
 * heart rate and HRV with their sources. Each metric has exactly one definition here, so the same name shows the same
 * number on every screen. Measured values (from a watch or your own entry) always win over estimates, and estimates
 * say so.
 */
final class Pro {
    private Pro() {}

    // ---- Sources ----
    static final class Reading { double value=-1;String day="",source="";boolean estimate;long at; boolean ok(){return value>0;} }
    private static String yesterday(){return DaySteps.key(System.currentTimeMillis()-86400000L);}
    /** The newest value of a daily series no older than {@code maxDays}, with the app it came from. */
    static Reading latest(JSONObject v,String key,int maxDays){
        Reading r=new Reading();TreeMap<String,Double> s=Vitals.series(v,key);if(s.isEmpty())return r;
        Map.Entry<String,Double> e=s.lastEntry();if(e.getKey().compareTo(DaySteps.key(System.currentTimeMillis()-maxDays*86400000L))<0)return r;
        r.value=e.getValue();r.day=e.getKey();r.source=sourceOf(v,key);return r;
    }
    static String sourceOf(JSONObject v,String key){
        JSONObject by=v.optJSONObject("by");JSONArray a=by==null?null:by.optJSONArray(key);if(a==null||a.length()==0)return "Health Connect";
        List<String> n=new ArrayList<>();for(int i=0;i<a.length();i++){String l=HealthSync.label(a.optString(i));if(!n.contains(l))n.add(l);}return String.join(", ",n);
    }
    /** Median and spread of a series over the 30 days before {@code beforeDay}; null with fewer than {@code min} days. */
    static double[] baseline(TreeMap<String,Double> s,String beforeDay,int min,boolean log){
        List<Double> v=new ArrayList<>();String from=DaySteps.key(System.currentTimeMillis()-31L*86400000L);
        for(Map.Entry<String,Double> e:s.entrySet())if(e.getKey().compareTo(beforeDay)<0&&e.getKey().compareTo(from)>=0)v.add(log?Math.log(e.getValue()):e.getValue());
        if(v.size()<min)return null;double m=0;for(double x:v)m+=x;m/=v.size();double sd=0;for(double x:v)sd+=(x-m)*(x-m);sd=Math.sqrt(sd/v.size());return new double[]{m,sd,v.size()};
    }

    // ---- Resting heart rate and HRV ----
    /** Resting heart rate series: the watch's own figure, else the lowest 30-minute average of your heart rate. */
    static TreeMap<String,Double> restSeries(Context c,JSONObject v){
        TreeMap<String,Double> t=new TreeMap<>(Vitals.series(v,"hrRest"));t.putAll(Vitals.series(v,"rhr"));return t;
    }
    static Reading restingHr(Context c){
        JSONObject v=Vitals.all(c);Reading r=latest(v,"rhr",2);if(r.ok())return r;
        Reading low=latest(v,"hrRest",2);if(low.ok()){low.source="Lowest 30 min of your watch's heart rate";return low;}
        return r;
    }
    static TreeMap<String,Double> hrvSeries(Context c,JSONObject v){TreeMap<String,Double> t=new TreeMap<>(Vitals.series(v,"hrv"));return t;}
    static Reading hrv(Context c){
        JSONObject v=Vitals.all(c);Reading r=latest(v,"hrv",2);if(r.ok()){JSONObject n=v.optJSONObject("hrvNight");if(n!=null&&n.optBoolean(r.day))r.source+=" · while asleep";return r;}
        return r;
    }

    // ---- Sleep need ----
    static final class SleepNeed { int base,strain,debt,total;String basis; }
    /**
     * Tonight's sleep need, in minutes, built like WHOOP's: a base for your age (9 h under 18, 8 h from 18 to 64 (the
     * middle of the recommended 7–9 h), 7 h 30 m from 65, from the US National Sleep Foundation's ranges), plus 6 minutes
     * for every point of yesterday's Strain (or Movement load) above 10 (at most 45), plus a seventh of the sleep you
     * missed against the base over the last 7 nights (at most 60), so a short week is paid back gradually.
     */
    private static final java.util.Map<String,Object[]> needCache=new java.util.HashMap<>();
    static SleepNeed sleepNeed(Context c,JSONArray hist,String forDay){
        String k=forDay+"|"+Vitals.all(c).optLong("at");synchronized(needCache){Object[] hit=needCache.get(k);if(hit!=null&&System.currentTimeMillis()-(long)hit[0]<60000)return (SleepNeed)hit[1];}
        SleepNeed n=sleepNeedNow(c,hist,forDay);synchronized(needCache){needCache.put(k,new Object[]{System.currentTimeMillis(),n});}return n;
    }
    private static SleepNeed sleepNeedNow(Context c,JSONArray hist,String forDay){
        SleepNeed n=new SleepNeed();int age=c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE).getInt("ageYears",0);
        n.base=age<=0?480:age<18?540:age>=65?450:480;n.basis=age<=0?"8 h (the middle of 7–9 h; add your age to fit it)":age<18?"9 h for your age":age>=65?"7 h 30 m for your age":"8 h for your age";
        try{Calendar d=Calendar.getInstance();d.setTime(new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(forDay));d.add(Calendar.DAY_OF_YEAR,-1);String y=DaySteps.key(d.getTimeInMillis());
            Strain st=strain(c,y);double load=st.strain>=0?st.strain:load(c,hist,y).load;if(load>10)n.strain=(int)Math.min(45,Math.round((load-10)*6));
            int missed=0;Calendar k=(Calendar)d.clone();for(int i=0;i<7;i++){SleepTracker.Night x=SleepTracker.night(c,DaySteps.key(k.getTimeInMillis()));if(x!=null&&x.asleep>=180)missed+=Math.max(0,n.base-x.asleep);k.add(Calendar.DAY_OF_YEAR,-1);}
            n.debt=Math.min(60,missed/7);}catch(Exception ignored){}
        n.total=n.base+n.strain+n.debt;return n;
    }

    // ---- Recovery ----
    static final class Recovery { int score=-1;String need="";final List<String[]> parts=new ArrayList<>(); String zone(){return score>=67?"Green":score>=34?"Yellow":"Red";} }
    /**
     * Recovery (0–100 %), WHOOP-style but from open data: last night's HRV against your 30-day normal (45 %), resting
     * heart rate against normal (25 %), sleep against your sleep need (25 %) and breathing rate against normal (5 %).
     * HRV and resting heart rate count by how many standard deviations they sit from your own normal: one SD better
     * than usual adds 25 points to that part. Parts with no data are left out and the rest re-weighted; it needs HRV or
     * resting heart rate with 5+ days of history, plus one more part.
     */
    static Recovery recovery(Context c){
        Recovery r=new Recovery();JSONObject v=Vitals.all(c);String today=DaySteps.todayKey();double wsum=0,sum=0;int core=0;
        Reading h=hrv(c);double[] hb=baseline(hrvSeries(c,v),h.day.isEmpty()?today:h.day,5,true);
        if(h.ok()&&hb!=null){double z=(Math.log(h.value)-hb[0])/Math.max(.05,hb[1]);double p=clamp(50+25*z);sum+=.45*p;wsum+=.45;core++;
            r.parts.add(new String[]{"HRV",String.format(Locale.US,"%.0f ms",h.value)+(h.day.equals(today)?"":" (yesterday)"),String.format(Locale.US,"normal %.0f ms",Math.exp(hb[0])),String.valueOf(Math.round(p))});}
        Reading rh=restingHr(c);double[] rb=baseline(restSeries(c,v),rh.day.isEmpty()?today:rh.day,5,false);
        if(rh.ok()&&rb!=null){double z=(rh.value-rb[0])/Math.max(1.5,rb[1]);double p=clamp(50-25*z);sum+=.25*p;wsum+=.25;core++;
            r.parts.add(new String[]{"Resting heart rate",Math.round(rh.value)+" bpm"+(rh.day.equals(today)?"":" (yesterday)"),String.format(Locale.US,"normal %.0f bpm",rb[0]),String.valueOf(Math.round(p))});}
        SleepTracker.Night n=SleepTracker.night(c,today);
        if(n!=null){int need=sleepNeed(c,DaySteps.sessions(c),today).total;double p=clamp(n.asleep*100.0/need);sum+=.25*p;wsum+=.25;r.parts.add(new String[]{"Sleep",hm(n.asleep),"need "+hm(need),String.valueOf(Math.round(p))});}
        Reading rs=latest(v,"resp",1);double[] rsb=baseline(Vitals.series(v,"resp"),rs.day.isEmpty()?today:rs.day,5,false);
        if(rs.ok()&&rsb!=null){double d=rs.value-rsb[0];double p=d<.5?100:clamp(100-(d-.5)*40);sum+=.05*p;wsum+=.05;r.parts.add(new String[]{"Breathing rate",String.format(Locale.US,"%.1f /min",rs.value),String.format(Locale.US,"normal %.1f",rsb[0]),String.valueOf(Math.round(p))});}
        if(core==0){r.need=h.ok()||rh.ok()?"history":"hr";return r;}
        if(r.parts.size()<2){r.need="more";return r;}
        r.score=(int)Math.round(sum/wsum);return r;
    }
    static final class Now { int morning=-1,now=-1;double strainDrain,stressDrain,load=-1;String loadName=""; }
    /**
     * Recovery now: this morning's Recovery, lowered through the day by what you've spent: 2 points for every point of
     * today's Strain (Movement load without a watch), and up to 15 points for stress, in proportion to how high your
     * Stress has been and how much of the day it covers (stressed for 8+ hours counts in full). It's recalculated with
     * every sync, so it moves as the day's heart rate, steps and stress arrive. Morning Recovery itself only changes when
     * last night's data does.
     */
    static Now recoveryNow(Context c){
        Now n=new Now();Recovery r=recovery(c);n.morning=r.score;if(r.score<0)return n;String today=DaySteps.todayKey();
        Strain st=strain(c,today);if(st.strain>=0){n.load=st.strain;n.loadName="Strain";}else{Load l=load(c,DaySteps.sessions(c),today);if(l.load>=0){n.load=l.load;n.loadName="Movement load";}}
        if(n.load>0)n.strainDrain=n.load*2;
        JSONObject ss=stress(c,today);if(ss!=null){int sc=ss.optInt("score");double hours=ss.optInt("min")/60.0;if(sc>25)n.stressDrain=Math.min(15,(sc-25)/75.0*15*Math.min(1,hours/8));}
        n.now=(int)Math.round(clamp(r.score-n.strainDrain-n.stressDrain));return n;
    }
    static double clamp(double x){return Math.max(0,Math.min(100,x));}
    static String hm(long min){return String.format(Locale.US,"%dh %02dm",min/60,min%60);}

    // ---- Heart rate: strain and zones ----
    static final class Strain { double strain=-1,trimp;int minutes;long[] zoneMin=new long[5];String need="";int rest,max; }
    /** Minutes at each heart rate (5-bpm bins from 30) for a day: Health Connect's, else Kadamm's own watch readings. */
    static int[] histogram(Context c,JSONObject v,String day){
        JSONObject h=v.optJSONObject("hrHist");JSONArray a=h==null?null:h.optJSONArray(day);
        int[] out=new int[WatchHealth.BINS];int n=0;if(a!=null)for(int i=0;i<Math.min(a.length(),out.length);i++){out[i]=a.optInt(i);n+=out[i];}
        if(n>0)return out;
        for(int bpm:WearBridge.minutes(c,day)){out[Math.max(0,Math.min(WatchHealth.BINS-1,(bpm-WatchHealth.BIN0)/WatchHealth.BIN))]++;n++;}
        return n>0?out:null;
    }
    /**
     * Strain (0–21) from heart rate, like WHOOP's: Banister's training impulse (TRIMP) summed over every minute of the
     * day above 20 % of your heart-rate reserve (each minute weighted by reserve × 0.64e^(1.92 × reserve), 0.86e^(1.67 ×
     * reserve) for women), then 21 × (1 − e^(−TRIMP/120)) so it grows ever more slowly: a quiet day is 4–7, an hour's
     * brisk walking about 10, a hard run 14+. Needs a resting heart rate and your age (or a set maximum heart rate).
     */
    static Strain strain(Context c,String day){
        Strain s=new Strain();JSONObject v=Vitals.all(c);int max=SessionMetrics.maxHeartRate(c);
        TreeMap<String,Double> rs=restSeries(c,v);double rest=-1;
        for(Map.Entry<String,Double> e:rs.descendingMap().entrySet())if(e.getKey().compareTo(day)<=0){rest=e.getValue();break;}
        int[] h=histogram(c,v,day);if(h==null){s.need="hr";return s;}if(max<=0){s.need="age";return s;}if(rest<=0){s.need="rest";return s;}
        s.rest=(int)Math.round(rest);s.max=max;boolean f="f".equals(c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE).getString("sex",""));
        for(int i=0;i<h.length;i++){if(h[i]==0)continue;double bpm=WatchHealth.BIN0+i*WatchHealth.BIN+WatchHealth.BIN/2.0;double x=(bpm-rest)/(max-rest);s.minutes+=h[i];
            if(x>=.5)s.zoneMin[Math.min(4,(int)((x-.5)/.1))]+=h[i];
            if(x<.2)continue;x=Math.min(1,x);s.trimp+=h[i]*x*(f?.86*Math.exp(1.67*x):.64*Math.exp(1.92*x));}
        s.strain=Math.round(21*(1-Math.exp(-s.trimp/120))*10)/10.0;return s;
    }

    // ---- Stress ----
    /** Stress (0–100) from heart rate while still, worked out in WatchHealth.stressDay; null without enough readings. */
    static JSONObject stress(Context c,String day){JSONObject s=Vitals.all(c).optJSONObject("stress");return s==null?null:s.optJSONObject(day);}
    static String stressLevel(int s){return s<=25?"Rest":s<=50?"Low":s<=75?"Medium":"High";}

    // ---- Movement load (phone only) ----
    static final class Load { double load=-1,points;String basis="";int brisk,easy; }
    /**
     * Movement load (0–21) from your steps alone, for days without a watch. Points: per minute, 0.2 for slow steps
     * (20–59 a minute), 0.6 for walking (60–99), 1.5 for brisk walking (100–129) and 2.5 for running pace (130+), from
     * Health Connect's minute-by-minute steps; without those, your sessions' effort points (the same scale) plus 0.3 for
     * every 100 everyday steps. Load = 21 × (1 − e^(−points/110)): a quiet day is 2–5, 10,000 steps with a brisk walk
     * about 11, a 20,000-step day 16+.
     */
    static Load load(Context c,JSONArray hist,String day){
        Load l=new Load();JSONObject md=DaySteps.minuteDays(c).optJSONObject(day);JSONArray b=md==null?null:md.optJSONArray("b");
        if(b!=null&&b.length()>=4){l.points=b.optInt(0)*.2+b.optInt(1)*.6+b.optInt(2)*1.5+b.optInt(3)*2.5;l.brisk=b.optInt(2)+b.optInt(3);l.easy=b.optInt(1);l.basis="minute-by-minute steps";}
        else{long inSessions=0;for(int i=0;i<hist.length();i++){JSONObject o=hist.optJSONObject(i);if(o==null||!day.equals(o.optString("day",""))||!DaySummary.counts(o))continue;SessionMetrics m=SessionMetrics.of(c,o);l.points+=m.effort;l.brisk+=(int)Math.round(m.intensityMin);if(o.optBoolean("stepsAvailable",false))inSessions+=o.optLong("steps",0);}
            Long all=DaySteps.allDayTotals(c).get(day);long outside=Math.max(0,(all==null?0:all)-inSessions);l.points+=outside/100.0*.3;l.basis="sessions and everyday steps";
            if(all==null&&l.points==0)return l;}
        l.load=Math.round(21*(1-Math.exp(-l.points/110))*10)/10.0;return l;
    }

    // ---- VO2max: one figure for the whole app ----
    static final class Vo2 { double value=-1;String method="",detail="",day="";boolean measured; }
    /**
     * VO2max from the best evidence available, in this order: measured by your watch (last 90 days); a Cooper test
     * (12-minute run, last 120 days: (metres − 504.9) / 44.73); from heart rate (Uth–Sørensen: 15.3 × max HR / resting
     * HR); from walks with heart rate (ACSM walking cost scaled by heart-rate reserve). Fitness age uses this same value.
     */
    static Vo2 vo2(Context c,JSONArray hist){
        Vo2 r=new Vo2();JSONObject v=Vitals.all(c);
        Reading w=latest(v,"vo2",90);if(w.ok()){r.value=w.value;r.day=w.day;r.measured=true;r.method="Measured by your watch";r.detail=w.source;return r;}
        Cooper ct=cooper(c,hist,120,true);if(ct!=null){r.value=ct.vo2;r.day=ct.day;r.method="Cooper 12-minute test";r.detail=String.format(Locale.US,"%,d m in 12 minutes",Math.round(ct.metres));return r;}
        int max=SessionMetrics.maxHeartRate(c);TreeMap<String,Double> rs=restSeries(c,v);
        if(max>0&&rs.size()>=3){List<Double> last=new ArrayList<>(rs.descendingMap().values()).subList(0,Math.min(7,rs.size()));double rest=Vitals.median(last);
            if(rest>=35&&rest<=100){r.value=Math.round(15.3*max/rest*10)/10.0;r.day=rs.lastKey();r.method="From your heart rate";r.detail=String.format(Locale.US,"15.3 × max %d ÷ resting %.0f bpm",max,rest);return r;}}
        Vitals.FitnessAge fa=Vitals.walkingEstimate(c,hist);if(fa!=null&&fa.vo2>0){r.value=Math.round(fa.vo2*10)/10.0;r.method="Estimated from walks with heart rate";r.detail=fa.sessions+" walks";return r;}
        return r;
    }
    static final class Cooper { double metres,vo2;String day;boolean run;long id; }
    /** Your best 12 minutes in the last {@code days} days; {@code testOnly} keeps runs and sessions started as a test. */
    static Cooper cooper(Context c,JSONArray hist,int days,boolean testOnly){
        Cooper best=null;String from=DaySteps.key(System.currentTimeMillis()-days*86400000L);
        for(int i=0;i<hist.length();i++){JSONObject o=hist.optJSONObject(i);if(o==null||o.optString("day","").compareTo(from)<0)continue;boolean test=o.optBoolean("cooperTest",false);
            // Only GPS sessions of 12+ minutes can hold a Cooper distance; skip the rest before the (cached but costly) analysis.
            if(o.optLong("durationMs",0)<720000||"indoor".equals(o.optString("activityMode"))||o.optString("route","").length()<200)continue;
            if(testOnly&&!test&&!"Run".equals(o.optString("type")))continue;if(!SessionMetrics.ready()&&!test)continue;SessionMetrics m=SessionMetrics.of(c,o);
            if(m.cooperM<=0||(testOnly&&!test&&!m.run))continue;if(best==null||m.cooperM>best.metres){best=new Cooper();best.metres=m.cooperM;best.day=o.optString("day");best.run=m.run||test;best.id=o.optLong("id");}}
        if(best!=null)best.vo2=Math.round((best.metres-504.9)/44.73*10)/10.0;
        return best!=null&&best.vo2>=10?best:null;
    }

    // ---- What the watch's app can share ----
    /** The Health Connect type a measure needs written by the watch's app. */
    private static final String[][] WRITES={{"floors","FLOORS_CLIMBED"},{"sleep","SLEEP"},{"hr","HEART_RATE"},{"rhr","RESTING_HEART_RATE"},{"hrv","HEART_RATE_VARIABILITY"},{"resp","RESPIRATORY_RATE"},
        {"spo2","OXYGEN_SATURATION"},{"skin","SKIN_TEMPERATURE"},{"btemp","BODY_TEMPERATURE"},{"bp","BLOOD_PRESSURE"},{"vo2","VO2_MAX"},{"weight","WEIGHT"}};
    /**
     * Whether any app that shares into Health Connect on this phone (the watch's app: Zepp, Samsung Health, Fitbit,
     * Garmin…) is allowed to write this measure: TRUE yes, FALSE none of them can (so it can't arrive), null when no
     * such app is known. Read from Android's own record of each app's granted Health Connect permissions.
     */
    private static final java.util.Map<String,Object[]> shareCache=new java.util.HashMap<>();
    static Boolean canShare(Context c,String key){
        synchronized(shareCache){Object[] hit=shareCache.get(key);if(hit!=null&&System.currentTimeMillis()-(long)hit[0]<60000)return (Boolean)hit[1];}
        Boolean r=canShareNow(c,key);synchronized(shareCache){shareCache.put(key,new Object[]{System.currentTimeMillis(),r});}return r;
    }
    private static Boolean canShareNow(Context c,String key){
        String type=null;for(String[] w:WRITES)if(w[0].equals(key))type=w[1];if(type==null)return null;
        java.util.Set<String> pkgs=new java.util.TreeSet<>();JSONArray a=Vitals.all(c).optJSONArray("sources");if(a!=null)for(int i=0;i<a.length();i++)pkgs.add(a.optString(i));
        for(String p:c.getSharedPreferences("kadamm_health",Context.MODE_PRIVATE).getString("healthSources","").split(","))if(!p.trim().isEmpty())pkgs.add(p.trim());
        pkgs.remove(c.getPackageName());boolean any=false;
        // Google Fit only relays other apps' data, so what it may write says nothing about the watch.
        for(String p:pkgs){if(p.startsWith("com.android.healthconnect")||p.equals("com.google.android.apps.fitness"))continue;
            try{android.content.pm.PackageInfo pi=c.getPackageManager().getPackageInfo(p,android.content.pm.PackageManager.GET_PERMISSIONS);if(pi.requestedPermissions==null)continue;any=true;
                for(int i=0;i<pi.requestedPermissions.length;i++)if(("android.permission.health.WRITE_"+type).equals(pi.requestedPermissions[i])&&(pi.requestedPermissionsFlags[i]&android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED)!=0)return true;}catch(Exception ignored){}}
        return any?Boolean.FALSE:null;
    }
    /** The watch apps that share, by name ("Zepp, Google Fit"). */
    static String sharingApps(Context c){java.util.List<String> n=new ArrayList<>();JSONArray a=Vitals.all(c).optJSONArray("sources");if(a!=null)for(int i=0;i<a.length();i++){String p=a.optString(i);if(p.startsWith("com.android.healthconnect")||p.equals("com.google.android.apps.fitness")||p.equals(c.getPackageName()))continue;String l=HealthSync.label(p);if(!n.contains(l))n.add(l);}return String.join(", ",n);}

    // ---- Floors ----
    /**
     * Floors climbed on a day: {floors, source} with source 1 = Health Connect (the phone's or watch's own count),
     * 2 = Kadamm sessions recorded with the barometer (metres climbed ÷ 3, a standard floor). The higher of the two,
     * never their sum; {-1, 0} when neither has anything for the day.
     */
    static double[] floors(Context c,JSONArray hist,String day){
        double hc=-1;Double v=Vitals.series(Vitals.all(c),"floors").get(day);if(v!=null)hc=v;
        double gain=0;boolean any=false;
        for(int i=0;i<hist.length();i++){JSONObject o=hist.optJSONObject(i);if(o==null||!day.equals(o.optString("day",""))||Insights.vehicle(o))continue;SessionMetrics m=SessionMetrics.of(c,o);if(m.barometer&&m.gain>=0){gain+=m.gain;any=true;}}
        double ses=any?Math.floor(gain/3.0):-1;
        if(hc<0&&ses<0)return new double[]{-1,0};return hc>=ses?new double[]{hc,1}:new double[]{ses,2};
    }

    // ---- Which watch ----
    /** The apps and watches Health Connect data came from, and when it last synced. */
    static String connectedSources(Context c){return Vitals.sources(Vitals.all(c));}
    static long lastSync(Context c){SharedPreferences p=c.getSharedPreferences("kadamm_health",Context.MODE_PRIVATE);return Math.max(p.getLong("vitalsCheckedAt",0),p.getLong("healthSyncedAt",0));}
    static int count(JSONObject v,String key){JSONObject cn=v.optJSONObject("counts");return cn==null?0:cn.optInt(key);}
    static boolean granted(JSONObject v,String key){JSONArray g=v.optJSONArray("granted");if(g==null)return false;for(int i=0;i<g.length();i++)if(key.equals(g.optString(i)))return true;return false;}
    static Iterator<String> keys(JSONObject o){return o==null?new ArrayList<String>().iterator():o.keys();}
}
