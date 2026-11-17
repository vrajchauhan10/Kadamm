package com.stride.run;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

/**
 * Decides what a finished recording becomes, before it is saved. Runs on every session, automatic or not:
 * <ul>
 *   <li>Under {@link #MIN_STEPS} steps it isn't a session: nothing is saved (the steps still count for the day,
 *   because the day total comes from the step counter, not from sessions).</li>
 *   <li>Vehicle stretches (15 km/h or faster with almost no steps, minute by minute) are cut out: their distance
 *   and time don't count, and the route shows a gap there. A recording that was only a ride is dropped.</li>
 *   <li>Stepping without going anywhere (steps rising while GPS moves under 15 m in a minute: a treadmill, a
 *   shop, a house) is counted as indoor walking from your stride. When that is most of the session, the whole
 *   session becomes an indoor one.</li>
 * </ul>
 * Each change is written into the session (activityLabel, vehicleKm, indoorKm, autoDetected) so it can be shown.
 */
final class SessionRules {
    private SessionRules() {}
    static final int MIN_STEPS=100;
    private static final double VEHICLE_MS=15/3.6;

    /** The session to save, possibly changed, or null when it should not be saved at all. */
    static JSONObject review(Context c,JSONObject s){
        try{
            boolean stepsOk=s.optBoolean("stepsAvailable",false);long steps=s.optLong("steps",0);
            if(stepsOk&&steps<MIN_STEPS)return null;
            if(!stepsOk&&s.optDouble("distanceKm",0)<.2)return null;
            if(!"indoor".equals(s.optString("activityMode","outdoor")))split(c,s);
            if(Insights.vehicle(s))return null;
            if(stepsOk&&s.optLong("steps",0)<MIN_STEPS)return null;
            return s;
        }catch(Exception e){return s;}
    }

    /** Minute windows over the 5-second timeline: vehicle minutes are removed, stepping-in-place minutes added as indoor distance. */
    private static void split(Context c,JSONObject s)throws Exception{
        JSONArray tl=new JSONArray(s.optString("timeline","[]"));if(tl.length()<12)return;
        double stride=c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE).getInt("strideLengthCm",72)/100d;
        double vehicleM=0,vehicleS=0,indoorM=0;long indoorSteps=0,allSteps=0;java.util.List<long[]> vehicleWindows=new java.util.ArrayList<>();
        int j=0;
        for(int i=1;i<tl.length();i++){
            JSONArray a=tl.getJSONArray(j),b=tl.getJSONArray(i);long dt=b.getLong(0)-a.getLong(0);if(dt<60)continue;
            if(dt>180){j=i;continue;}
            long ds=Math.max(0,b.getLong(1)-a.getLong(1));double dm=Math.max(0,b.getLong(2)-a.getLong(2));allSteps+=ds;
            if(dm/dt>=VEHICLE_MS&&ds<dt/3){vehicleM+=dm;vehicleS+=dt;vehicleWindows.add(new long[]{a.getLong(0),b.getLong(0)});}
            else if(ds>=40&&dm<15){indoorSteps+=ds;indoorM+=ds*stride-dm;}
            j=i;
        }
        double km=s.optDouble("distanceKm",0);StringBuilder note=new StringBuilder();
        if(vehicleM>=100){
            km=Math.max(0,km-vehicleM/1000);s.put("vehicleKm",vehicleM/1000);
            s.put("walkDistanceKm",Math.max(0,s.optDouble("walkDistanceKm",0)-vehicleM/1000));
            s.put("movingMs",Math.max(0,s.optLong("movingMs",0)-(long)(vehicleS*1000)));
            breakRoute(s,vehicleWindows);
            note.append(String.format(Locale.US," · %.1f km in a vehicle left out",vehicleM/1000));
        }
        if(indoorSteps>=200&&allSteps>0){
            boolean mostly=indoorSteps>=allSteps*.7&&km<.15;
            if(mostly){
                s.put("activityMode","indoor");s.put("distanceEstimated",true);s.put("autoDetected","indoor");
                double ikm=s.optLong("steps",0)*stride/1000;km=ikm;s.put("walkDistanceKm",ikm);s.put("indoorKm",ikm);
                s.put("activityLabel","Indoor "+s.optString("type","Walk").toLowerCase(Locale.US)+" · detected: steps without moving, like a treadmill");
            }else{
                double ikm=indoorM/1000;km+=ikm;s.put("indoorKm",ikm);s.put("walkDistanceKm",s.optDouble("walkDistanceKm",0)+ikm);
                note.append(String.format(Locale.US," · %.2f km walked in place, from your steps",ikm));
            }
        }
        s.put("distanceKm",km);
        if(note.length()>0&&!"indoor".equals(s.optString("autoDetected")))s.put("activityLabel",s.optString("activityLabel","Outdoor walk")+note);
        s.put("calories",Calories.estimate(c,s.optDouble("walkDistanceKm",0),s.optLong("walkDurationMs",0),s.optDouble("runDistanceKm",0),s.optLong("runDurationMs",0),s.optLong("steps",0)));
    }
    /** Marks route points inside vehicle windows as gaps, so the map and analysis skip them. */
    private static void breakRoute(JSONObject s,java.util.List<long[]> windows)throws Exception{
        JSONArray r=new JSONArray(s.optString("route","[]"));JSONArray out=new JSONArray();
        for(int i=0;i<r.length();i++){JSONObject p=r.getJSONObject(i);long t=p.optLong("s",-1);boolean in=false;for(long[] w:windows)if(t>=w[0]&&t<=w[1])in=true;
            if(in){if(out.length()>0)out.getJSONObject(out.length()-1).put("vehicleAfter",true);continue;}
            if(out.length()>0&&out.getJSONObject(out.length()-1).optBoolean("vehicleAfter",false)){p.put("break",true);out.getJSONObject(out.length()-1).remove("vehicleAfter");}
            out.put(p);}
        s.put("route",out.toString());
    }
}
