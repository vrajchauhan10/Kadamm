package com.stride.run;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The phone's side of Kadamm on Wear OS. Works with any Wear OS watch paired to this phone (Galaxy Watch, Pixel
 * Watch, TicWatch, Fossil and others) through the Wearable Data Layer: the phone shares today's steps, goal and the
 * coach's challenges ("/kadamm/today") and the live session ("/kadamm/live"); the watch sends back start, pause and
 * finish, and its heart rate during a session (see {@link WearListener}).
 * Nothing happens when no watch is paired: the Data Layer just keeps the latest item for when one connects.
 */
final class WearBridge {
    static final String TODAY="/kadamm/today",LIVE="/kadamm/live";
    private static String lastToday="",lastLive="";private static long lastLiveAt=0;
    private WearBridge() {}

    /** Today's summary and challenges. Only sent when something changed. */
    static void pushToday(Context c){
        try{
            JSONObject j=new JSONObject();GoalCapsule.Day d=GoalCapsule.today(c);
            j.put("steps",d.steps).put("goal",d.stepGoal).put("brisk",Math.round(d.brisk)).put("briskGoal",d.briskGoal).put("km",Math.round(d.km*100)/100.0).put("kmGoal",d.kmGoal).put("pct",d.total());
            TrainerCoach.Day cd=TrainerCoach.today(c,DaySteps.sessions(c));
            j.put("greeting",cd.greeting).put("message",cd.message).put("done",cd.done).put("allDone",cd.allDone);
            JSONArray ch=new JSONArray();for(TrainerCoach.Challenge x:cd.list)ch.put(new JSONObject().put("title",x.title).put("progress",x.progress()).put("f",x.fraction()).put("done",x.done));j.put("challenges",ch);
            int[] st=DaySteps.streaks(DaySteps.allDayTotals(c),d.stepGoal);j.put("streak",st[0]);
            String s=j.toString();if(s.equals(lastToday))return;lastToday=s;
            PutDataMapRequest r=PutDataMapRequest.create(TODAY);r.getDataMap().putString("json",s);
            Wearable.getDataClient(c).putDataItem(r.asPutDataRequest());
        }catch(Throwable ignored){}
    }

    /** The live session: running or not, time, distance, steps, pace. Sent at most every 3 seconds. */
    static void pushLive(Context c){
        try{
            SharedPreferences p=c.getSharedPreferences("stride_state",Context.MODE_PRIVATE);boolean active=p.getBoolean("active",false);
            long now=System.currentTimeMillis();if(active&&now-lastLiveAt<3000)return;
            JSONObject j=new JSONObject().put("active",active);
            if(active){long el=p.getLong("elapsed",0);float km=p.getFloat("distance",0);j.put("paused",p.getBoolean("paused",false)).put("elapsedMs",el).put("km",km).put("steps",p.getInt("steps",0)).put("type",p.getString("currentType","Walk"))
                .put("startedAt",p.getLong("sessionStartTimeMs",now)).put("at",now);if(km>.05)j.put("paceS",Math.round(p.getLong("movingMs",el)/1000.0/km));}
            String s=j.toString();if(s.equals(lastLive))return;lastLive=s;lastLiveAt=now;
            PutDataMapRequest r=PutDataMapRequest.create(LIVE);r.getDataMap().putString("json",s);
            Wearable.getDataClient(c).putDataItem(r.asPutDataRequest().setUrgent());
        }catch(Throwable ignored){}
    }

    /** The watch's heart-rate readings during the current session, as [seconds from start, bpm] every 5 s. */
    static void addHeartRate(Context c,int bpm){
        if(bpm<30||bpm>230)return;SharedPreferences p=c.getSharedPreferences("stride_state",Context.MODE_PRIVATE);if(!p.getBoolean("active",false))return;
        long sec=(System.currentTimeMillis()-p.getLong("sessionStartTimeMs",System.currentTimeMillis()))/1000/5*5;
        try{JSONArray a=new JSONArray(p.getString("wearHr","[]"));if(a.length()>0&&a.getJSONArray(a.length()-1).getLong(0)==sec)return;if(a.length()>8000)return;
            a.put(new JSONArray().put(sec).put(bpm));p.edit().putString("wearHr",a.toString()).putInt("wearHrNow",bpm).putLong("wearHrAt",System.currentTimeMillis()).apply();}catch(Exception ignored){}
    }
}
