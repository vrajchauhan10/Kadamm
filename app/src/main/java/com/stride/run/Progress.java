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
    static final class Badge {
        final String id,name,desc;final int icon;final double value,target;final String unit;
        Badge(String id,String name,String desc,int icon,double value,double target,String unit){this.id=id;this.name=name;this.desc=desc;this.icon=icon;this.value=value;this.target=target;this.unit=unit;}
        boolean earned(){return value>=target;}
        String progressText(){
            if(earned())return "Earned";
            if("".equals(unit))return "Not yet";
            return (unit.equals("km")?String.format(Locale.US,"%.1f / %.0f km",value,target):String.format(Locale.US,"%,d / %,d %s",(long)value,(long)target,unit));
        }
    }
    static List<Badge> badges(JSONArray hist,Map<String,Long> totals,int goal){
        long maxDay=0;for(Long v:totals.values())maxDay=Math.max(maxDay,v);
        int[] st=DaySteps.streaks(totals,goal);
        double totalKm=0,maxKm=0;boolean run=false,early=false,night=false;int sessions=hist.length();
        for(int i=0;i<hist.length();i++){
            JSONObject o=hist.optJSONObject(i);if(o==null)continue;double km=o.optDouble("distanceKm",0);totalKm+=km;maxKm=Math.max(maxKm,km);
            if(o.optDouble("runDistanceKm",0)>.1||"Run".equals(o.optString("type")))run=true;
            long start=o.optLong("startTimeMs",0);if(start>0&&o.optLong("durationMs",0)>=5*60000){Calendar c=Calendar.getInstance();c.setTimeInMillis(start);int h=c.get(Calendar.HOUR_OF_DAY);if(h<7&&h>=4)early=true;if(h>=21)night=true;}
        }
        List<Badge> b=new ArrayList<>();
        b.add(new Badge("first","First steps","Record your first session",R.drawable.ic_walk,sessions,1,"sessions"));
        b.add(new Badge("day10k","10K day","10,000 steps in one day",R.drawable.ic_flag,maxDay,10000,"steps"));
        b.add(new Badge("day20k","20K day","20,000 steps in one day",R.drawable.ic_flag,maxDay,20000,"steps"));
        b.add(new Badge("streak7","7-day streak","Reach your goal 7 days in a row",R.drawable.ic_bolt,st[1],7,"days"));
        b.add(new Badge("streak30","30-day streak","Reach your goal 30 days in a row",R.drawable.ic_bolt,st[1],30,"days"));
        b.add(new Badge("goal30","Goal crusher","Reach your goal on 30 days",R.drawable.ic_trophy,st[2],30,"days"));
        b.add(new Badge("run","First run","Record some running",R.drawable.ic_run,run?1:0,1,""));
        b.add(new Badge("km5","5 km session","Cover 5 km in one session",R.drawable.ic_place,maxKm,5,"km"));
        b.add(new Badge("km10","10 km session","Cover 10 km in one session",R.drawable.ic_place,maxKm,10,"km"));
        b.add(new Badge("total50","50 km club","50 km across all sessions",R.drawable.ic_chart,totalKm,50,"km"));
        b.add(new Badge("total100","100 km club","100 km across all sessions",R.drawable.ic_chart,totalKm,100,"km"));
        b.add(new Badge("total500","500 km club","500 km across all sessions",R.drawable.ic_chart,totalKm,500,"km"));
        b.add(new Badge("s25","Regular","25 sessions recorded",R.drawable.ic_calendar,sessions,25,"sessions"));
        b.add(new Badge("s100","Committed","100 sessions recorded",R.drawable.ic_calendar,sessions,100,"sessions"));
        b.add(new Badge("early","Early bird","A session started before 7 am",R.drawable.ic_timer,early?1:0,1,""));
        b.add(new Badge("night","Night owl","A session started after 9 pm",R.drawable.ic_timer,night?1:0,1,""));
        return b;
    }
    private static SharedPreferences seen(Context c){return c.getSharedPreferences("kadamm_badges",Context.MODE_PRIVATE);}
    /** Badges earned since the user last looked at Achievements. */
    static List<Badge> unseen(Context c,List<Badge> all){
        Set<String> s=seen(c).getStringSet("seen",new HashSet<>());List<Badge> out=new ArrayList<>();
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
