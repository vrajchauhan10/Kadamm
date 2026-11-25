package com.stride.run;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Watch measurements (sleep, resting heart rate, VO2max, from any watch that shares into Health Connect), fitness
 * age, and records over time. Only measured data is shown; anything derived is labelled as an estimate.
 */
final class Vitals {
    private Vitals() {}

    /** Everything Health Connect shared (see WatchHealth.kt): {sleep, sleepDetail, rhr, hrv, resp, spo2, …, sources}. Watch
     *  measurements are kept whatever the step source, since they don't depend on steps. */
    private static String cachedRaw;private static JSONObject cached;
    static JSONObject all(Context c){
        // Parsed once per change: the Home and Health data screens ask for it many times per render.
        String raw=c.getSharedPreferences("kadamm_health",Context.MODE_PRIVATE).getString("vitals","{}");
        synchronized(Vitals.class){if(raw.equals(cachedRaw)&&cached!=null)return cached;}
        try{JSONObject o=new JSONObject(raw);synchronized(Vitals.class){cachedRaw=raw;cached=o;}return o;}catch(Exception e){return new JSONObject();}
    }
    static JSONObject allUncached(Context c){
        try{return new JSONObject(c.getSharedPreferences("kadamm_health",Context.MODE_PRIVATE).getString("vitals","{}"));}catch(Exception e){return new JSONObject();}
    }
    static TreeMap<String,Double> series(JSONObject v,String name){TreeMap<String,Double> t=new TreeMap<>();JSONObject o=v.optJSONObject(name);if(o==null)return t;for(Iterator<String> it=o.keys();it.hasNext();){String k=it.next();t.put(k,o.optDouble(k));}return t;}
    static String sources(JSONObject v){JSONArray a=v.optJSONArray("sources");if(a==null||a.length()==0)return "";List<String> n=new ArrayList<>();for(int i=0;i<a.length();i++){String l=HealthSync.label(a.optString(i));if(!n.contains(l))n.add(l);}return String.join(", ",n);}
    static double median(List<Double> x){if(x.isEmpty())return -1;double[] a=new double[x.size()];for(int i=0;i<a.length;i++)a[i]=x.get(i);Arrays.sort(a);return a.length%2==1?a[a.length/2]:(a[a.length/2-1]+a[a.length/2])/2;}

    // ---- Fitness age ----
    static final class FitnessAge { double vo2;boolean fromWatch;int age,fitAge,sessions;String need,method=""; }
    /** Average VO2max for an age (HUNT fitness study, approximated as a straight line per sex; the mean of both when sex isn't set). */
    static double norm(double age,String sex){double m=62.5-.36*age,f=50.5-.28*age;return "m".equals(sex)?m:"f".equals(sex)?f:(m+f)/2;}
    /** Fitness age from the app's one VO2max ({@link Pro#vo2}), so every screen shows the same VO2max. */
    static FitnessAge fitnessAge(Context c,JSONArray hist){
        FitnessAge f=new FitnessAge();SharedPreferences s=c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE);f.age=s.getInt("ageYears",0);String sex=s.getString("sex","");
        if(f.age<=0){f.need="age";return f;}
        Pro.Vo2 v=Pro.vo2(c,hist);if(v.value<=0){FitnessAge w=walkingCheck(c,hist);f.need=w.need;f.sessions=w.sessions;return f;}
        f.vo2=v.value;f.fromWatch=v.measured;f.method=v.method;
        // The age whose average VO2max equals yours.
        double m="m".equals(sex)?.36:"f".equals(sex)?.28:.32,b="m".equals(sex)?62.5:"f".equals(sex)?50.5:56.5;
        f.fitAge=(int)Math.round(Math.max(20,Math.min(90,(b-f.vo2)/m)));return f;
    }
    /**
     * VO2max from your own walks with heart rate: oxygen cost of walking at your speed (ACSM: 0.1 × m/min + 3.5),
     * scaled to your heart-rate reserve (VO2max ≈ VO2 × (max − rest) / (avg − rest)). Needs a measured resting heart
     * rate and 2+ walks of 10+ minutes in 60 days; null otherwise.
     */
    static FitnessAge walkingEstimate(Context c,JSONArray hist){FitnessAge f=walkingCheck(c,hist);return f.need==null?f:null;}
    private static FitnessAge walkingCheck(Context c,JSONArray hist){
        FitnessAge f=new FitnessAge();JSONObject v=all(c);
        List<Double> rhr=new ArrayList<>(Pro.restSeries(c,v).values());double rest=median(rhr);int max=SessionMetrics.maxHeartRate(c);
        if(max<=0){f.need="age";return f;}if(rest<=0){f.need="rest";return f;}
        List<Double> est=new ArrayList<>();String from=DaySteps.key(System.currentTimeMillis()-60L*86400000L);
        for(int i=0;i<hist.length();i++){JSONObject o=hist.optJSONObject(i);if(o==null||!DaySummary.counts(o)||o.optString("day","").compareTo(from)<0)continue;SessionMetrics m=SessionMetrics.of(c,o);
            if(m.hrAvg<=0||m.estimatedDistance||m.movingS<600||m.run)continue;double km=o.optDouble("distanceKm",0);if(km<.8)continue;
            double mpm=km*1000/(m.movingS/60.0),vo=.1*mpm+3.5,frac=(m.hrAvg-rest)/(max-rest);if(frac<.4||frac>.95)continue;est.add(vo/frac);}
        f.sessions=est.size();if(est.size()<2){f.need="walks";return f;}
        f.vo2=median(est);return f;
    }

    // ---- Sleep and steps ----
    /** Sleep after active days against sleep after quiet days, in minutes; null without 8+ nights. */
    static double[] sleepVsSteps(Context c){
        TreeMap<String,Double> sleep=series(all(c),"sleep");if(sleep.size()<8)return null;Map<String,Long> totals=DaySteps.allDayTotals(c);
        List<double[]> pairs=new ArrayList<>();java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US);
        for(Map.Entry<String,Double> e:sleep.entrySet()){try{Calendar d=Calendar.getInstance();d.setTime(f.parse(e.getKey()));d.add(Calendar.DAY_OF_YEAR,-1);Long st=totals.get(DaySteps.key(d.getTimeInMillis()));if(st!=null&&st>500)pairs.add(new double[]{st,e.getValue()});}catch(Exception ignored){}}
        if(pairs.size()<8)return null;List<Double> st=new ArrayList<>();for(double[] p:pairs)st.add(p[0]);double med=median(st);
        double hi=0,lo=0;int nh=0,nl=0;for(double[] p:pairs){if(p[0]>=med){hi+=p[1];nh++;}else{lo+=p[1];nl++;}}
        if(nh<3||nl<3)return null;return new double[]{hi/nh,lo/nl,med,pairs.size()};
    }

    // ---- Records over time ----
    static final class Month { String key,label;long bestDay;double longestKm,bestPaceS=-1;int sessions; }
    /** The last 6 months: biggest day, longest walk and fastest pace (sessions of 1 km+ with measured distance). */
    static List<Month> records(Context c,JSONArray hist){
        TreeMap<String,Month> map=new TreeMap<>();Calendar d=Calendar.getInstance();String[] mn={"Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};
        for(int i=0;i<6;i++){Month m=new Month();m.key=String.format(Locale.US,"%04d-%02d",d.get(Calendar.YEAR),d.get(Calendar.MONTH)+1);m.label=mn[d.get(Calendar.MONTH)];map.put(m.key,m);d.add(Calendar.MONTH,-1);}
        for(Map.Entry<String,Long> e:DaySteps.allDayTotals(c).entrySet()){Month m=map.get(e.getKey().length()>=7?e.getKey().substring(0,7):"");if(m!=null&&e.getValue()>m.bestDay)m.bestDay=e.getValue();}
        for(int i=0;i<hist.length();i++){JSONObject o=hist.optJSONObject(i);if(o==null||!DaySummary.counts(o))continue;Month m=map.get(o.optString("day","").length()>=7?o.optString("day","").substring(0,7):"");if(m==null)continue;m.sessions++;
            double km=o.optDouble("distanceKm",0);if(o.optBoolean("distanceEstimated",false))continue;if(km>m.longestKm)m.longestKm=km;
            if(km>=1&&SessionMetrics.ready()){SessionMetrics x=SessionMetrics.of(c,o);if(x.movingS>0){double p=x.movingS/km;if(p>150&&p<1500&&(m.bestPaceS<0||p<m.bestPaceS))m.bestPaceS=p;}}}
        return new ArrayList<>(map.values());
    }
}
