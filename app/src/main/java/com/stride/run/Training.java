package com.stride.run;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Training analysis across sessions: what kind of session each one was, how efficiency, cardiac drift and
 * heart-rate recovery are trending, training monotony over the last week, and the after-session report. Built only
 * from measured data (GPS or step timeline, and heart rate from a watch); anything that can't be measured is left out.
 */
final class Training {
    private Training() {}

    // ---- Session type ----
    static final class Type { String name="",why="";int tone; }

    /**
     * What kind of session it was. Run-walk: 20–80 % of the distance running. Intervals: 4+ surges (250 m stretches
     * 12 %+ faster than your median) and an uneven pace. Long: 45+ minutes running (60+ walking) and 1.5× your usual
     * session of that kind. Tempo/brisk: half the time or more at 80 %+ of max heart rate (or in the brisk and
     * vigorous movement zones without a watch) at a steady pace. Intervals also need 1 km+, surges in a fifth of
     * the 250 m stretches and 2 stops at most, so a walk broken up by road crossings isn't counted as intervals. Recovery: no time in the hard zones, under 30
     * minutes, and heart rate under 65 % of max when known. Easy otherwise.
     */
    static Type type(Context c,JSONArray hist,JSONObject o){
        Type t=new Type();SessionMetrics m=SessionMetrics.of(c,o);if(m.movingS<300||m.vehicle)return t;
        boolean run=m.run;double runShare=m.km>0?o.optDouble("runDistanceKm",0)/m.km:0;
        long[] z=m.hrZoneS!=null?m.hrZoneS:m.zoneS;long total=0;for(long v:z)total+=v;
        double hard=total>0?(z[3]+z[4])/(double)total:0,mod=total>0?(z[2]+z[3]+z[4])/(double)total:0;
        double usual=usualMinutes(c,hist,o,run);
        if(m.km>=.3&&runShare>=.2&&runShare<=.8){t.name=run?"Run-walk":"Walk-run";t.why=String.format(Locale.US,"%.0f %% of the distance running, the rest walking.",runShare*100);return t;}
        if(m.km>=1&&m.surges>=4&&m.surges>=.2*m.chunkPace.size()&&m.consistency>=15&&m.stops<=2){t.name=run?"Intervals":"Interval walk";t.why=m.surges+" fast stretches with easier ones between: pace varied by "+Math.round(m.consistency)+" %.";t.tone=1;return t;}
        double min=m.movingS/60d;
        if(min>=(run?45:60)&&usual>0&&min>=1.5*usual){t.name=run?"Long run":"Long walk";t.why=String.format(Locale.US,"%.0f minutes, %.1f× your usual %s of %.0f minutes.",min,min/usual,run?"run":"walk",usual);t.tone=1;return t;}
        boolean steady=m.consistency<0||m.consistency<12;
        if(hard>=.5&&steady){t.name=run?"Tempo run":"Brisk walk";t.why=String.format(Locale.US,"%.0f %% of the time at %s, at a steady pace.",hard*100,m.hrZoneS!=null?"80 %+ of your max heart rate":"brisk or vigorous effort");t.tone=1;return t;}
        boolean lowHr=m.hrAvg<=0||m.maxHr<=0||m.hrAvg<.65*m.maxHr;
        if(mod<.1&&min<30&&lowHr){t.name=run?"Recovery run":"Recovery walk";t.why=m.hrAvg>0&&m.maxHr>0?String.format(Locale.US,"Short and gentle: heart rate averaged %d %% of max.",Math.round(m.hrAvg*100d/m.maxHr)):"Short and gentle, with no time at moderate effort or harder.";return t;}
        if(mod>=.5){t.name=run?"Steady run":"Steady walk";t.why=String.format(Locale.US,"A steady, moderate effort: %.0f %% of the time at moderate effort, little of it brisk or hard.",mod*100);return t;}
        t.name=run?"Easy run":"Easy walk";t.why=mod>0?String.format(Locale.US,"Mostly comfortable effort: %.0f %% of the time at moderate effort or harder.",mod*100):"Comfortable effort throughout.";return t;
    }
    /** Median moving minutes of your sessions of the same kind over the 6 weeks before this one; -1 under 4 sessions. */
    private static double usualMinutes(Context c,JSONArray hist,JSONObject o,boolean run){
        long at=o.optLong("startTimeMs",o.optLong("id"));List<Double> v=new ArrayList<>();
        for(int i=0;i<hist.length();i++){JSONObject s=hist.optJSONObject(i);if(s==null||s.optLong("id")==o.optLong("id")||!DaySummary.counts(s))continue;long t=s.optLong("startTimeMs",s.optLong("id"));
            if(t>=at||at-t>42L*86400000L)continue;SessionMetrics m=SessionMetrics.of(c,s);if(m.run!=run||m.movingS<300)continue;v.add(m.movingS/60d);}
        if(v.size()<4)return -1;double[] a=new double[v.size()];for(int i=0;i<a.length;i++)a[i]=v.get(i);Arrays.sort(a);return a[a.length/2];
    }

    // ---- Trends ----
    /** One point per session: {time, value, run 1/0}, oldest first, for the last {@code days} days. */
    static List<double[]> series(Context c,JSONArray hist,String what,int days){
        long from=System.currentTimeMillis()-days*86400000L;List<double[]> out=new ArrayList<>();
        for(int i=0;i<hist.length();i++){JSONObject o=hist.optJSONObject(i);if(o==null||!DaySummary.counts(o))continue;long t=o.optLong("startTimeMs",o.optLong("id"));if(t<from)continue;
            SessionMetrics m=SessionMetrics.of(c,o);double v="efficiency".equals(what)?m.efficiency:"drift".equals(what)?(Double.isNaN(m.drift)?-999:m.drift):"hrr".equals(what)?(m.hrEndMoving?m.hrr1:-1):-1;
            if("drift".equals(what)?v>-999:v>0)out.add(new double[]{t,v,m.run?1:0});}
        out.sort((a,b)->Double.compare(a[0],b[0]));return out;
    }
    /** {recent average, earlier average, change %} of a series split at {@code splitDays} ago; null without 2 + 2 points. */
    static double[] change(List<double[]> s,int splitDays,boolean run){
        long cut=System.currentTimeMillis()-splitDays*86400000L;double a=0,b=0;int na=0,nb=0;
        for(double[] p:s){if((p[2]>0)!=run)continue;if(p[0]>=cut){a+=p[1];na++;}else{b+=p[1];nb++;}}
        if(na<2||nb<2)return null;a/=na;b/=nb;return new double[]{a,b,b!=0?(a-b)/Math.abs(b)*100:0};
    }

    // ---- Training monotony (Foster 1998) ----
    static final class Monotony { double[] daily=new double[7];double weekLoad,mean,sd,monotony=-1,strain;int activeDays; }
    /**
     * Monotony = average daily load ÷ its standard deviation over the last 7 days (today included), with daily load
     * = the day's session effort (the same points as Fitness). Above 2.0 means every day carries about the same load
     * with no easy days between, which Foster linked to staleness and illness. Training strain = weekly load × monotony.
     */
    static Monotony monotony(Context c,JSONArray hist){
        Monotony mo=new Monotony();Calendar d=Calendar.getInstance();String[] keys=new String[7];
        for(int i=6;i>=0;i--){keys[i]=DaySteps.key(d.getTimeInMillis());d.add(Calendar.DAY_OF_YEAR,-1);}
        for(int i=0;i<hist.length();i++){JSONObject o=hist.optJSONObject(i);if(o==null||Insights.vehicle(o))continue;String k=o.optString("day","");for(int j=0;j<7;j++)if(keys[j].equals(k))mo.daily[j]+=SessionMetrics.of(c,o).effort;}
        for(double v:mo.daily){mo.weekLoad+=v;if(v>0)mo.activeDays++;}mo.mean=mo.weekLoad/7;
        double var=0;for(double v:mo.daily)var+=(v-mo.mean)*(v-mo.mean);mo.sd=Math.sqrt(var/7);
        if(mo.activeDays>=3&&mo.sd>0){mo.monotony=mo.mean/mo.sd;mo.strain=mo.weekLoad*mo.monotony;}
        return mo;
    }
    static String monotonyWord(double m){return m<0?"":m<1.5?"Varied":m<2?"Fairly even":"Monotonous";}

    // ---- After-session report ----
    static final class Report { Type type;final List<String> good=new ArrayList<>(),limit=new ArrayList<>();String compare="",load="",recovery="",next="";}

    /** The report for one session, built only from its numbers and your history. */
    static Report report(Context c,JSONArray hist,JSONObject o){
        Report r=new Report();SessionMetrics m=SessionMetrics.of(c,o);r.type=type(c,hist,o);String kind=m.run?"run":"walk";
        // What went well, what held you back.
        if(m.firstHalfPace>0&&m.secondHalfPace>0){double d=(m.firstHalfPace-m.secondHalfPace)/m.firstHalfPace;
            if(d>.03)r.good.add("Negative split: the second half was "+Math.round(d*100)+" % faster than the first.");
            else if(d<-.08)r.limit.add("You slowed by "+Math.round(-d*100)+" % in the second half: the start may have been too quick.");
            else if(d>=-.03)r.good.add("Even pacing from start to finish.");}
        if(m.paceScore>=80)r.good.add("Pace consistency "+m.paceScore+" of 100.");
        if(!Double.isNaN(m.drift)){if(m.drift<5)r.good.add(String.format(Locale.US,"Heart rate held steady for the pace (drift %.1f %%): a sign of a good aerobic base.",m.drift));
            else if(m.drift>=10)r.limit.add(String.format(Locale.US,"Heart rate climbed %.0f bpm at about the same pace (drift %.1f %%). Heat, a fast start, little sleep or too little water can all cause it.",m.driftHalves[1][1]-m.driftHalves[0][1],m.drift));}
        if(m.cadenceAvg>0&&!m.run&&m.cadenceAvg>=110)r.good.add(String.format(Locale.US,"Brisk cadence: %.0f steps a minute.",m.cadenceAvg));
        if(m.stops>=4)r.limit.add(m.stops+" stops broke up the "+kind+".");
        int newBest=0;for(int i=0;i<SessionMetrics.EFFORTS.length;i++)if(m.best.containsKey(SessionMetrics.EFFORTS[i])&&SessionMetrics.rank(c,hist,o,SessionMetrics.EFFORTS[i])==1)newBest++;
        if(newBest>0)r.good.add(newBest==1?"A new personal best.":newBest+" new personal bests.");
        // Against similar sessions: same kind, distance within 30 %, the 90 days before.
        long at=o.optLong("startTimeMs",o.optLong("id"));double ps=0,hs=0,es=0;int n=0,nh=0,ne=0;
        for(int i=0;i<hist.length();i++){JSONObject s=hist.optJSONObject(i);if(s==null||s.optLong("id")==o.optLong("id")||!DaySummary.counts(s))continue;long t=s.optLong("startTimeMs",s.optLong("id"));if(t>=at||at-t>90L*86400000L)continue;
            SessionMetrics x=SessionMetrics.of(c,s);if(x.run!=m.run||x.km<=0||m.km<=0||Math.abs(x.km-m.km)>.3*m.km||x.paceMoving<=0||x.estimatedDistance)continue;
            ps+=x.paceMoving;n++;if(x.hrAvg>0){hs+=x.hrAvg;nh++;}if(x.efficiency>0){es+=x.efficiency;ne++;}}
        if(n>=1&&m.paceMoving>0&&!m.estimatedDistance){double p=ps/n,dp=m.paceMoving-p;StringBuilder b=new StringBuilder(String.format(Locale.US,"Against your %d similar %s%s (%.1f km ± 30 %%, last 90 days): pace %s",n,kind,n==1?"":"s",m.km,Math.abs(dp)<3?"the same":Insights.clock(Math.abs(dp))+(dp<0?" /km faster":" /km slower")));
            if(nh>0&&m.hrAvg>0)b.append(String.format(Locale.US,", heart rate %+d bpm",Math.round(m.hrAvg-hs/nh)));
            if(ne>0&&m.efficiency>0){double de=(m.efficiency-es/ne)/(es/ne)*100;b.append(String.format(Locale.US,", efficiency %+.0f %%",de));if(de>=3)r.good.add(String.format(Locale.US,"More distance per heartbeat than your similar %ss (%+.0f %%).",kind,de));else if(de<=-5)r.limit.add(String.format(Locale.US,"Less distance per heartbeat than usual (%.0f %%): tiredness, heat or hills can do that.",de));}
            r.compare=b.append('.').toString();}
        // Load.
        double[] recent=SessionMetrics.recentEffort(c,hist,o);SessionMetrics.Fitness f=SessionMetrics.fitness(c,hist);double gain=SessionMetrics.fitnessGain(f,o);
        r.load=String.format(Locale.US,"Effort %d%s · fitness %+.1f from this day.",Math.round(m.effort),recent[0]>0&&recent[1]>=3?String.format(Locale.US," (%.1f× your recent average)",m.effort/recent[0]):"",gain);
        // Recovery after it.
        if(m.hrr1>=0&&m.hrEndMoving)r.recovery=String.format(Locale.US,"Heart rate fell %d bpm in the first minute%s after you stopped (from %d bpm).",m.hrr1,m.hrr2>=0?String.format(Locale.US," and %d in two",m.hrr2):"",m.hrEnd);
        // Next.
        r.next=nextWorkout(c,hist,r.type);
        return r;
    }
    /** What to do next, from today's Recovery and the plan. */
    static String nextWorkout(Context c,JSONArray hist,Type t){
        int rec=-1;try{rec=Pro.recovery(c).score;}catch(Exception ignored){}
        if(rec>=0&&rec<34)return "Recovery is "+rec+" % (red): rest or an easy 20-minute walk next, and an early night.";
        boolean hard=t!=null&&t.tone==1;
        try{Trainer.State st=Trainer.build(c,hist);for(Trainer.Workout w:st.week)if(!w.past&&!w.today)return (hard?"After a harder session, keep the next one easy. ":"")+"Next in your plan: "+w.dayName+", "+w.title+(w.minutes>0?" ("+w.minutes+" min)":"")+".";}catch(Exception ignored){}
        return hard?"After a harder session, keep the next one easy: an easy walk or rest.":"Next: another easy session, or one brisk walk to build fitness.";
    }
    /** The latest counted session that ended in the last {@code hours} hours, or null. */
    static JSONObject latest(JSONArray hist,int hours){
        JSONObject best=null;long now=System.currentTimeMillis();
        for(int i=0;i<hist.length();i++){JSONObject o=hist.optJSONObject(i);if(o==null||!DaySummary.counts(o))continue;long end=o.optLong("endTimeMs",o.optLong("startTimeMs",o.optLong("id")));
            if(now-end>hours*3600000L||o.optLong("durationMs")<5*60000L)continue;if(best==null||end>best.optLong("endTimeMs",0))best=o;}
        return best;
    }
}
