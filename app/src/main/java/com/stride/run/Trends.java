package com.stride.run;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 9.7 reports: how steps are spread over the day, pace consistency over time, goal forecasts (today, week, month,
 * year) from your own history, and active calories against body weight. Everything comes from measured data; the
 * forecasts and calories are labelled as estimates where they're shown.
 */
final class Trends {
    private Trends() {}
    static final int WAKE=7,SLEEP=22;

    // ---- Daily step distribution ----
    /** A day's steps per hour: Kadamm's own count or Health Connect's, whichever is more complete. Null without either. */
    static double[] hours(Context c,String day){
        double[] own=DaySteps.hoursOn(c,day),hc=null;JSONObject d=DaySteps.minuteDays(c).optJSONObject(day);
        if(d!=null&&d.optJSONArray("h")!=null){JSONArray a=d.optJSONArray("h");hc=new double[24];for(int i=0;i<24;i++)hc[i]=a.optDouble(i);}
        return own==null?hc:hc==null?own:sum(own)>=sum(hc)?own:hc;
    }
    static double sum(double[] a){double s=0;for(double v:a)s+=v;return s;}

    static final class Spread { double score;int activeHours,windowHours,topHour,longestQuiet,quietFrom;double topShare,inWindow; }
    /**
     * How evenly a day's steps are spread over waking hours (7 am to 10 pm, or to the current hour today): 10 when
     * every hour has the same steps, 0 when they all fall in one hour (normalised Shannon entropy). Null under 500
     * steps or 3 hours.
     */
    static Spread spread(double[] h,int untilHour){
        if(h==null)return null;int end=Math.min(SLEEP,untilHour),n=end-WAKE;if(n<3)return null;
        Spread s=new Spread();s.windowHours=n;double tot=0;for(int i=WAKE;i<end;i++)tot+=h[i];if(tot<500)return null;s.inWindow=tot;
        double hsum=0;int run=0;
        for(int i=WAKE;i<end;i++){double p=h[i]/tot;if(p>0)hsum-=p*Math.log(p);if(h[i]>=250)s.activeHours++;if(h[i]>h[s.topHour]||s.topHour<WAKE)s.topHour=i;
            if(h[i]<100){run++;if(run>s.longestQuiet){s.longestQuiet=run;s.quietFrom=i-run+1;}}else run=0;}
        s.score=Math.round(100*hsum/Math.log(n))/10.0;s.topShare=h[s.topHour]/tot;return s;
    }
    static String spreadLabel(double score){return score>=8?"Well spread":score>=6?"Fairly spread":score>=4?"Bunched up":"All in one go";}
    /** The last {@code days} full days' scores {day → score}, oldest first. */
    static TreeMap<String,Double> spreadHistory(Context c,int days){
        TreeMap<String,Double> out=new TreeMap<>();Calendar d=Calendar.getInstance();
        for(int i=0;i<days;i++){d.add(Calendar.DAY_OF_YEAR,-1);String k=DaySteps.key(d.getTimeInMillis());Spread s=spread(hours(c,k),SLEEP);if(s!=null)out.put(k,s.score);}
        return out;
    }

    // ---- Pace consistency over time ----
    /** The last 20 walks' pace scores {day index, score}, oldest first. */
    static List<double[]> paceScores(Context c,JSONArray hist){
        List<double[]> out=new ArrayList<>();
        for(int i=0;i<hist.length()&&out.size()<20;i++){JSONObject o=hist.optJSONObject(i);if(o==null||!DaySummary.counts(o))continue;SessionMetrics m=SessionMetrics.of(c,o);if(m.paceScore<0||m.vehicle)continue;out.add(0,new double[]{o.optLong("id"),m.paceScore});}
        return out;
    }

    // ---- Goal forecast ----
    static final class Outlook {
        String name;long sofar,target,mid,lo,hi;double needPerDay=-1;int daysLeft;String crossDay;double goalDays=-1;int goalDaysSoFar,totalDays;
        boolean reached(){return sofar>=target;}
    }
    static final class Forecast {
        int goal,dataDays;double accuracy=-1;long missSteps;
        /** The first day of the year with step data, when that's after 1 January. */
        String yearFrom;
        /** Today: steps by bedtime (your usual steps after this hour added to what you have) and the chance of reaching the goal. */
        long todaySoFar,todayMid,todayLo,todayHi;int todayChance=-1,usualDone=-1;
        Outlook week,month,year;
        /** This month day by day {actual cumulative (−1 ahead of today), projected cumulative, low, high}. */
        double[][] monthPath;
        double[] expectedByDow=new double[7];
    }
    private static double median(List<Double> x){if(x.isEmpty())return -1;Double[] a=x.toArray(new Double[0]);Arrays.sort(a);return a.length%2==1?a[a.length/2]:(a[a.length/2-1]+a[a.length/2])/2;}
    private static int dow(Calendar d){return (d.get(Calendar.DAY_OF_WEEK)+5)%7;}
    /** A day counts as data from 500 steps: below that the phone wasn't counting (switched off, sensor asleep). */
    static final long MIN_DAY=500;
    /**
     * Expected steps on a day: a quarter the median of that weekday over the 8 weeks before it (needs 2 such days),
     * three quarters the median of the 28 days before it. Tested against real history, a long recent window
     * forecasts best; the weekday part keeps a regular rhythm (busy Mondays, quiet Sundays).
     */
    private static double expected(Map<String,Long> t,Calendar day){
        List<Double> wd=new ArrayList<>(),recent=new ArrayList<>();Calendar d=(Calendar)day.clone();String today=DaySteps.todayKey();
        for(int i=1;i<=56;i++){d.add(Calendar.DAY_OF_YEAR,-1);String k=DaySteps.key(d.getTimeInMillis());if(k.compareTo(today)>=0)continue;Long v=t.get(k);if(v==null||v<MIN_DAY)continue;if(i%7==0)wd.add((double)v);if(i<=28)recent.add((double)v);}
        double a=wd.size()>=2?median(wd):-1,b=median(recent);if(a<0&&b<0)return -1;if(a<0)return b;if(b<0)return a;return .25*a+.75*b;
    }
    /** Share of days over 8 weeks on this weekday that reached the goal; −1 with fewer than 2 such days. */
    private static double hitRate(Map<String,Long> t,Calendar day,int goal){
        int n=0,hit=0;Calendar d=(Calendar)day.clone();for(int i=1;i<=8;i++){d.add(Calendar.DAY_OF_YEAR,-7);Long v=t.get(DaySteps.key(d.getTimeInMillis()));if(v==null||v<MIN_DAY)continue;n++;if(v>=goal)hit++;}
        return n<2?-1:hit/(double)n;
    }
    static Forecast forecast(Context c,int goal){
        Map<String,Long> t=DaySteps.allDayTotals(c);Forecast f=new Forecast();f.goal=goal;Calendar now=Calendar.getInstance();String today=DaySteps.todayKey();
        Calendar d=Calendar.getInstance();for(int i=0;i<56;i++){d.add(Calendar.DAY_OF_YEAR,-1);Long v=t.get(DaySteps.key(d.getTimeInMillis()));if(v!=null&&v>=MIN_DAY)f.dataDays++;}
        if(f.dataDays<14)return f;
        // Spread of real days around their expected value (residual standard deviation) and the average miss, over 4 weeks.
        double ss=0;List<Double> misses=new ArrayList<>(),missSteps=new ArrayList<>();int n=0;d=Calendar.getInstance();
        for(int i=0;i<28;i++){d.add(Calendar.DAY_OF_YEAR,-1);Long v=t.get(DaySteps.key(d.getTimeInMillis()));double e=expected(t,d);if(v==null||v<MIN_DAY||e<=0)continue;ss+=(v-e)*(v-e);misses.add(Math.abs(v-e)/Math.max(e,1));missSteps.add(Math.abs(v-e));n++;}
        double sd=n>=5?Math.sqrt(ss/n):0;if(n>=5){f.accuracy=median(misses);f.missSteps=Math.round(median(missSteps));}
        for(int k=0;k<7;k++){Calendar x=Calendar.getInstance();x.add(Calendar.DAY_OF_YEAR,(k-dow(now)+7)%7);f.expectedByDow[k]=expected(t,x);}

        // Today: what you usually walk after this time of day, from the hours of the last 28 days.
        long sofar=Math.max(0,t.getOrDefault(today,0L));f.todaySoFar=sofar;int hr=now.get(Calendar.HOUR_OF_DAY);double frac=now.get(Calendar.MINUTE)/60.0;
        List<Double> after=new ArrayList<>(),share=new ArrayList<>();d=Calendar.getInstance();
        for(int i=0;i<28;i++){d.add(Calendar.DAY_OF_YEAR,-1);double[] h=hours(c,DaySteps.key(d.getTimeInMillis()));if(h==null)continue;double tot=sum(h);if(tot<500)continue;
            double done=0;for(int j=0;j<hr;j++)done+=h[j];done+=h[hr]*frac;after.add(tot-done);share.add(done/tot);}
        double eToday=expected(t,now);
        if(after.size()>=7){
            Double[] a=after.toArray(new Double[0]);Arrays.sort(a);f.todayMid=sofar+Math.round(median(after));f.todayLo=sofar+Math.round(a[a.length/4]);f.todayHi=sofar+Math.round(a[a.length*3/4]);
            int hit=0;for(double x:after)if(sofar+x>=goal)hit++;f.todayChance=sofar>=goal?100:(int)Math.round(100.0*hit/after.size());f.usualDone=(int)Math.round(100*median(share));
        }else if(eToday>0){double left=Math.max(0,1-(hr+frac-WAKE)/(SLEEP-WAKE));f.todayMid=sofar+Math.round(eToday*Math.min(1,left));f.todayLo=f.todayMid;f.todayHi=f.todayMid;}
        else f.todayMid=sofar;

        // Week (Monday to Sunday).
        Calendar mon=Calendar.getInstance();mon.add(Calendar.DAY_OF_YEAR,-dow(now));f.week=outlook(t,"This week",mon,7,goal,f,sd,now);
        Calendar first=Calendar.getInstance();first.set(Calendar.DAY_OF_MONTH,1);int dim=now.getActualMaximum(Calendar.DAY_OF_MONTH);f.month=outlook(t,"This month",first,dim,goal,f,sd,now);
        // The year from your first day of data, so a year that started before Kadamm isn't counted as missed days.
        Calendar jan=Calendar.getInstance();jan.set(Calendar.DAY_OF_YEAR,1);String firstKey=null,janKey=DaySteps.key(jan.getTimeInMillis());
        for(Map.Entry<String,Long> e:new TreeMap<>(t).entrySet())if(e.getKey().compareTo(janKey)>=0&&e.getValue()>0){firstKey=e.getKey();break;}
        int daysInYear=now.getActualMaximum(Calendar.DAY_OF_YEAR),skip=0;
        if(firstKey!=null&&firstKey.compareTo(janKey)>0){skip=(int)Math.round(dayNum(firstKey)-dayNum(janKey));jan.add(Calendar.DAY_OF_YEAR,skip);f.yearFrom=firstKey;}
        f.year=outlook(t,"This year",jan,daysInYear-skip,goal,f,sd,now);
        // The month day by day, for the chart.
        f.monthPath=new double[dim][4];double cum=0,proj=0,var=0;Calendar x=(Calendar)first.clone();int todayIdx=now.get(Calendar.DAY_OF_MONTH)-1;
        for(int i=0;i<dim;i++){
            if(i<todayIdx){cum+=Math.max(0,t.getOrDefault(DaySteps.key(x.getTimeInMillis()),0L));proj=cum;f.monthPath[i]=new double[]{cum,cum,cum,cum};}
            else if(i==todayIdx){cum+=sofar;proj=proj+f.todayMid;double s1=Math.max(0,f.todayHi-f.todayMid);var+=s1*s1;f.monthPath[i]=new double[]{cum,proj,proj-Math.sqrt(var),proj+Math.sqrt(var)};}
            else{proj+=Math.max(0,f.expectedByDow[dow(x)]);var+=sd*sd;double s2=Math.sqrt(var);f.monthPath[i]=new double[]{-1,proj,Math.max(cum,proj-s2),proj+s2};}
            x.add(Calendar.DAY_OF_YEAR,1);}
        return f;
    }
    /** A period's total so far, where it's heading (±1 standard deviation of your days), the steps a day it needs and goal days. */
    private static Outlook outlook(Map<String,Long> t,String name,Calendar start,int days,int goal,Forecast f,double sd,Calendar now){
        Outlook o=new Outlook();o.name=name;o.target=(long)goal*days;o.totalDays=days;String today=DaySteps.todayKey();Calendar x=(Calendar)start.clone();
        double rest=0,var=0,goalDays=0;
        for(int i=0;i<days;i++){String k=DaySteps.key(x.getTimeInMillis());int cmp=k.compareTo(today);
            if(cmp<0){long v=Math.max(0,t.getOrDefault(k,0L));o.sofar+=v;if(v>=goal){o.goalDaysSoFar++;goalDays++;}}
            else if(cmp==0){o.sofar+=f.todaySoFar;rest+=f.todayMid-f.todaySoFar;double s=Math.max(0,f.todayHi-f.todayMid);var+=s*s;if(f.todaySoFar>=goal)o.goalDaysSoFar++;goalDays+=f.todayChance>=0?f.todayChance/100.0:(f.todayMid>=goal?1:0);o.daysLeft++;
                if(o.sofar<o.target&&o.sofar+rest>=o.target)o.crossDay=k;}
            else{double e=Math.max(0,f.expectedByDow[dow(x)]);rest+=e;var+=sd*sd;double hr=hitRate(t,x,goal);goalDays+=hr>=0?hr:(e>=goal?1:0);o.daysLeft++;
                if(o.crossDay==null&&o.sofar+rest>=o.target&&o.sofar<o.target)o.crossDay=k;}
            x.add(Calendar.DAY_OF_YEAR,1);}
        o.mid=Math.round(o.sofar+rest);double s=Math.sqrt(var);o.lo=Math.round(Math.max(o.sofar,o.mid-s));o.hi=Math.round(o.mid+s);o.goalDays=goalDays;
        // Today counts as a day left: the steps a day you need from now, including the rest of today.
        long need=o.target-o.sofar+f.todaySoFar;if(o.daysLeft>0&&o.sofar<o.target)o.needPerDay=need/(double)o.daysLeft;
        return o;
    }

    // ---- Calories and weight ----
    static final class Body {
        /** Weight by day (Health Connect and your own entries; yours win on the same day), oldest first. */
        TreeMap<String,Double> weight=new TreeMap<>();
        /** Estimated active kcal per day for the last 56 days. */
        TreeMap<String,Double> kcal=new TreeMap<>();
        /** Weekly: {active kcal a day, weight change kg} for weeks with 2+ weigh-ins, oldest first. */
        List<double[]> weeks=new ArrayList<>();
        double kgPerWeek=Double.NaN,kcal28=0,correlation=Double.NaN;int weighIns28;
    }
    static TreeMap<String,Double> manualWeights(Context c){
        TreeMap<String,Double> t=new TreeMap<>();try{JSONObject o=new JSONObject(c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE).getString("weights","{}"));for(Iterator<String> it=o.keys();it.hasNext();){String k=it.next();t.put(k,o.optDouble(k));}}catch(Exception ignored){}
        return t;
    }
    static void logWeight(Context c,double kg){
        SharedPreferences s=c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE);
        try{JSONObject o=new JSONObject(s.getString("weights","{}"));o.put(DaySteps.todayKey(),Math.round(kg*10)/10.0);s.edit().putString("weights",o.toString()).putInt("weightKg",(int)Math.round(kg)).apply();}catch(Exception ignored){}
    }
    static Body body(Context c,JSONArray hist){
        Body b=new Body();b.weight.putAll(Vitals.series(Vitals.all(c),"weight"));b.weight.putAll(manualWeights(c));
        // Active calories: sessions (MET by pace) plus everyday steps at 0.5 kcal per kg per km, the same as the day summary.
        Map<String,Long> totals=DaySteps.allDayTotals(c);Map<String,double[]> ses=new HashMap<>();String from=DaySteps.key(System.currentTimeMillis()-56L*86400000L);
        for(int i=0;i<hist.length();i++){JSONObject o=hist.optJSONObject(i);if(o==null||!DaySummary.counts(o))continue;String k=o.optString("day","");if(k.compareTo(from)<0)continue;
            double[] a=ses.get(k);if(a==null){a=new double[2];ses.put(k,a);}a[0]+=o.optInt("calories",0);if(o.optBoolean("stepsAvailable",false))a[1]+=o.optLong("steps",0);}
        double kg=Calories.weightKg(c);String today=DaySteps.todayKey();Calendar d=Calendar.getInstance();
        for(int i=0;i<56;i++){String k=DaySteps.key(d.getTimeInMillis());d.add(Calendar.DAY_OF_YEAR,-1);
            if(k.equals(today)){b.kcal.put(k,(double)DaySteps.activeKcalToday(c));continue;}
            double[] a=ses.get(k);long all=Math.max(0,totals.getOrDefault(k,0L));if(all<=0&&a==null)continue;
            double out=Math.max(0,all-(a==null?0:a[1]));b.kcal.put(k,(a==null?0:a[0])+DaySteps.stepKm(c,(long)out)*kg*.5);}
        String m28=DaySteps.key(System.currentTimeMillis()-28L*86400000L);
        for(Map.Entry<String,Double> e:b.kcal.entrySet())if(e.getKey().compareTo(m28)>0)b.kcal28+=e.getValue();
        // Trend: least-squares slope of the last 28 days' weigh-ins, in kg per week (needs 3+ readings over a week or more).
        List<double[]> pts=new ArrayList<>();for(Map.Entry<String,Double> e:b.weight.entrySet())if(e.getKey().compareTo(m28)>0)pts.add(new double[]{dayNum(e.getKey()),e.getValue()});
        b.weighIns28=pts.size();if(pts.size()>=3&&pts.get(pts.size()-1)[0]-pts.get(0)[0]>=7)b.kgPerWeek=slope(pts)*7;
        // Weekly pairs for the link between activity and weight: change from first to last weigh-in in each week.
        Calendar mon=Calendar.getInstance();mon.add(Calendar.DAY_OF_YEAR,-dow(mon));
        for(int w=7;w>=1;w--){Calendar s=(Calendar)mon.clone();s.add(Calendar.DAY_OF_YEAR,-7*w);double kc=0;int kd=0;List<Double> ws=new ArrayList<>();
            for(int i=0;i<7;i++){String k=DaySteps.key(s.getTimeInMillis());Double v=b.kcal.get(k);if(v!=null){kc+=v;kd++;}Double wt=b.weight.get(k);if(wt!=null)ws.add(wt);s.add(Calendar.DAY_OF_YEAR,1);}
            if(kd>=4&&ws.size()>=2)b.weeks.add(new double[]{kc/kd,ws.get(ws.size()-1)-ws.get(0)});}
        if(b.weeks.size()>=5)b.correlation=pearson(b.weeks);
        return b;
    }
    static double dayNum(String k){try{return new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.US).parse(k).getTime()/86400000.0;}catch(Exception e){return 0;}}
    private static double slope(List<double[]> p){double mx=0,my=0;for(double[] q:p){mx+=q[0];my+=q[1];}mx/=p.size();my/=p.size();double num=0,den=0;for(double[] q:p){num+=(q[0]-mx)*(q[1]-my);den+=(q[0]-mx)*(q[0]-mx);}return den==0?0:num/den;}
    private static double pearson(List<double[]> p){double mx=0,my=0;for(double[] q:p){mx+=q[0];my+=q[1];}mx/=p.size();my/=p.size();double sxy=0,sx=0,sy=0;for(double[] q:p){sxy+=(q[0]-mx)*(q[1]-my);sx+=(q[0]-mx)*(q[0]-mx);sy+=(q[1]-my)*(q[1]-my);}return sx==0||sy==0?Double.NaN:sxy/Math.sqrt(sx*sy);}
}
