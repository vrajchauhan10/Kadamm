package com.stride.run;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Kadamm Trainer: where you are, what to do today, and where that leads, worked out from your own sessions and
 * daily steps. Six stages, Starter to 10K runner, each reached by meeting all of its checks over the last three
 * weeks. A weekly plan per stage, eased when you're tired (form below −20 or a load ratio above 1.5) and
 * nudged up when you're fresh and under-loaded. Paces come from your own pace zones (threshold = best 30
 * minutes). The forecast runs the same fitness model as Reports forward four weeks, with and without the plan.
 */
final class Trainer {
    private Trainer() {}
    static final String[] STAGES={"Starter","Walker","Brisk walker","Walk-runner","5K runner","10K runner"};

    static final class Check { final String text;final boolean done; Check(String t,boolean d){text=t;done=d;} }
    static final class Workout {
        String dayName,title,detail,why="";int minutes,zone;boolean rest,run,intervals,today,past,done;double effort;
    }
    static final class State {
        int stage;List<Check> next=new ArrayList<>();Workout today;final List<Workout> week=new ArrayList<>();
        double threshold,fitness,form,load;double[] history,plan,stop;long weekStepsProjected,weekStepsSoFar;double monthOnTrack=-1;final List<String> tips=new ArrayList<>();
        String paceRange(int zone){return range(threshold,zone);}
    }

    /** Pace range for a zone (1–6) as "13:28–12:32 /km", from the threshold pace; "" without one. */
    static String range(double thr,int zone){
        if(thr<=0||zone<1)return "";double tk=3600/thr;double lo=zone==1?.70:SessionMetrics.PACE_FROM[zone-2],hi=zone==6?1.12:SessionMetrics.PACE_FROM[zone-1];
        return clock(3600/(tk*lo))+"–"+clock(3600/(tk*hi))+" /km";
    }
    private static String clock(double s){long r=Math.round(s);return String.format(Locale.US,"%d:%02d",r/60,r%60);}

    // kind, minutes, pace zone (0 = rest), intervals flag via title
    private static final Object[][][] PLANS={
        {{"Easy walk",20,2},{"Steps day",0,0},{"Easy walk",20,2},{"Steps day",0,0},{"Brisk walk",20,3},{"Long easy walk",30,2},{"Rest",0,0}},
        {{"Easy walk",30,2},{"Brisk walk",25,3},{"Steps day",0,0},{"Brisk walk",30,3},{"Easy walk",30,2},{"Long walk",45,2},{"Rest",0,0}},
        {{"Easy walk",30,2},{"Brisk walk",35,3},{"Rest",0,0},{"Intervals: 6 × 2 min fast, 2 min easy",30,5},{"Easy walk",30,2},{"Long walk",60,3},{"Rest",0,0}},
        {{"Easy walk",30,2},{"Walk-run: 8 × 1 min run, 2 min walk",30,4},{"Rest",0,0},{"Brisk walk",40,4},{"Easy walk",30,2},{"Long walk-run",45,3},{"Rest",0,0}},
        {{"Easy run",25,2},{"Intervals: 5 × 3 min fast, 2 min easy",35,5},{"Walk",30,1},{"Tempo run",20,4},{"Easy walk",30,1},{"Long easy run",40,2},{"Rest",0,0}},
        {{"Easy run",35,2},{"Intervals: 6 × 4 min fast, 2 min easy",45,5},{"Walk",30,1},{"Tempo run",30,4},{"Easy run",30,2},{"Long run",60,2},{"Rest",0,0}}};
    private static final double[] ZONE_EFFORT={0,.5,1,1.5,2,2.5,2.5};

    static State build(Context c,JSONArray hist){
        State st=new State();SharedPreferences set=c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE);
        Map<String,Long> totals=DaySteps.allDayTotals(c);int goal=set.getInt("stepGoal",10000);
        Calendar mon=midnight();int back=(mon.get(Calendar.DAY_OF_WEEK)+5)%7;mon.add(Calendar.DAY_OF_YEAR,-back);
        // The last three full-or-current weeks: intensity minutes and sessions of 10+ minutes.
        double[] wi=new double[3];int[] ws=new int[3];
        for(int w=0;w<3;w++){Calendar a=(Calendar)mon.clone();a.add(Calendar.DAY_OF_YEAR,-7*w);Calendar b=(Calendar)a.clone();b.add(Calendar.DAY_OF_YEAR,7);
            String fa=DaySteps.key(a.getTimeInMillis()),fb=DaySteps.key(b.getTimeInMillis());wi[w]=SessionMetrics.intensityMinutes(c,hist,fa,fb);
            for(int i=0;i<hist.length();i++){JSONObject o=hist.optJSONObject(i);if(o==null||Insights.vehicle(o))continue;String d=o.optString("day","");if(d.compareTo(fa)>=0&&d.compareTo(fb)<0&&o.optLong("durationMs")>=10*60000L)ws[w]++;}}
        double steps21=0;int n21=0;Calendar d=midnight();for(int i=0;i<21;i++){d.add(Calendar.DAY_OF_YEAR,-1);Long v=totals.get(DaySteps.key(d.getTimeInMillis()));if(v!=null&&v>0){steps21+=v;n21++;}}
        double avgSteps=n21>0?steps21/n21:0;
        double longest=0,maxRunKm=0;String since=DaySteps.key(System.currentTimeMillis()-60L*86400000L);
        for(int i=0;i<hist.length();i++){JSONObject o=hist.optJSONObject(i);if(o==null||Insights.vehicle(o)||o.optString("day","").compareTo(since)<0)continue;longest=Math.max(longest,o.optDouble("distanceKm",0));maxRunKm=Math.max(maxRunKm,o.optDouble("runDistanceKm",0));}
        Map<Integer,double[]> best=SessionMetrics.allTimeBest(c,hist);double b5=best.containsKey(5000)?best.get(5000)[0]:-1,b10=best.containsKey(10000)?best.get(10000)[0]:-1;
        int w150=0;for(double v:wi)if(v>=150)w150++;int w2=0,w3=0;for(int v:ws){if(v>=2)w2++;if(v>=3)w3++;}
        List<List<Check>> req=new ArrayList<>();
        req.add(new ArrayList<>());
        req.add(list(new Check(String.format(Locale.US,"Average 6,000 steps a day (now %,d)",Math.round(avgSteps)),avgSteps>=6000),new Check("2 sessions a week in 2 of the last 3 weeks ("+w2+" of 3)",w2>=2)));
        req.add(list(new Check(String.format(Locale.US,"Average 8,000 steps a day (now %,d)",Math.round(avgSteps)),avgSteps>=8000),new Check("150 intensity minutes in 2 of the last 3 weeks ("+w150+" of 3)",w150>=2)));
        req.add(list(new Check("3 sessions every week for 3 weeks ("+w3+" of 3)",w3>=3),new Check("A 5 km session in the last 60 days"+(longest>0?String.format(Locale.US," (longest %.1f km)",longest):""),longest>=5),new Check("150 intensity minutes every week for 3 weeks ("+w150+" of 3)",w150>=3)));
        req.add(list(new Check(String.format(Locale.US,"Run 2.5 km in one session (best %.1f km)",maxRunKm),maxRunKm>=2.5),new Check("5 km in under 45 minutes"+(b5>0?" (best "+clock(b5)+")":""),b5>0&&b5<=45*60)));
        req.add(list(new Check(String.format(Locale.US,"Run 5 km in one session (best %.1f km)",maxRunKm),maxRunKm>=5),new Check("10 km in under 80 minutes"+(b10>0?" (best "+clock(b10)+")":""),b10>0&&b10<=80*60)));
        st.stage=0;for(int k=1;k<STAGES.length;k++){boolean all=true;for(Check ch:req.get(k))if(!ch.done)all=false;if(!all)break;st.stage=k;}
        if(st.stage<STAGES.length-1)st.next=req.get(st.stage+1);
        st.threshold=SessionMetrics.thresholdPace(c,hist);
        SessionMetrics.Fitness fit=SessionMetrics.fitness(c,hist);st.fitness=fit.fitness;st.form=fit.form;
        List<double[]> vals=new ArrayList<>(fit.days.values());double a7=0,a28=0;int nv=vals.size();for(int i=Math.max(0,nv-28);i<nv;i++){a28+=vals.get(i)[3];if(i>=nv-7)a7+=vals.get(i)[3];}st.load=a28>0?(a7/7)/(a28/28):1;
        boolean tired=st.form<-20||st.load>1.5,fresh=st.form>5&&st.load<.8;
        // The week
        String today=DaySteps.todayKey();String[] names={"Mon","Tue","Wed","Thu","Fri","Sat","Sun"};
        for(int i=0;i<7;i++){
            Object[] p=PLANS[st.stage][i];Workout w=new Workout();w.dayName=names[i];w.title=(String)p[0];w.minutes=(int)p[1];w.zone=(int)p[2];w.rest=w.minutes==0;
            w.intervals=w.title.startsWith("Intervals")||w.title.startsWith("Walk-run");w.run=w.title.contains("run")&&!w.title.startsWith("Walk-run");
            Calendar dd=(Calendar)mon.clone();dd.add(Calendar.DAY_OF_YEAR,i);String key=DaySteps.key(dd.getTimeInMillis());w.today=key.equals(today);w.past=key.compareTo(today)<0;
            if(!w.rest&&tired&&w.zone>=4){w.title="Easy walk (eased: recovering)";w.zone=2;w.minutes=Math.max(20,w.minutes*7/10);w.intervals=false;w.run=false;w.why="Your form is "+Math.round(st.form)+" and load "+String.format(Locale.US,"%.2f",st.load)+": an easy day lets you absorb the work.";}
            else if(!w.rest&&fresh){w.minutes=Math.round(w.minutes*1.1f/5f)*5;w.why="You're fresh (form "+String.format(Locale.US,"%+d",Math.round(st.form))+") and this week is light (load "+String.format(Locale.US,"%.2f",st.load)+"), so a little extra.";}
            else if(!w.rest)w.why=w.zone>=4?"A harder day builds speed; easier days around it let you recover.":w.zone==3?"Brisk walking at moderate intensity counts double toward your heart health.":"Easy minutes build the base everything else stands on.";
            else w.why=w.title.startsWith("Steps")?"No workout today; just reach your step goal.":"Rest is when fitness grows. A relaxed stroll is fine.";
            w.detail=w.rest?(w.title.startsWith("Steps")?String.format(Locale.US,"Reach %,d steps",goal):"Recovery day"):w.minutes+" min"+(st.threshold>0&&w.zone>0?" · "+range(st.threshold,w.zone)+" (Z"+w.zone+")":"");
            w.effort=w.minutes*ZONE_EFFORT[Math.min(6,w.zone)];
            // Done when that day's sessions add up to 70 % of the minutes (rest days: done once the day has passed or the step goal is met).
            long mins=0;for(int k=0;k<hist.length();k++){JSONObject o=hist.optJSONObject(k);if(o!=null&&key.equals(o.optString("day",""))&&!Insights.vehicle(o))mins+=o.optLong("movingMs",o.optLong("durationMs"))/60000;}
            Long sv=totals.get(key);w.done=w.rest?(w.past||(sv!=null&&sv>=goal)):mins>=w.minutes*.7;
            st.week.add(w);if(w.today)st.today=w;
        }
        // Forecast: fitness over the last 4 weeks, then 4 weeks ahead with the plan and with nothing.
        List<double[]> fv=new ArrayList<>(fit.days.values());int h=Math.min(28,fv.size());st.history=new double[h];for(int i=0;i<h;i++)st.history[i]=fv.get(fv.size()-h+i)[0];
        double[] last=fv.isEmpty()?new double[]{0,0}:fv.get(fv.size()-1);st.plan=new double[28];st.stop=new double[28];double cp=last[0],ap=last[1],cs=last[0];
        int dow=(Calendar.getInstance().get(Calendar.DAY_OF_WEEK)+5)%7;
        for(int i=0;i<28;i++){double load=st.week.get((dow+1+i)%7).effort;cp+=(load-cp)/42d;ap+=(load-ap)/7d;cs+=(0-cs)/42d;st.plan[i]=cp;st.stop[i]=cs;}
        // Projections
        for(int i=0;i<7;i++){Calendar dd=(Calendar)mon.clone();dd.add(Calendar.DAY_OF_YEAR,i);String k=DaySteps.key(dd.getTimeInMillis());if(k.compareTo(today)<=0){Long v=totals.get(k);st.weekStepsSoFar+=v==null?0:v;}}
        double recent=0;int rn=0;Calendar r=midnight();for(int i=0;i<14;i++){r.add(Calendar.DAY_OF_YEAR,-1);Long v=totals.get(DaySteps.key(r.getTimeInMillis()));if(v!=null){recent+=v;rn++;}}
        double perDay=rn>0?recent/rn:0;st.weekStepsProjected=Math.round(st.weekStepsSoFar+perDay*(6-dow));
        Progress.Period m=Progress.month(0);Progress.Summary ms=Progress.summarize(m,hist,totals,goal);int target=set.getInt("chMonthSteps",250000);
        double projMonth=ms.steps+perDay*ms.future;st.monthOnTrack=Math.min(1.5,projMonth/Math.max(1,target));
        tips(c,hist,st,wi[0],perDay);
        return st;
    }
    private static void tips(Context c,JSONArray hist,State st,double weekIntensity,double perDay){
        if(st.form<-20)st.tips.add("You've done a lot lately (form "+Math.round(st.form)+"). Keep today easy and sleep well; fitness grows in recovery.");
        if(weekIntensity<150)st.tips.add(Math.round(150-weekIntensity)+" more intensity minutes this week reaches the WHO's 150. Two 25-minute brisk walks would do it.");
        // When you walk
        double[] h=null;JSONObject md=DaySteps.minuteDays(c);double eve=0,all=0;
        for(java.util.Iterator<String> it=md.keys();it.hasNext();){JSONObject x=md.optJSONObject(it.next());JSONArray a=x==null?null:x.optJSONArray("h");if(a==null)continue;for(int i=0;i<24;i++){double v=a.optDouble(i);all+=v;if(i>=18)eve+=v;}}
        if(all>0&&eve/all>.5)st.tips.add("Most of your walking is after 6 pm. A 10-minute walk in the morning or after lunch spreads the benefit through the day.");
        // Cadence
        double cad=0,cs=0;String since=DaySteps.key(System.currentTimeMillis()-14L*86400000L);
        for(int i=0;i<hist.length();i++){JSONObject o=hist.optJSONObject(i);if(o==null||o.optString("day","").compareTo(since)<0)continue;SessionMetrics m=SessionMetrics.of(c,o);if(m.cadenceAvg>0){cad+=m.cadenceAvg*m.movingS;cs+=m.movingS;}}
        if(cs>0&&cad/cs<105)st.tips.add(String.format(Locale.US,"Your walking cadence is %d steps a minute. On brisk days aim for 110+; quicker, shorter steps are the easiest way to speed up.",Math.round(cad/cs)));
        if(st.stage<STAGES.length-1){int left=0;for(Check ch:st.next)if(!ch.done)left++;st.tips.add(left==1?"One check left to reach "+STAGES[st.stage+1]+".":left+" checks left to reach "+STAGES[st.stage+1]+".");}
    }
    @SafeVarargs private static <T> List<T> list(T... a){List<T> l=new ArrayList<>();for(T x:a)l.add(x);return l;}
    private static Calendar midnight(){Calendar c=Calendar.getInstance();c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return c;}
}
