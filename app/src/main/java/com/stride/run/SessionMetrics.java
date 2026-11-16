package com.stride.run;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Everything Kadamm can honestly say about one session, from what the phone recorded: GPS points (with time,
 * GPS and barometric altitude, walk/run, and session steps from 8.2), the 5-second step timeline (indoor too),
 * and heart rate when a watch wrote it to Health Connect. Nothing here is invented: a metric with no data
 * behind it stays unset (-1 or null) and the screen shows it as unavailable.
 *
 * Sources for the formulas are noted where they are used. Indoor sessions have no GPS, so their distance,
 * pace and splits come from steps × the configured stride and are flagged {@link #estimatedDistance}.
 */
final class SessionMetrics {
    /** Intensity zones, light to vigorous. Cadence bounds from CADENCE-Adults (Tudor-Locke et al., 2019): 100 spm ≈ 3 METs, 130 spm ≈ 6 METs. */
    static final String[] ZONES={"Light","Easy","Moderate","Brisk","Vigorous"};
    static final int[] CADENCE_FROM={80,100,115,130};
    /** The same zones by walking speed (km/h), via the MET bands in {@link Calories#met}. */
    static final double[] SPEED_FROM={3.2,4.0,4.8,6.4};
    /** Heart-rate zones as a share of maximum heart rate: 50, 60, 70, 80, 90 %. */
    static final double[] HR_FROM={.60,.70,.80,.90};
    /** How much a minute in each zone adds to the effort score. */
    static final double[] ZONE_WEIGHT={.2,.5,1,1.5,2.5};
    /** Best-effort distances in metres, with their labels. */
    static final int[] EFFORTS={400,1000,1609,3219,5000,10000,21097};
    static final String[] EFFORT_NAMES={"400 m","1 km","1 mile","2 miles","5 km","10 km","Half marathon"};

    final long id;
    double km,kmh=-1,paceMoving=-1,paceElapsed=-1;long elapsedS,movingS,steps;boolean stepsOk,indoor,estimatedDistance,gps,run,vehicle;
    double cadenceAvg=-1,cadenceMax=-1,strideAvgCm=-1,strideMaxCm=-1;boolean strideMeasured;
    /** {seconds, value, isRun}. */
    final List<double[]> cadence=new ArrayList<>(),stride=new ArrayList<>(),hr=new ArrayList<>();
    /** {km, seconds per km, isRun} and {km, metres}. */
    List<double[]> pace=new ArrayList<>(),elevation=new ArrayList<>();
    final long[] zoneS=new long[5];String zoneSource="";double intensityMin,effort;int stops;
    final List<Split> splits=new ArrayList<>();
    final Map<Integer,Double> best=new LinkedHashMap<>();
    double gain=-1,loss=-1,altMin=Double.NaN,altMax=Double.NaN,gapPace=-1;boolean barometer;long[] gradeS;
    double consistency=-1,firstHalfPace=-1,secondHalfPace=-1,vdot=-1;
    int hrAvg=-1,hrMax=-1,maxHr=-1;long[] hrZoneS;String hrSource;
    /** Where the steps-over-time came from: "Kadamm" (8.2+ recording), "Health Connect" (per minute) or "". */
    String stepSource="";boolean seaLevel;
    /** Fastest pace held for 1, 5, 10, 20, 30 and 60 minutes (GPS): duration seconds → seconds per km. */
    final Map<Integer,Double> bestByTime=new LinkedHashMap<>();
    static final int[] CURVE={60,300,600,1200,1800,3600};
    /** Stride variability: spread of the 30-second stride over the walk, as a % of the average. */
    double strideCv=-1;
    /** Grade-adjusted pace along the route {km, seconds per km, 0}, with the barometer only. */
    List<double[]> gap=new ArrayList<>();
    /** Map overlays {lat1, lon1, lat2, lon2, colour}: by speed (slow green → fast red) and by grade. */
    final List<double[]> paceOverlay=new ArrayList<>(),gradeOverlay=new ArrayList<>();
    /** Where each best effort finished {lat, lon}, for pinning labels on the map. */
    final Map<Integer,double[]> bestEnd=new HashMap<>();
    /** Speeds (km/h) by 30-second window while moving, for pace zones: {seconds, km/h}. */
    final List<double[]> speeds=new ArrayList<>();

    static final class Split { int index;double fraction=1,pace,cadence=-1,elev=Double.NaN;long sec;boolean fastest,slowest; }

    private SessionMetrics(long id){this.id=id;}

    // ---- Cache: sessions don't change once saved, apart from Health Connect extras ----
    private static final Map<Long,Object[]> cache=new HashMap<>();
    private static String signature(JSONObject o){return o.optString("route","").length()+"/"+o.optString("timeline","").length()+"/"+o.optString("hr","").length()+"/"+o.optString("hcMin","").length()+"/"+o.optDouble("distanceKm")+"/"+o.optLong("steps")+"/"+o.optString("activityMode");}
    static SessionMetrics of(Context c,JSONObject o){
        long id=o.optLong("id");String sig=signature(o)+"/"+settingsKey(c);
        synchronized(cache){Object[] hit=cache.get(id);if(hit!=null&&sig.equals(hit[0]))return (SessionMetrics)hit[1];}
        if(appContext==null)appContext=c.getApplicationContext();SessionMetrics m=compute(c,o);
        synchronized(cache){cache.put(id,new Object[]{sig,m});}
        return m;
    }
    private static volatile boolean warmed;
    /** True once every saved session has been analysed, so callers on the main thread can use the cache freely. */
    static boolean ready(){return warmed;}
    /** Fills the cache off the main thread, so the first look at a session or a report doesn't stall. */
    static void warm(Context c,JSONArray hist){
        Context app=c.getApplicationContext();
        Thread t=new Thread(()->{
            for(int i=0;i<hist.length();i++){JSONObject o=hist.optJSONObject(i);if(o!=null)try{of(app,o);}catch(Exception ignored){}}
            warmed=true;
            // Places and records walk every route point too; doing them here keeps Profile's first open quick.
            try{Insights.coverage(hist);Insights.records(hist,DaySteps.allDayTotals(app));}catch(Exception ignored){}
        },"KadammMetrics");t.setPriority(Thread.MIN_PRIORITY);t.start();
    }
    private static SharedPreferences settings(Context c){return c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE);}
    private static String settingsKey(Context c){SharedPreferences s=settings(c);return s.getInt("strideLengthCm",72)+"/"+s.getInt("ageYears",0)+"/"+s.getInt("maxHr",0);}
    /** Maximum heart rate: the user's own figure if set, else 208 − 0.7 × age (Tanaka 2001); -1 without either. */
    static int maxHeartRate(Context c){SharedPreferences s=settings(c);int set=s.getInt("maxHr",0);if(set>0)return set;int age=s.getInt("ageYears",0);return age>0?(int)Math.round(208-.7*age):-1;}

    // ---- Computation ----
    private static final class P { double lat,lon,m,alt=Double.NaN,baro=Double.NaN;long s,k=-1;boolean run,brk; }

    private static SessionMetrics compute(Context c,JSONObject o){
        SessionMetrics x=new SessionMetrics(o.optLong("id"));
        x.km=o.optDouble("distanceKm",0);x.elapsedS=o.optLong("durationMs",0)/1000;x.movingS=o.optLong("movingMs",o.optLong("durationMs",0))/1000;
        x.steps=o.optLong("steps",0);x.stepsOk=o.optBoolean("stepsAvailable",false)&&x.steps>0;x.indoor="indoor".equals(o.optString("activityMode"));
        x.estimatedDistance=o.optBoolean("distanceEstimated",false)||x.indoor;x.vehicle=Insights.vehicle(o);
        x.run=o.optDouble("runDistanceKm",0)>Math.max(.05,x.km*.5)||"Run".equals(o.optString("type"));
        if(x.movingS>0&&x.km>0){x.kmh=x.km/(x.movingS/3600d);x.paceMoving=x.movingS/x.km;}
        if(x.elapsedS>0&&x.km>0)x.paceElapsed=x.elapsedS/x.km;

        List<P> pts=points(o.optString("route","[]"));x.gps=!x.indoor&&pts.size()>=2;
        long[][] tl=timeline(o.optString("timeline","[]"));
        // Steps over time: the 5-second timeline (8.2+), else steps stored on GPS points, else none.
        List<long[]> stepSeries=new ArrayList<>();
        if(tl.length>=6&&tl[tl.length-1][1]>0)for(long[] r:tl){if(r[1]>=0)stepSeries.add(new long[]{r[0],r[1]});}
        else for(P p:pts)if(p.k>=0)stepSeries.add(new long[]{p.s,p.k});
        if(!stepSeries.isEmpty())x.stepSource="Kadamm";
        else{
            // Older sessions: Samsung Health's per-minute steps, read through Health Connect, summed into a running total.
            JSONArray hm=o.optJSONArray("hcMin");if(hm==null)try{String v=o.optString("hcMin","");if(!v.isEmpty())hm=new JSONArray(v);}catch(Exception ignored){}
            if(hm!=null&&hm.length()>=3){long cum=0;stepSeries.add(new long[]{Math.max(0,hm.optJSONArray(0).optLong(0)),0});
                for(int i=0;i<hm.length();i++){JSONArray r=hm.optJSONArray(i);if(r==null)continue;cum+=r.optLong(1);stepSeries.add(new long[]{r.optLong(0)+60,cum});}
                if(cum>0)x.stepSource="Health Connect";else stepSeries.clear();}
        }
        // Distance over time {seconds, metres, isRun}: GPS track outdoors, the timeline indoors.
        List<double[]> dist=new ArrayList<>();
        if(x.gps)for(P p:pts)dist.add(new double[]{p.s,p.m,p.run?1:0});
        else if(tl.length>=6)for(long[] r:tl)dist.add(new double[]{r[0],r[2],x.run?1:0});

        cadenceAndStride(x,stepSeries,dist);
        zones(c,x,dist,o);
        splits(x,dist,stepSeries,pts);
        if(x.gps&&!x.vehicle)bestEfforts(x,pts);
        elevation(x,pts,tl,o);
        heartRate(c,x,o);
        // Effort: minutes in each zone weighted by intensity; heart-rate zones replace movement zones when present.
        long[] z=x.hrZoneS!=null?x.hrZoneS:x.zoneS;double e=0;for(int i=0;i<5;i++)e+=z[i]/60d*ZONE_WEIGHT[i];x.effort=e;
        // WHO counts a vigorous minute as two moderate ones; 150 a week is the guideline.
        x.intensityMin=(z[2]+z[3])/60d+2*z[4]/60d;
        if(x.run&&x.gps&&!x.vehicle)x.vdot=vdot(x);
        if(x.gps)x.pace=Insights.pace(Insights.track(o.optString("route","[]")));
        else if(dist.size()>=6)x.pace=paceFromTimeline(dist);
        if(x.gps&&!x.vehicle){curve(x,pts);overlays(x,pts);}
        if(x.stride.size()>=6){double sum=0;for(double[] q:x.stride)sum+=q[1];double mean=sum/x.stride.size(),v=0;for(double[] q:x.stride)v+=(q[1]-mean)*(q[1]-mean);x.strideCv=Math.sqrt(v/x.stride.size())/mean*100;}
        return x;
    }

    private static List<P> points(String route){
        List<P> out=new ArrayList<>();
        try{
            JSONArray a=new JSONArray(route);P prev=null;
            for(int i=0;i<a.length();i++){
                JSONObject q=a.optJSONObject(i);if(q==null||!q.has("s"))continue;
                P p=new P();p.lat=q.optDouble("lat");p.lon=q.optDouble("lon");p.s=q.optLong("s");p.run="r".equals(q.optString("t"));p.brk=q.optBoolean("break",false);
                if(q.has("a"))p.alt=q.optDouble("a");if(q.has("b"))p.baro=q.optDouble("b");if(q.has("k"))p.k=q.optLong("k");
                // Distance across a GPS gap isn't counted, matching the session's own distance.
                p.m=prev==null?0:prev.m+(p.brk?0:Insights.metres(prev.lat,prev.lon,p.lat,p.lon));
                out.add(p);prev=p;
            }
        }catch(Exception ignored){}
        return out;
    }
    /** Timeline rows {seconds, steps or -1, metres, barometric decimetres or Long.MIN_VALUE}. */
    private static long[][] timeline(String json){
        try{
            JSONArray a=new JSONArray(json);long[][] out=new long[a.length()][];
            for(int i=0;i<a.length();i++){JSONArray r=a.getJSONArray(i);out[i]=new long[]{r.getLong(0),r.getLong(1),r.getLong(2),r.isNull(3)?Long.MIN_VALUE:Math.round(r.getDouble(3)*10)};}
            return out;
        }catch(Exception e){return new long[0][];}
    }

    /** Index of the last sample at least {@code window} seconds before sample i, or -1. */
    private static int back(long[] times,int i,long window){int j=i;while(j>0&&times[i]-times[j]<window)j--;return times[i]-times[j]>=window?j:-1;}

    /**
     * Cadence (steps per minute) and stride over 30-second windows. Stride is measured only outdoors, as GPS
     * distance over steps; indoors it is the configured stride, which is not a measurement.
     */
    private static void cadenceAndStride(SessionMetrics x,List<long[]> st,List<double[]> dist){
        long[] t=new long[st.size()];for(int i=0;i<t.length;i++)t[i]=st.get(i)[0];
        double movingSteps=0,movingSec=0;
        for(int i=1;i<st.size();i++){
            int j=back(t,i,30);if(j<0||t[i]-t[j]>90)continue;
            double spm=(st.get(i)[1]-st.get(j)[1])*60d/(t[i]-t[j]);if(spm<0||spm>260)continue;
            boolean run=x.gps&&runAt(dist,t[i]);x.cadence.add(new double[]{t[i],spm,run?1:0});
            if(spm>=40){double dt=t[i]-t[i-1];movingSec+=dt;movingSteps+=spm*dt/60d;x.cadenceMax=Math.max(x.cadenceMax,spm);}
        }
        if(movingSec>=60)x.cadenceAvg=movingSteps*60d/movingSec;
        else if(x.stepsOk&&x.movingS>=60)x.cadenceAvg=x.steps*60d/x.movingS;
        // Stride: outdoors with GPS and steps only.
        if(x.gps&&!x.estimatedDistance&&x.stepsOk&&!x.vehicle){
            x.strideMeasured=true;x.strideAvgCm=x.km*100000d/x.steps;
            for(int i=1;i<st.size();i++){
                int j=back(t,i,30);if(j<0||t[i]-t[j]>90)continue;long dk=st.get(i)[1]-st.get(j)[1];if(dk<20)continue;
                double dm=metresAt(dist,t[i])-metresAt(dist,t[j]);double cm=dm*100/dk;if(cm<25||cm>250)continue;
                x.stride.add(new double[]{t[i],cm,runAt(dist,t[i])?1:0});x.strideMaxCm=Math.max(x.strideMaxCm,cm);
            }
            if(x.strideAvgCm<25||x.strideAvgCm>250){x.strideAvgCm=-1;x.strideMeasured=false;x.stride.clear();}
        }
    }
    private static double metresAt(List<double[]> d,double sec){
        if(d.isEmpty())return 0;if(sec<=d.get(0)[0])return d.get(0)[1];
        int lo=0,hi=d.size()-1;if(sec>=d.get(hi)[0])return d.get(hi)[1];
        while(hi-lo>1){int mid=(lo+hi)/2;if(d.get(mid)[0]<=sec)lo=mid;else hi=mid;}
        double[] a=d.get(lo),b=d.get(hi);double f=b[0]==a[0]?0:(sec-a[0])/(b[0]-a[0]);return a[1]+(b[1]-a[1])*f;
    }
    private static boolean runAt(List<double[]> d,double sec){
        if(d.isEmpty())return false;int lo=0,hi=d.size()-1;while(hi-lo>1){int mid=(lo+hi)/2;if(d.get(mid)[0]<=sec)lo=mid;else hi=mid;}return d.get(hi)[2]>0;
    }
    /** Time (seconds) at which cumulative distance first reaches {@code m}, interpolated; NaN if never. */
    private static double timeAt(List<double[]> d,double m){
        for(int i=1;i<d.size();i++)if(d.get(i)[1]>=m){double[] a=d.get(i-1),b=d.get(i);double f=b[1]==a[1]?0:(m-a[1])/(b[1]-a[1]);return a[0]+(b[0]-a[0])*f;}
        return Double.NaN;
    }
    private static double stepsAt(List<long[]> st,double sec){
        if(st.isEmpty())return Double.NaN;if(sec<=st.get(0)[0])return st.get(0)[1];
        for(int i=1;i<st.size();i++)if(st.get(i)[0]>=sec){long[] a=st.get(i-1),b=st.get(i);double f=b[0]==a[0]?0:(sec-a[0])/(double)(b[0]-a[0]);return a[1]+(b[1]-a[1])*f;}
        return st.get(st.size()-1)[1];
    }

    /** Time in each intensity zone, from cadence when steps were recorded over time, else from GPS speed, else the session average. */
    private static void zones(Context c,SessionMetrics x,List<double[]> dist,JSONObject o){
        boolean moving=false;long still=0;
        if(x.cadence.size()>=4){
            x.zoneSource="cadence";
            for(int i=0;i<x.cadence.size();i++){
                double[] q=x.cadence.get(i);double dt=i==0?Math.min(60,q[0]):Math.min(90,q[0]-x.cadence.get(i-1)[0]);
                if(q[1]<40){still+=dt;if(moving&&still>=20){x.stops++;moving=false;}continue;}
                still=0;moving=true;x.zoneS[zone(q[1],CADENCE_FROM)]+=Math.round(dt);
            }
        }else if(x.gps&&dist.size()>=6){
            x.zoneSource="GPS speed";long[] t=new long[dist.size()];for(int i=0;i<t.length;i++)t[i]=(long)dist.get(i)[0];
            for(int i=1;i<dist.size();i++){
                int j=back(t,i,30);if(j<0)continue;double dt=Math.min(30,t[i]-t[i-1]);if(t[i]-t[j]>120)continue;
                double kmh=(dist.get(i)[1]-dist.get(j)[1])/(t[i]-t[j])*3.6;
                if(kmh<1){still+=dt;if(moving&&still>=20){x.stops++;moving=false;}continue;}
                still=0;moving=true;x.zoneS[dist.get(i)[2]>0?4:zone(kmh,SPEED_FROM)]+=Math.round(dt);
            }
        }else if(x.movingS>=60&&(x.cadenceAvg>0||x.kmh>0)){
            x.zoneSource="session average";
            x.zoneS[x.cadenceAvg>0?zone(x.cadenceAvg,CADENCE_FROM):x.run?4:zone(x.kmh,SPEED_FROM)]=x.movingS;
        }
    }
    private static int zone(double v,double[] from){int z=0;for(double f:from)if(v>=f)z++;return z;}
    private static int zone(double v,int[] from){int z=0;for(int f:from)if(v>=f)z++;return z;}

    /** Kilometre splits with pace, cadence and climb; the last part-kilometre counts if it's 50 m or more. */
    private static void splits(SessionMetrics x,List<double[]> dist,List<long[]> st,List<P> pts){
        if(dist.size()<2)return;double total=dist.get(dist.size()-1)[1];if(total<200)return;
        double prevT=dist.get(0)[0];int k=1;
        while(true){
            double target=k*1000d;boolean last=target>total;if(last&&total-(k-1)*1000d<50)break;
            double t=last?dist.get(dist.size()-1)[0]:timeAt(dist,target);if(Double.isNaN(t))break;
            Split s=new Split();s.index=k;s.fraction=last?(total-(k-1)*1000d)/1000d:1;s.sec=Math.round(t-prevT);s.pace=s.sec/s.fraction;
            if(!st.isEmpty()&&t-prevT>0){double a=stepsAt(st,prevT),b=stepsAt(st,t);if(!Double.isNaN(a)&&b>a)s.cadence=(b-a)*60/(t-prevT);}
            x.splits.add(s);prevT=t;k++;if(last)break;
        }
        // Climb per split from the altitude series, filled in elevation().
        Split fast=null,slow=null;for(Split s:x.splits){if(s.fraction<.5)continue;if(fast==null||s.pace<fast.pace)fast=s;if(slow==null||s.pace>slow.pace)slow=s;}
        if(fast!=null&&x.splits.size()>1){fast.fastest=true;if(slow!=fast)slow.slowest=true;}
        // Consistency: spread of pace over 250 m stretches (stops excluded), and first half against second half.
        List<Double> chunks=new ArrayList<>();double pt=dist.get(0)[0];
        for(double m=250;m<=total;m+=250){double t=timeAt(dist,m);if(Double.isNaN(t))break;chunks.add((t-pt)*4);pt=t;}
        if(chunks.size()>=4){
            double[] v=new double[chunks.size()];for(int i=0;i<v.length;i++)v[i]=chunks.get(i);double[] sorted=v.clone();Arrays.sort(sorted);double med=sorted[sorted.length/2];
            double sum=0,n=0;for(double q:v)if(q<med*2.5){sum+=q;n++;}double mean=sum/n,var=0;for(double q:v)if(q<med*2.5)var+=(q-mean)*(q-mean);
            x.consistency=Math.sqrt(var/n)/mean*100;
        }
        double half=timeAt(dist,total/2);
        if(!Double.isNaN(half)&&total>=1000){x.firstHalfPace=(half-dist.get(0)[0])/(total/2000d);x.secondHalfPace=(dist.get(dist.size()-1)[0]-half)/(total/2000d);}
    }

    /** Fastest time for each standard distance anywhere inside the session (GPS only), with the start point interpolated. */
    private static void bestEfforts(SessionMetrics x,List<P> pts){
        for(int target:EFFORTS){
            if(pts.get(pts.size()-1).m<target)break;double bestT=Double.MAX_VALUE;int j=0;
            for(int i=1;i<pts.size();i++){
                P e=pts.get(i);if(e.m<target)continue;
                while(j+1<i&&e.m-pts.get(j+1).m>=target)j++;
                P a=pts.get(j),b=pts.get(j+1);double startM=e.m-target;double f=b.m==a.m?0:(startM-a.m)/(b.m-a.m);
                double startT=a.s+(b.s-a.s)*Math.max(0,Math.min(1,f));if(e.s-startT<bestT){bestT=e.s-startT;x.bestEnd.put(target,new double[]{e.lat,e.lon});}
            }
            // A pace faster than 2:30 /km means a GPS jump, not a real effort.
            if(bestT<Double.MAX_VALUE&&bestT/(target/1000d)>=150)x.best.put(target,bestT);
        }
    }

    /**
     * Elevation from the barometer when the phone has one (8.2+; precise to about a metre), else GPS altitude,
     * which drifts by several metres and is smoothed hard. Gradient and grade-adjusted pace need the barometer.
     */
    private static void elevation(SessionMetrics x,List<P> pts,long[][] tl,JSONObject o){
        List<double[]> alt=new ArrayList<>();// {seconds, metres along, altitude}
        int baro=0;for(P p:pts)if(!Double.isNaN(p.baro))baro++;
        if(baro>=10){x.barometer=true;for(P p:pts)if(!Double.isNaN(p.baro))alt.add(new double[]{p.s,p.m,p.baro});}
        else if(!x.gps){int n=0;for(long[] r:tl)if(r[3]!=Long.MIN_VALUE)n++;if(n>=10){x.barometer=true;for(long[] r:tl)if(r[3]!=Long.MIN_VALUE)alt.add(new double[]{r[0],r[2],r[3]/100d});}}
        if(alt.isEmpty())for(P p:pts)if(!Double.isNaN(p.alt))alt.add(new double[]{p.s,p.m,p.alt});
        if(alt.size()<10)return;
        // Heights above sea level: GPS reports height above the WGS84 ellipsoid, so the geoid offset for this
        // place is added; barometer altitude (standard atmosphere) is anchored to the GPS sea-level average.
        P first=null;for(P p:pts)if(!Double.isNaN(p.alt)){first=p;break;}
        double off=first==null?Double.NaN:geoid(first.lat,first.lon);
        if(!Double.isNaN(off)){
            if(x.barometer){double g=0,b=0;int n=0;for(P p:pts)if(!Double.isNaN(p.alt)&&!Double.isNaN(p.baro)){g+=p.alt+off;b+=p.baro;n++;}if(n>=10){double shift=(g-b)/n;for(double[] q:alt)q[2]+=shift;}}
            else for(double[] q:alt)q[2]+=off;
            x.seaLevel=true;
        }
        // Smooth: a moving average over neighbours (wider for GPS).
        int w=x.barometer?2:8;double[] sm=new double[alt.size()];
        for(int i=0;i<sm.length;i++){double s=0;int n=0;for(int k=Math.max(0,i-w);k<=Math.min(sm.length-1,i+w);k++){s+=alt.get(k)[2];n++;}sm[i]=s/n;}
        double gain=0,loss=0,ref=sm[0],th=x.barometer?1:5;x.altMin=sm[0];x.altMax=sm[0];
        for(double v:sm){x.altMin=Math.min(x.altMin,v);x.altMax=Math.max(x.altMax,v);if(v-ref>=th){gain+=v-ref;ref=v;}else if(ref-v>=th){loss+=ref-v;ref=v;}}
        x.gain=x.barometer?gain:o.has("elevGainM")?o.optDouble("elevGainM"):gain;x.loss=loss;
        for(int i=0;i<sm.length;i++)x.elevation.add(new double[]{alt.get(i)[1]/1000d,sm[i]});
        for(Split s:x.splits){double a=altAt(alt,sm,(s.index-1)*1000d),b=altAt(alt,sm,(s.index-1)*1000d+s.fraction*1000d);if(!Double.isNaN(a)&&!Double.isNaN(b))s.elev=b-a;}
        if(!x.barometer||!x.gps)return;
        // Gradient over 30 m stretches: uphill above +2 %, downhill below −2 %. Grade-adjusted pace uses
        // Minetti's energy cost of moving on a slope (J/kg/m), relative to flat ground (3.6).
        x.gradeS=new long[3];double adjusted=0,time=0;int j=0;
        for(int i=1;i<alt.size();i++){
            while(j<i-1&&alt.get(i)[1]-alt.get(j+1)[1]>=30)j++;double run=alt.get(i)[1]-alt.get(j)[1];if(run<10)continue;
            double g=(sm[i]-sm[j])/run,dt=alt.get(i)[0]-alt.get(i-1)[0],dm=alt.get(i)[1]-alt.get(i-1)[1];if(dt<=0||dt>30)continue;
            x.gradeS[g>.02?0:g<-.02?2:1]+=Math.round(dt);
            double gc=Math.max(-.45,Math.min(.45,g));double cost=155.4*Math.pow(gc,5)-30.4*Math.pow(gc,4)-43.3*Math.pow(gc,3)+46.3*gc*gc+19.5*gc+3.6;
            adjusted+=dm*cost/3.6;time+=dt;
            if(dm>0)x.gap.add(new double[]{alt.get(i)[1]/1000d,Math.max(150,Math.min(1800,dt/(dm*cost/3.6/1000d))),0});
        }
        x.gap=smooth(x.gap,8);
        if(adjusted>100)x.gapPace=time/(adjusted/1000d);
    }
    private static final Map<String,Double> geoidCache=new HashMap<>();
    /** Sea-level height minus ellipsoid height here (metres), from Android's geoid model (Android 14+); NaN if unavailable. */
    private static double geoid(double lat,double lon){
        if(android.os.Build.VERSION.SDK_INT<34)return Double.NaN;String key=Math.round(lat*10)+","+Math.round(lon*10);
        synchronized(geoidCache){Double v=geoidCache.get(key);if(v!=null)return v;}
        double out=Double.NaN;
        try{android.location.Location l=new android.location.Location("gps");l.setLatitude(lat);l.setLongitude(lon);l.setAltitude(0);
            new android.location.altitude.AltitudeConverter().addMslAltitudeToLocation(appContext,l);if(l.hasMslAltitude())out=l.getMslAltitudeMeters();}catch(Exception ignored){}
        synchronized(geoidCache){geoidCache.put(key,out);}return out;
    }
    private static Context appContext;
    private static double altAt(List<double[]> alt,double[] sm,double m){for(int i=0;i<alt.size();i++)if(alt.get(i)[1]>=m)return sm[i];return alt.isEmpty()?Double.NaN:sm[sm.length-1];}

    private static void heartRate(Context c,SessionMetrics x,JSONObject o){
        JSONArray a=o.optJSONArray("hr");if(a==null){try{String s=o.optString("hr","");if(!s.isEmpty())a=new JSONArray(s);}catch(Exception ignored){}}
        x.hrSource=o.optString("hrSource",null);x.maxHr=maxHeartRate(c);
        if(a==null||a.length()<6)return;double sum=0;
        for(int i=0;i<a.length();i++){JSONArray r=a.optJSONArray(i);if(r==null)continue;double bpm=r.optDouble(1);x.hr.add(new double[]{r.optDouble(0),bpm,0});sum+=bpm;x.hrMax=Math.max(x.hrMax,(int)bpm);}
        x.hrAvg=(int)Math.round(sum/x.hr.size());
        if(x.maxHr>0){
            x.hrZoneS=new long[5];
            for(int i=1;i<x.hr.size();i++){double dt=Math.min(30,x.hr.get(i)[0]-x.hr.get(i-1)[0]),f=x.hr.get(i)[1]/x.maxHr;if(f<.5)continue;x.hrZoneS[zone(f,HR_FROM)]+=Math.round(dt);}
        }
    }

    private static List<double[]> smooth(List<double[]> d,int w){List<double[]> out=new ArrayList<>();for(int i=0;i<d.size();i++){double sum=0;int n=0;for(int k=Math.max(0,i-w);k<=Math.min(d.size()-1,i+w);k++){sum+=d.get(k)[1];n++;}out.add(new double[]{d.get(i)[0],sum/n,0});}return out;}

    /** Fastest pace held over each duration in {@link #CURVE}: the most distance covered in that much time. */
    private static void curve(SessionMetrics x,List<P> pts){
        for(int dur:CURVE){
            if(pts.get(pts.size()-1).s-pts.get(0).s<dur)break;double bestM=0;int j=0;
            for(int i=1;i<pts.size();i++){while(j<i&&pts.get(i).s-pts.get(j+1).s>=dur)j++;if(pts.get(i).s-pts.get(j).s>=dur)bestM=Math.max(bestM,pts.get(i).m-pts.get(j).m);}
            if(bestM>dur/1800d*1000*.1&&dur/(bestM/1000d)>=150)x.bestByTime.put(dur,dur/(bestM/1000d));
        }
    }
    /**
     * Route overlays. Pace: each stretch coloured by its 30-second speed, from this session's slowest (green)
     * to fastest (red) stretches. Grade (barometer only): uphill red, flat amber, downhill blue.
     */
    private static void overlays(SessionMetrics x,List<P> pts){
        long[] t=new long[pts.size()];for(int i=0;i<t.length;i++)t[i]=pts.get(i).s;
        double[] kmh=new double[pts.size()];
        for(int i=1;i<pts.size();i++){int j=back(t,i,30);if(j<0)j=0;double dt=t[i]-t[j];kmh[i]=dt>0?(pts.get(i).m-pts.get(j).m)/dt*3.6:0;if(kmh[i]>=1&&dt<=120)x.speeds.add(new double[]{t[i],kmh[i],Math.min(30,t[i]-t[i-1])});}
        double[] sorted=new double[x.speeds.size()];for(int i=0;i<sorted.length;i++)sorted[i]=x.speeds.get(i)[1];Arrays.sort(sorted);
        if(sorted.length>=10){double lo=sorted[sorted.length/10],hi=sorted[sorted.length*9/10];if(hi-lo<.5){lo-=.25;hi+=.25;}
            for(int i=1;i<pts.size();i++){P a=pts.get(i-1),b=pts.get(i);if(b.brk)continue;double f=(kmh[i]-lo)/(hi-lo);x.paceOverlay.add(new double[]{a.lat,a.lon,b.lat,b.lon,ramp(Math.max(0,Math.min(1,f)))});}}
        int baro=0;for(P p:pts)if(!Double.isNaN(p.baro))baro++;
        if(baro>=10){int j=0;
            for(int i=1;i<pts.size();i++){P b=pts.get(i);if(b.brk||Double.isNaN(b.baro))continue;while(j<i-1&&b.m-pts.get(j+1).m>=30)j++;P a=pts.get(j);if(Double.isNaN(a.baro)||b.m-a.m<10)continue;
                double g=(b.baro-a.baro)/(b.m-a.m);P p=pts.get(i-1);x.gradeOverlay.add(new double[]{p.lat,p.lon,b.lat,b.lon,g>.02?MetricViews.ZONE_COLORS[4]:g<-.02?MetricViews.ZONE_COLORS[1]:MetricViews.ZONE_COLORS[3]});}}
    }
    /** Green → amber → red. */
    static int ramp(double f){int a=f<.5?MetricViews.ZONE_COLORS[2]:MetricViews.ZONE_COLORS[3],b=f<.5?MetricViews.ZONE_COLORS[3]:MetricViews.ZONE_COLORS[4];return MetricViews.blendC(a,b,(float)(f<.5?f*2:(f-.5)*2));}

    // ---- Pace zones: six zones around your threshold pace, the best pace you've held for 30 minutes ----
    static final String[] PACE_ZONES={"Recovery","Endurance","Tempo","Threshold","VO₂ max","Anaerobic"};
    /** Lower bounds as a share of threshold speed (Strava-style): 78, 88, 94, 101, 105 %. */
    static final double[] PACE_FROM={.78,.88,.94,1.01,1.05};
    /** Threshold pace (seconds per km) from all sessions' best 30-minute stretch, or -1 before any 30-minute GPS session. */
    static double thresholdPace(Context c,JSONArray hist){
        double best=-1;for(int i=0;i<hist.length();i++){JSONObject s=hist.optJSONObject(i);if(s==null)continue;Double p=of(c,s).bestByTime.get(1800);if(p!=null&&(best<0||p<best))best=p;}return best;
    }
    /** Seconds in each pace zone for this session, given the threshold pace. */
    long[] paceZones(double threshold){
        if(threshold<=0||speeds.isEmpty())return null;double tk=3600/threshold;long[] z=new long[6];
        for(double[] q:speeds){int k=0;for(double f:PACE_FROM)if(q[1]>=tk*f)k++;z[k]+=Math.round(q[2]);}
        return z;
    }

    /** Daniels' VDOT from the best running effort of 1.5 km or more: an estimate of VO₂max from race pace. */
    private static double vdot(SessionMetrics x){
        double best=-1;
        for(Map.Entry<Integer,Double> e:x.best.entrySet()){
            if(e.getKey()<1500)continue;double t=e.getValue()/60d,v=e.getKey()/t;
            double pct=.8+.1894393*Math.exp(-.012778*t)+.2989558*Math.exp(-.1932605*t),vo2=-4.6+.182258*v+.000104*v*v;best=Math.max(best,vo2/pct);
        }
        return best>=20&&best<=90?best:-1;
    }
    /** Indoor pace by distance from the timeline: {km, seconds per km, isRun}, over 150 m windows. */
    private static List<double[]> paceFromTimeline(List<double[]> d){
        List<double[]> out=new ArrayList<>();int j=0;
        for(int i=1;i<d.size();i++){
            while(j<i-1&&d.get(i)[1]-d.get(j+1)[1]>=150)j++;double dm=d.get(i)[1]-d.get(j)[1],dt=d.get(i)[0]-d.get(j)[0];
            if(dm<50||dt<=0)continue;out.add(new double[]{d.get(i)[1]/1000d,Math.max(150,Math.min(1800,dt/(dm/1000d))),d.get(i)[2]});
        }
        return out;
    }

    // ---- Across sessions ----

    /** Steps-based walking speed in m/s; a "vital sign" of health (Studenski et al., JAMA 2011). */
    double walkingSpeed(){return kmh>0&&!run?kmh/3.6:-1;}
    String walkingSpeedVerdict(){double v=walkingSpeed();return v<0?"":v>=1.3?"Brisk, above the healthy range":v>=1.0?"Healthy range (1.0 m/s and up)":v>=.8?"A little below the healthy range":"Slow, below 0.8 m/s";}

    /** {mean effort of the previous sessions, how many}: sessions in the 21 days before this one. */
    static double[] recentEffort(Context c,JSONArray hist,JSONObject o){
        long start=o.optLong("startTimeMs",o.optLong("id"));double sum=0;int n=0;
        for(int i=0;i<hist.length();i++){JSONObject s=hist.optJSONObject(i);if(s==null||s.optLong("id")==o.optLong("id"))continue;long t=s.optLong("startTimeMs",s.optLong("id"));
            if(t<start&&start-t<=21L*86400000L&&s.optLong("durationMs")>=5*60000L&&!Insights.vehicle(s)){sum+=of(c,s).effort;n++;}}
        return new double[]{n>0?sum/n:-1,n};
    }

    /**
     * Fitness, fatigue and form from daily effort, the impulse-response model behind Strava's Fitness & Freshness
     * and TrainingPeaks' Performance Manager (Banister): fitness is a 42-day weighted average of daily effort,
     * fatigue a 7-day one, and form the difference.
     */
    static final class Fitness { final TreeMap<String,double[]> days=new TreeMap<>(); double fitness,fatigue,form; }
    static Fitness fitness(Context c,JSONArray hist){
        Fitness f=new Fitness();TreeMap<String,Double> load=new TreeMap<>();
        for(int i=0;i<hist.length();i++){JSONObject s=hist.optJSONObject(i);if(s==null||Insights.vehicle(s))continue;String d=s.optString("day","");if(d.isEmpty())continue;Double old=load.get(d);load.put(d,(old==null?0:old)+of(c,s).effort);}
        if(load.isEmpty())return f;
        Calendar day=Calendar.getInstance();try{day.setTime(new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(load.firstKey()));}catch(Exception e){return f;}
        String today=DaySteps.todayKey();double ctl=0,atl=0;
        for(int guard=0;guard<2000;guard++){
            String key=DaySteps.key(day.getTimeInMillis());Double l=load.get(key);double v=l==null?0:l;
            ctl+=(v-ctl)/42d;atl+=(v-atl)/7d;f.days.put(key,new double[]{ctl,atl,ctl-atl,v});
            if(key.compareTo(today)>=0)break;day.add(Calendar.DAY_OF_YEAR,1);
        }
        double[] last=f.days.lastEntry().getValue();f.fitness=last[0];f.fatigue=last[1];f.form=last[2];return f;
    }
    /** How much this session's day moved fitness: the day's fitness minus the day before's. */
    static double fitnessGain(Fitness f,JSONObject o){
        String d=o.optString("day","");double[] on=f.days.get(d);Map.Entry<String,double[]> before=f.days.lowerEntry(d);
        return on==null?0:on[0]-(before==null?0:before.getValue()[0]);
    }

    /** All-time rank (1 = fastest) of this session's best effort at {@code metres} among all sessions, or 0. */
    static int rank(Context c,JSONArray hist,JSONObject o,int metres){
        Double mine=of(c,o).best.get(metres);if(mine==null)return 0;int faster=0;
        for(int i=0;i<hist.length();i++){JSONObject s=hist.optJSONObject(i);if(s==null||s.optLong("id")==o.optLong("id"))continue;Double t=of(c,s).best.get(metres);if(t!=null&&t<mine)faster++;}
        return faster+1;
    }
    /** All-time best time for each effort distance across all sessions. */
    static Map<Integer,double[]> allTimeBest(Context c,JSONArray hist){
        Map<Integer,double[]> out=new TreeMap<>();
        for(int i=0;i<hist.length();i++){JSONObject s=hist.optJSONObject(i);if(s==null)continue;for(Map.Entry<Integer,Double> e:of(c,s).best.entrySet()){double[] b=out.get(e.getKey());if(b==null||e.getValue()<b[0])out.put(e.getKey(),new double[]{e.getValue(),s.optLong("id")});}}
        return out;
    }
    /** Earlier sessions like this one (same indoor/outdoor, distance within 20 %), oldest first, ending with this one: {id, pace}. */
    static List<double[]> matched(Context c,JSONArray hist,JSONObject o){
        List<double[]> out=new ArrayList<>();double km=o.optDouble("distanceKm",0);if(km<.5)return out;long start=o.optLong("startTimeMs",o.optLong("id"));String mode=o.optString("activityMode","outdoor");
        for(int i=0;i<hist.length();i++){JSONObject s=hist.optJSONObject(i);if(s==null||Insights.vehicle(s)||!mode.equals(s.optString("activityMode","outdoor")))continue;
            double k=s.optDouble("distanceKm",0);long t=s.optLong("startTimeMs",s.optLong("id"));if(t>start||Math.abs(k-km)>km*.2)continue;SessionMetrics m=of(c,s);if(m.paceMoving>0)out.add(new double[]{t,m.paceMoving,s.optLong("id")});}
        out.sort((a,b)->Double.compare(a[0],b[0]));return out;
    }
    /**
     * Intensity minutes (WHO: moderate plus twice vigorous) for days in [from, to). Each day takes the larger of
     * its sessions' minutes and its all-day brisk minutes from Health Connect, never the two added together.
     */
    static double intensityMinutes(Context c,JSONArray hist,String from,String to){
        Map<String,Double> byDay=new HashMap<>();
        for(int i=0;i<hist.length();i++){JSONObject s=hist.optJSONObject(i);if(s==null)continue;String d=s.optString("day","");if(d.compareTo(from)>=0&&d.compareTo(to)<0&&!Insights.vehicle(s)){Double v=byDay.get(d);byDay.put(d,(v==null?0:v)+of(c,s).intensityMin);}}
        JSONObject md=DaySteps.minuteDays(c);for(java.util.Iterator<String> it=md.keys();it.hasNext();){String d=it.next();if(d.compareTo(from)<0||d.compareTo(to)>=0)continue;double b=md.optJSONObject(d).optDouble("brisk",0);Double v=byDay.get(d);if(v==null||b>v)byDay.put(d,b);}
        double sum=0;for(double v:byDay.values())sum+=v;return sum;
    }

    // ---- Words ----
    String clock(double sec){return Insights.clock(sec);}
    int dominantZone(){long[] z=hrZoneS!=null?hrZoneS:zoneS;int best=-1;for(int i=0;i<5;i++)if(z[i]>0&&(best<0||z[i]>z[best]))best=i;return best;}
    long zoneTotal(){long[] z=hrZoneS!=null?hrZoneS:zoneS;long t=0;for(long v:z)t+=v;return t;}
    /** A short read of the session in plain words, built only from the numbers above. */
    String summary(double[] recent){
        StringBuilder b=new StringBuilder();int dz=dominantZone();long total=zoneTotal();
        if(dz<0||total<60)return "";
        String kind=run?"run":"walk";
        if(consistency>=0&&consistency<6)b.append("A very steady ").append(ZONES[dz].toLowerCase(Locale.US)).append(' ').append(kind);
        else if(consistency>=0&&consistency>15)b.append("A ").append(ZONES[dz].toLowerCase(Locale.US)).append(' ').append(kind).append(" with changing pace");
        else b.append("A ").append(ZONES[dz].toLowerCase(Locale.US)).append(' ').append(kind);
        long[] z=hrZoneS!=null?hrZoneS:zoneS;long hard=z[2]+z[3]+z[4];
        b.append(String.format(Locale.US,": %d of %d minutes at moderate effort or harder.",Math.round(hard/60d),Math.round(total/60d)));
        if(firstHalfPace>0&&secondHalfPace>0){double d=(firstHalfPace-secondHalfPace)/firstHalfPace;
            if(d>.03)b.append(" You finished faster than you started (negative split).");else if(d<-.05)b.append(" You slowed in the second half.");else b.append(" Even pacing from start to finish.");}
        if(recent!=null&&recent[0]>0&&recent[1]>=3){double r=effort/recent[0];
            if(r>=1.3)b.append(" A bigger effort than usual for you.");else if(r<=.7)b.append(" Lighter than your recent sessions; good for recovery.");else b.append(" About your usual effort.");}
        return b.toString();
    }
}
