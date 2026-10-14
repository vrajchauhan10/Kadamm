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
 * Kadamm's daily summary: a few plain sentences written on the phone from the day's real numbers (no network,
 * no AI service). Phrasing varies by day but stays the same within a day, so the card doesn't flicker.
 */
final class Coach {
    private Coach() {}

    /** One line of the summary: a kind (steps, compare, sessions, active, left, streak, setup) and its text. */
    static final class Item { final String kind,text; Item(String k,String t){kind=k;text=t;} }
    static final class Summary {
        final String title,headline;final List<Item> items;long steps;int goal;
        Summary(String t,String h,List<Item> i){title=t;headline=h;items=i;}
        /** Notification text: the headline, then one line per item. */
        String text(){StringBuilder b=new StringBuilder(headline);for(Item i:items)b.append("\n• ").append(i.text);return b.toString();}
    }

    private static String pick(String day,int salt,String... options){return options[Math.abs((day+salt).hashCode())%options.length];}

    static Summary today(Context c){
        String day=DaySteps.todayKey();int hour=Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int goal=c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE).getInt("stepGoal",10000);
        long steps=DaySteps.todayTotal(c);boolean data=DaySteps.hasTodayData(c);
        Map<String,Long> totals=DaySteps.allDayTotals(c);
        long sum=0;int days=0;Calendar d=Calendar.getInstance();
        for(int i=1;i<=7;i++){d.add(Calendar.DAY_OF_YEAR,-1);Long v=totals.get(DaySteps.key(d.getTimeInMillis()));if(v!=null&&v>0){sum+=v;days++;}}
        long avg=days==0?0:sum/days;
        JSONArray hist;try{hist=new JSONArray(c.getSharedPreferences("kadamm_history",Context.MODE_PRIVATE).getString("sessions","[]"));}catch(Exception e){hist=new JSONArray();}
        int sessions=0,runs=0;double km=0;long ms=0;
        for(int i=0;i<hist.length();i++){JSONObject o=hist.optJSONObject(i);if(o==null||!day.equals(o.optString("day")))continue;sessions++;km+=o.optDouble("distanceKm",0);ms+=o.optLong("durationMs",0);if(o.optDouble("runDistanceKm",0)>.1)runs++;}
        SharedPreferences hp=c.getSharedPreferences("kadamm_health",Context.MODE_PRIVATE);
        int active=day.equals(hp.getString("healthDate",""))?hp.getInt("healthActiveMin",0):(int)(ms/60000);
        int[] st=DaySteps.streaks(totals,goal);

        List<Item> out=new ArrayList<>();String title=hour>=20?"Your day in Kadamm":"Your day so far";
        if(!data){out.add(new Item("setup","Allow step counting or connect Health Connect to see your day here"));return new Summary(title,"No steps counted yet today",out);}
        long pct=Math.round(steps*100.0/goal);
        String headline=steps>=goal?pick(day,1,"Goal done","You made it","Target reached"):pct>=80?pick(day,1,"Nearly there","So close"):pct>=50?pick(day,1,"Over halfway","Good momentum"):hour>=17?pick(day,1,"A quiet day so far","Room for an evening walk"):pick(day,1,"Off to a start","Building up");
        // Short and sweet: at most three rows, only what matters.
        out.add(new Item("steps",String.format(Locale.US,"%,d / %,d steps · %d%%",steps,goal,pct)));
        if(steps<goal)out.add(new Item("left",String.format(Locale.US,"%,d to go · ~%d min walk",goal-steps,Math.max(5,Math.round((goal-steps)/100.0)))));
        else out.add(new Item("streak",st[0]>1?String.format(Locale.US,"Goal done · %d-day streak",st[0]):"Goal done"));
        if(sessions>0)out.add(new Item("sessions",String.format(Locale.US,"%d %s · %s%d min",sessions,sessions==1?"session":"sessions",km>=.01?String.format(Locale.US,"%.1f km · ",km):"",ms/60000)));
        else if(avg>0&&Math.abs(steps-avg)>=avg*0.1)out.add(new Item(steps>avg?"up":"down",String.format(Locale.US,"%s%,d vs your usual day",steps>avg?"+":"−",Math.abs(steps-avg))));
        Summary result=new Summary(title,headline,out);result.steps=steps;result.goal=goal;return result;
    }
}
