package com.stride.run;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

/**
 * One day's sessions added up: totals (distance, moving time, steps, calories, effort, intensity minutes) and
 * averages weighted by time (pace over the whole distance, cadence over moving time). Shown when a day is opened
 * in History, in the nightly session-summary notification, and as shareable text.
 */
final class DaySummary {
    int sessions,auto,skipped;double km,kcal,effort,intensity,cadence=-1;long movingS,elapsedS,steps;String longest="";
    /** Whether a session counts: short recordings under 100 steps (saved before 9.0) and rides are left out. */
    static boolean counts(JSONObject o){return !(o.optBoolean("stepsAvailable",false)&&o.optLong("steps",0)<SessionRules.MIN_STEPS)&&!Insights.vehicle(o);}
    private DaySummary() {}

    static DaySummary of(Context c,JSONArray hist,String day){
        DaySummary d=new DaySummary();double cadSum=0,cadS=0;long longestS=0;
        for(int i=0;i<hist.length();i++){
            JSONObject o=hist.optJSONObject(i);if(o==null||!day.equals(o.optString("day","")))continue;
            if(!counts(o)){d.skipped++;continue;}
            d.sessions++;if(o.optBoolean("auto",false))d.auto++;
            d.km+=o.optDouble("distanceKm",0);d.kcal+=o.optInt("calories",0);if(o.optBoolean("stepsAvailable",false))d.steps+=o.optLong("steps",0);
            long el=o.optLong("durationMs",0)/1000;d.elapsedS+=el;
            SessionMetrics m=SessionMetrics.of(c,o);d.movingS+=m.movingS;d.effort+=m.effort;d.intensity+=m.intensityMin;
            if(m.cadenceAvg>0){cadSum+=m.cadenceAvg*m.movingS;cadS+=m.movingS;}
            if(el>longestS){longestS=el;d.longest=o.optString("time","");}
        }
        if(cadS>0)d.cadence=cadSum/cadS;
        return d;
    }
    /** Average pace over the day's whole distance and moving time, seconds per km; -1 when there's too little distance. */
    double pace(){return km>=.05&&movingS>0&&movingS/km<1800?movingS/km:-1;}
    static String clock(long s){return s>=3600?String.format(Locale.US,"%dh %02dm",s/3600,(s/60)%60):String.format(Locale.US,"%d min",Math.max(0,Math.round(s/60d)));}
    static String pace(double s){return s>0?String.format(Locale.US,"%d:%02d /km",Math.round(s)/60,Math.round(s)%60):"—";}

    /** Per-session averages: time, distance and steps. */
    long avgTime(){return sessions>0?elapsedS/sessions:0;}
    double avgKm(){return sessions>0?km/sessions:0;}
    long avgSteps(){return sessions>0?steps/sessions:0;}
    /** One-line headline: "3 sessions · 6.03 km · 1h 15m". */
    String headline(){return sessions+(sessions==1?" session":" sessions")+String.format(Locale.US," · %.2f km · ",km)+clock(elapsedS);}
    /** The full summary as plain text, for the notification and for sharing. */
    String text(String title){
        StringBuilder b=new StringBuilder(title).append('\n').append(headline());
        if(auto>0)b.append(String.format(Locale.US," (%d auto, %d started by you)",auto,sessions-auto));
        b.append('\n');
        b.append("Total: ");if(steps>0)b.append(String.format(Locale.US,"%,d steps · ",steps));
        b.append(String.format(Locale.US,"%.2f km · %d kcal · %s moving",km,Math.round(kcal),clock(movingS)));
        b.append("\nAverage: pace ").append(pace(pace()));
        if(cadence>0)b.append(String.format(Locale.US," · cadence %d spm",Math.round(cadence)));
        if(sessions>1)b.append(String.format(Locale.US," · %s and %.2f km per session",clock(avgTime()),avgKm()));
        b.append(String.format(Locale.US,"\nEffort %d · %d intensity minutes",Math.round(effort),Math.round(intensity)));
        return b.toString();
    }
}
