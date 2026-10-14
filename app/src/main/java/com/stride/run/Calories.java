package com.stride.run;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Session calories from pace, time and body weight, using standard MET values (Compendium of Physical
 * Activities). Reported as active calories: one MET of resting burn is taken off, to match "activity
 * calories" in Samsung Health. Replaces the old flat 65 kcal per km.
 */
final class Calories {
    private Calories() {}

    static double weightKg(Context c){return c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE).getInt("weightKg",70);}

    /** MET for a speed in km/h. Walking rises in steps with pace; running is roughly one MET per km/h. */
    static double met(double kmh,boolean run){
        if(run)return Math.max(6.0,Math.min(16.0,1.02*kmh));
        if(kmh<=0)return 2.0;if(kmh<3.2)return 2.8;if(kmh<4.8)return 3.5;if(kmh<5.6)return 4.3;if(kmh<6.4)return 5.0;return 7.0;
    }
    private static double part(double kg,double km,long ms,boolean run){
        if(ms<=0)return 0;double h=ms/3600000d;return Math.max(0,met(km/h,run)-1)*kg*h;
    }
    /**
     * Active kcal for a session. When GPS distance is missing but steps were counted (indoors, weak signal),
     * the distance is estimated from steps and the configured stride length.
     */
    static long estimate(Context c,double walkKm,long walkMs,double runKm,long runMs,long steps){
        double kg=weightKg(c),km=walkKm+runKm;long ms=walkMs+runMs;
        if(km<.01&&steps>0&&ms>0){
            double est=steps*c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE).getInt("strideLengthCm",72)/100000d;
            walkKm=est*walkMs/(double)ms;runKm=est*runMs/(double)ms;
        }
        return Math.round(part(kg,walkKm,walkMs,false)+part(kg,runKm,runMs,true));
    }
    /** Calories so far in the session being recorded. */
    static long live(Context c){
        SharedPreferences p=c.getSharedPreferences("stride_state",Context.MODE_PRIVATE);
        if(!p.getBoolean("active",false))return 0;
        return estimate(c,p.getFloat("walkDistanceKm",0),p.getLong("walkDurationMs",0),p.getFloat("runDistanceKm",0),p.getLong("runDurationMs",0),p.getInt("steps",0));
    }
}
