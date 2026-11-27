package com.stride.run;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Weekly and monthly reports, badges, and challenges, all computed from saved sessions and daily step totals. */
final class Progress {
    private Progress() {}

    // ---- Periods and summaries ----
    static final class Period {
        final Calendar start,end;final String label;final int days;final boolean month;String title;
        Period(Calendar start,Calendar end,String label,int days,boolean month){this.start=start;this.end=end;this.label=label;this.days=days;this.month=month;this.title=month?"My month in Kadamm":"My week in Kadamm";}
    }
    private static Calendar midnight(Calendar c){Calendar d=(Calendar)c.clone();d.set(Calendar.HOUR_OF_DAY,0);d.set(Calendar.MINUTE,0);d.set(Calendar.SECOND,0);d.set(Calendar.MILLISECOND,0);return d;}
    /** Monday-to-Sunday week; offset 0 is this week, -1 last week. */
    static Period week(int offset){
        Calendar s=midnight(Calendar.getInstance());int back=(s.get(Calendar.DAY_OF_WEEK)+5)%7;s.add(Calendar.DAY_OF_YEAR,-back+7*offset);
        Calendar e=(Calendar)s.clone();e.add(Calendar.DAY_OF_YEAR,7);Calendar last=(Calendar)e.clone();last.add(Calendar.DAY_OF_YEAR,-1);
        String label=new SimpleDateFormat("d MMM",Locale.getDefault()).format(s.getTime())+" – "+new SimpleDateFormat("d MMM",Locale.getDefault()).format(last.getTime());
        return new Period(s,e,offset==0?"This week · "+label:offset==-1?"Last week · "+label:label,7,false);
    }
    static Period month(int offset){
        Calendar s=midnight(Calendar.getInstance());s.set(Calendar.DAY_OF_MONTH,1);s.add(Calendar.MONTH,offset);
        Calendar e=(Calendar)s.clone();e.add(Calendar.MONTH,1);
        return new Period(s,e,new SimpleDateFormat("MMMM yyyy",Locale.getDefault()).format(s.getTime()),s.getActualMaximum(Calendar.DAY_OF_MONTH),true);
    }
    private static String range(Calendar s,Calendar last){
        SimpleDateFormat f=new SimpleDateFormat(s.get(Calendar.YEAR)==last.get(Calendar.YEAR)?"d MMM":"d MMM yyyy",Locale.getDefault());
        return f.format(s.getTime())+" – "+new SimpleDateFormat("d MMM yyyy",Locale.getDefault()).format(last.getTime());
    }
    /** The last {@code n} days, weeks or months up to and including today; offset steps back by whole periods. */
    static Period last(int n,int unit,int offset){
        Calendar end=midnight(Calendar.getInstance());end.add(Calendar.DAY_OF_YEAR,1);
        Calendar start=(Calendar)end.clone();
        if(unit==Calendar.MONTH)start.add(Calendar.MONTH,-n);else start.add(Calendar.DAY_OF_YEAR,-n*(unit==Calendar.WEEK_OF_YEAR?7:1));
        long span=end.getTimeInMillis()-start.getTimeInMillis();
        if(offset!=0){int len=(int)Math.round(span/86400000d);end.add(Calendar.DAY_OF_YEAR,len*offset);start.add(Calendar.DAY_OF_YEAR,len*offset);}
        return between(start,end,"last "+n+" "+(unit==Calendar.MONTH?(n==1?"month":"months"):unit==Calendar.WEEK_OF_YEAR?(n==1?"week":"weeks"):(n==1?"day":"days")));
    }
    /** Any range: start inclusive, end exclusive, both at midnight. */
    static Period between(Calendar start,Calendar end,String what){
        Calendar s=midnight(start),e=midnight(end);int days=Math.max(1,(int)Math.round((e.getTimeInMillis()-s.getTimeInMillis())/86400000d));
        Calendar last=(Calendar)e.clone();last.add(Calendar.DAY_OF_YEAR,-1);
        Period p=new Period(s,e,range(s,last)+" · "+days+(days==1?" day":" days"),days,days>7);p.title="My "+what+" in Kadamm";return p;
    }
    /** The same number of days immediately before {@code p}, for comparison. */
    static Period before(Period p){Calendar e=(Calendar)p.start.clone(),s=(Calendar)p.start.clone();s.add(Calendar.DAY_OF_YEAR,-p.days);return between(s,e,"");}
    static final class Summary {
        long steps,activeMs,bestSteps;double km;int sessions,goalDays,daysWithSteps,elapsed,future;String bestDay="";long[] daily;String[] labels,names;
    }
    static Summary summarize(Period p,JSONArray hist,Map<String,Long> totals,int goal){
        Summary s=new Summary();s.daily=new long[p.days];s.labels=new String[p.days];s.names=new String[p.days];
        Calendar d=(Calendar)p.start.clone();String today=DaySteps.todayKey();
        for(int i=0;i<p.days;i++,d.add(Calendar.DAY_OF_YEAR,1)){
            String key=DaySteps.key(d.getTimeInMillis());Long v=totals.get(key);long n=v==null?0:v;
            s.names[i]=new SimpleDateFormat("EEE d MMM",Locale.getDefault()).format(d.getTime());
            // Days after today are not counted: they would pull averages and goal-day ratios down.
            if(key.compareTo(today)>0){s.future++;s.labels[i]=p.month?String.valueOf(d.get(Calendar.DAY_OF_MONTH)):new SimpleDateFormat("EEE",Locale.getDefault()).format(d.getTime()).substring(0,1);continue;}
            s.elapsed++;
            s.daily[i]=n;s.steps+=n;if(n>0)s.daysWithSteps++;if(n>=goal&&n>0)s.goalDays++;
            if(n>s.bestSteps){s.bestSteps=n;s.bestDay=new SimpleDateFormat("EEE d MMM",Locale.getDefault()).format(d.getTime());}
            s.labels[i]=p.month?String.valueOf(d.get(Calendar.DAY_OF_MONTH)):new SimpleDateFormat("EEE",Locale.getDefault()).format(d.getTime()).substring(0,1);
        }
        String from=DaySteps.key(p.start.getTimeInMillis()),to=DaySteps.key(p.end.getTimeInMillis());
        for(int i=0;i<hist.length();i++){
            JSONObject o=hist.optJSONObject(i);if(o==null)continue;String day=o.optString("day","");
            if(day.compareTo(from)>=0&&day.compareTo(to)<0){s.sessions++;s.km+=o.optDouble("distanceKm",0);s.activeMs+=o.optLong("durationMs",0);}
        }
        return s;
    }

    // ---- Badges ----
    static final int EASY=0,MEDIUM=1,HARD=2,EXTREME=3;
    static final class Badge {
        final String id,name,desc;final int icon,tier;final double value,target;final String unit;
        Badge(int tier,String id,String name,String desc,int icon,double value,double target,String unit){this.tier=tier;this.id=id;this.name=name;this.desc=desc;this.icon=icon;this.value=value;this.target=target;this.unit=unit;}
        boolean earned(){return value>=target;}
        float fraction(){return target<=0?0:(float)Math.max(0,Math.min(1,value/target));}
        String progressText(){
            if(earned())return "Earned";
            if("".equals(unit))return "Not yet";
            return (unit.equals("km")||unit.equals("m")?String.format(Locale.US,"%.1f / %,.0f %s",value,target,unit):String.format(Locale.US,"%,d / %,d %s",(long)value,(long)target,unit));
        }
    }
    /**
     * Every badge, from easy to extreme, measured against saved sessions and daily step totals. Ids from before
     * 8.2 are kept, so badges already seen don't celebrate again.
     */
    static List<Badge> badges(Context c,JSONArray hist,Map<String,Long> totals,int goal){
        long maxDay=0,allSteps=0;for(Long v:totals.values()){maxDay=Math.max(maxDay,v);allSteps+=v;}
        int[] st=DaySteps.streaks(totals,goal);
        double totalKm=0,maxKm=0,longestMin=0,totalGain=0,maxGain=0,bestWho=0;boolean run=false,early=false,night=false,weekend=false,speedWalk=false,powerWalk=false,sub30=false;int sessions=0,indoor=0,bestWeekSessions=0,sweep=0;
        Map<String,Integer> perWeek=new java.util.HashMap<>();Map<String,Double> whoWeek=new java.util.HashMap<>();Set<String> sessionDays=new HashSet<>();Set<String> weekendDays=new HashSet<>();
        SimpleDateFormat wk=new SimpleDateFormat("YYYY-ww",Locale.US);
        for(int i=0;i<hist.length();i++){
            JSONObject o=hist.optJSONObject(i);if(o==null||Insights.vehicle(o))continue;sessions++;
            double km=o.optDouble("distanceKm",0);totalKm+=km;maxKm=Math.max(maxKm,km);long ms=o.optLong("durationMs",0);longestMin=Math.max(longestMin,ms/60000d);
            if("indoor".equals(o.optString("activityMode")))indoor++;
            if(o.optDouble("runDistanceKm",0)>.1||"Run".equals(o.optString("type")))run=true;
            double gain=o.optDouble("elevGainM",0);totalGain+=gain;maxGain=Math.max(maxGain,gain);
            long start=o.optLong("startTimeMs",0);String day=o.optString("day","");if(!day.isEmpty())sessionDays.add(day);
            if(start>0){
                Calendar cal=Calendar.getInstance();cal.setTimeInMillis(start);int h=cal.get(Calendar.HOUR_OF_DAY);
                if(ms>=5*60000){if(h<7&&h>=4)early=true;if(h>=21)night=true;}
                int dow=cal.get(Calendar.DAY_OF_WEEK);if(dow==Calendar.SATURDAY||dow==Calendar.SUNDAY){Calendar sat=(Calendar)cal.clone();if(dow==Calendar.SUNDAY)sat.add(Calendar.DAY_OF_YEAR,-1);weekendDays.add(DaySteps.key(sat.getTimeInMillis())+(dow==Calendar.SATURDAY?"s":"u"));}
                String w=wk.format(cal.getTime());Integer n=perWeek.get(w);perWeek.put(w,(n==null?0:n)+1);
                if(c!=null){Double m=whoWeek.get(w);whoWeek.put(w,(m==null?0:m)+SessionMetrics.of(c,o).intensityMin);}
            }
            boolean isRun="Run".equals(o.optString("type"))||o.optDouble("runDistanceKm",0)>km*.5;
            double pace=km>0?o.optLong("movingMs",ms)/1000d/km:0;
            if(!isRun&&!"indoor".equals(o.optString("activityMode"))&&km>=2&&pace>0&&pace<=600)speedWalk=true;
            if(!isRun&&!"indoor".equals(o.optString("activityMode"))&&km>=3&&pace>0&&pace<=540)powerWalk=true;
            if(c!=null){Double t=SessionMetrics.of(c,o).best.get(5000);if(t!=null&&t<=1800)sub30=true;}
        }
        for(String d:weekendDays)if(d.endsWith("s")&&weekendDays.contains(d.substring(0,d.length()-1)+"u"))weekend=true;
        for(int n:perWeek.values())bestWeekSessions=Math.max(bestWeekSessions,n);
        for(double v:whoWeek.values())bestWho=Math.max(bestWho,v);
        // Longest run of consecutive days with a session.
        List<String> days=new ArrayList<>(sessionDays);java.util.Collections.sort(days);int runDays=0;String prev=null;
        for(String d:days){runDays=prev!=null&&d.equals(next(prev))?runDays+1:1;sweep=Math.max(sweep,runDays);prev=d;}
        long bestWeek=0,bestMonth=0;Map<String,Long> wsum=new java.util.HashMap<>(),msum=new java.util.HashMap<>();
        for(Map.Entry<String,Long> e:totals.entrySet()){try{Calendar cal=Calendar.getInstance();cal.setTime(new SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(e.getKey()));String w=wk.format(cal.getTime());Long a=wsum.get(w);wsum.put(w,(a==null?0:a)+e.getValue());String m=e.getKey().substring(0,7);Long b=msum.get(m);msum.put(m,(b==null?0:b)+e.getValue());}catch(Exception ignored){}}
        for(long v:wsum.values())bestWeek=Math.max(bestWeek,v);for(long v:msum.values())bestMonth=Math.max(bestMonth,v);
        int W=R.drawable.ic_walk,F=R.drawable.ic_flag,B=R.drawable.ic_bolt,T=R.drawable.ic_trophy,P=R.drawable.ic_place,C=R.drawable.ic_chart,K=R.drawable.ic_calendar,M=R.drawable.ic_timer,H=R.drawable.ic_heart,X=R.drawable.ic_flame,U=R.drawable.ic_run;
        List<Badge> b=new ArrayList<>();
        // Easy
        b.add(new Badge(EASY,"first","First steps","Record your first session",W,sessions,1,"sessions"));
        b.add(new Badge(EASY,"day5k","5K day","5,000 steps in one day",F,maxDay,5000,"steps"));
        b.add(new Badge(EASY,"km1","First kilometre","Cover 1 km in one session",P,maxKm,1,"km"));
        b.add(new Badge(EASY,"streak3","3-day streak","Reach your goal 3 days in a row",B,st[1],3,"days"));
        b.add(new Badge(EASY,"s10","Ten sessions","Record 10 sessions",K,sessions,10,"sessions"));
        b.add(new Badge(EASY,"total10","10 km club","10 km across all sessions",C,totalKm,10,"km"));
        b.add(new Badge(EASY,"steps100k","100K steps","100,000 steps in total",W,allSteps,100000,"steps"));
        b.add(new Badge(EASY,"min30","Half hour","A 30-minute session",M,longestMin,30,"min"));
        b.add(new Badge(EASY,"run","First run","Record some running",U,run?1:0,1,""));
        b.add(new Badge(EASY,"early","Early bird","A session started before 7 am",M,early?1:0,1,""));
        b.add(new Badge(EASY,"night","Night owl","A session started after 9 pm",M,night?1:0,1,""));
        b.add(new Badge(EASY,"weekend","Weekend warrior","Sessions on a Saturday and the Sunday after",K,weekend?1:0,1,""));
        // Medium
        b.add(new Badge(MEDIUM,"day10k","10K day","10,000 steps in one day",F,maxDay,10000,"steps"));
        b.add(new Badge(MEDIUM,"streak7","7-day streak","Reach your goal 7 days in a row",B,st[1],7,"days"));
        b.add(new Badge(MEDIUM,"km5","5 km session","Cover 5 km in one session",P,maxKm,5,"km"));
        b.add(new Badge(MEDIUM,"total50","50 km club","50 km across all sessions",C,totalKm,50,"km"));
        b.add(new Badge(MEDIUM,"s25","Regular","25 sessions recorded",K,sessions,25,"sessions"));
        b.add(new Badge(MEDIUM,"steps500k","Half a million","500,000 steps in total",W,allSteps,500000,"steps"));
        b.add(new Badge(MEDIUM,"min60","Hour of power","A 60-minute session",M,longestMin,60,"min"));
        b.add(new Badge(MEDIUM,"week50k","50K week","50,000 steps in one week",F,bestWeek,50000,"steps"));
        b.add(new Badge(MEDIUM,"who150","WHO week","150 intensity minutes in one week",H,bestWho,150,"min"));
        b.add(new Badge(MEDIUM,"four","Four a week","4 sessions in one week",K,bestWeekSessions,4,"sessions"));
        b.add(new Badge(MEDIUM,"speed","Speed walker","2 km or more at 10:00 /km or faster",X,speedWalk?1:0,1,""));
        b.add(new Badge(MEDIUM,"indoor10","Treadmill trooper","10 indoor sessions",W,indoor,10,"sessions"));
        // Hard
        b.add(new Badge(HARD,"day20k","20K day","20,000 steps in one day",F,maxDay,20000,"steps"));
        b.add(new Badge(HARD,"streak30","30-day streak","Reach your goal 30 days in a row",B,st[1],30,"days"));
        b.add(new Badge(HARD,"goal30","Goal crusher","Reach your goal on 30 days",T,st[2],30,"days"));
        b.add(new Badge(HARD,"km10","10 km session","Cover 10 km in one session",P,maxKm,10,"km"));
        b.add(new Badge(HARD,"total100","100 km club","100 km across all sessions",C,totalKm,100,"km"));
        b.add(new Badge(HARD,"s100","Committed","100 sessions recorded",K,sessions,100,"sessions"));
        b.add(new Badge(HARD,"steps1m","Millionaire","1,000,000 steps in total",W,allSteps,1000000,"steps"));
        b.add(new Badge(HARD,"week100k","100K week","100,000 steps in one week",F,bestWeek,100000,"steps"));
        b.add(new Badge(HARD,"who300","Double dose","300 intensity minutes in one week",H,bestWho,300,"min"));
        b.add(new Badge(HARD,"sweep7","Seven-day sweep","A session on 7 days in a row",K,sweep,7,"days"));
        b.add(new Badge(HARD,"power","Power walker","3 km or more at 9:00 /km or faster",X,powerWalk?1:0,1,""));
        b.add(new Badge(HARD,"min120","Two-hour trek","A 2-hour session",M,longestMin,120,"min"));
        // Extreme
        b.add(new Badge(EXTREME,"day30k","30K day","30,000 steps in one day",F,maxDay,30000,"steps"));
        b.add(new Badge(EXTREME,"streak100","Century streak","Reach your goal 100 days in a row",B,st[1],100,"days"));
        b.add(new Badge(EXTREME,"half","Half marathon","Cover 21.1 km in one session",P,maxKm,21.1,"km"));
        b.add(new Badge(EXTREME,"total500","500 km club","500 km across all sessions",C,totalKm,500,"km"));
        b.add(new Badge(EXTREME,"steps5m","Five million","5,000,000 steps in total",W,allSteps,5000000,"steps"));
        b.add(new Badge(EXTREME,"month500k","500K month","500,000 steps in one month",F,bestMonth,500000,"steps"));
        b.add(new Badge(EXTREME,"s365","Year of walking","365 sessions recorded",K,sessions,365,"sessions"));
        b.add(new Badge(EXTREME,"climb1000","Mountain goat","1,000 m of climbing in total",X,totalGain,1000,"m"));
        b.add(new Badge(EXTREME,"sub30","Sub-30 5K","5 km in under 30 minutes",U,sub30?1:0,1,""));
        return b;
    }
    private static String next(String day){try{Calendar c=Calendar.getInstance();c.setTime(new SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(day));c.add(Calendar.DAY_OF_YEAR,1);return DaySteps.key(c.getTimeInMillis());}catch(Exception e){return "";}}
    private static SharedPreferences seen(Context c){return c.getSharedPreferences("kadamm_badges",Context.MODE_PRIVATE);}
    /** Badges earned since the user last looked at Achievements. */
    static List<Badge> unseen(Context c,List<Badge> all){
        Set<String> s=new HashSet<>(seen(c).getStringSet("seen",new HashSet<>()));List<Badge> out=new ArrayList<>();
        // A badge that's no longer earned (its data was corrected, e.g. steps a relay app had doubled) is forgotten, so
        // it's celebrated properly when it's really earned.
        boolean pruned=false;for(Badge b:all)if(!b.earned()&&s.remove(b.id))pruned=true;if(pruned)seen(c).edit().putStringSet("seen",s).apply();
        for(Badge b:all)if(b.earned()&&!s.contains(b.id))out.add(b);return out;
    }
    static void markSeen(Context c,List<Badge> all){Set<String> s=new HashSet<>();for(Badge b:all)if(b.earned())s.add(b.id);seen(c).edit().putStringSet("seen",s).apply();}

    // ---- Challenges (targets set by the user) ----
    static final class Challenge {
        final String key,title,detail;final double value,target;final int daysLeft;final boolean distance;
        Challenge(String key,String title,String detail,double value,double target,int daysLeft,boolean distance){this.key=key;this.title=title;this.detail=detail;this.value=value;this.target=target;this.daysLeft=daysLeft;this.distance=distance;}
        boolean done(){return value>=target;}
    }
    static int target(Context c,String key,int def){return c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE).getInt(key,def);}
    static List<Challenge> challenges(Context c,JSONArray hist,Map<String,Long> totals,int goal){
        Period m=month(0),w=week(0);Summary ms=summarize(m,hist,totals,goal),ws=summarize(w,hist,totals,goal);
        Calendar now=midnight(Calendar.getInstance());
        int monthLeft=(int)((m.end.getTimeInMillis()-now.getTimeInMillis())/86400000L),weekLeft=(int)((w.end.getTimeInMillis()-now.getTimeInMillis())/86400000L);
        List<Challenge> out=new ArrayList<>();
        out.add(new Challenge("chMonthSteps","Monthly steps",m.label,ms.steps,target(c,"chMonthSteps",250000),monthLeft,false));
        out.add(new Challenge("chMonthKm","Monthly distance","From your Kadamm sessions · "+m.label,ms.km,target(c,"chMonthKm",50),monthLeft,true));
        out.add(new Challenge("chWeekDays","Goal days this week","Days you reach your step goal",ws.goalDays,target(c,"chWeekDays",5),weekLeft,false));
        return out;
    }
}
