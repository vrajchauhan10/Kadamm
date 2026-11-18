package com.stride.run;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.ActivityTransitionEvent;
import com.google.android.gms.location.ActivityTransitionRequest;
import com.google.android.gms.location.ActivityTransitionResult;
import com.google.android.gms.location.DetectedActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Automatic sessions: Android's activity recognition tells Kadamm when you start walking or running, and a
 * session is recorded in the background. The recorder finishes it after a set time without movement.
 */
final class AutoTrack {
    private AutoTrack() {}
    static SharedPreferences s(Context c){return c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE);}
    static boolean enabled(Context c){return s(c).getBoolean("autoTrack",false);}
    /** Minutes without movement before any session finishes by itself; 0 turns it off. */
    static int stopAfterMin(Context c){return s(c).getInt("autoStopMin",3);}
    static boolean backgroundLocation(Context c){
        return Build.VERSION.SDK_INT<29||c.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)==PackageManager.PERMISSION_GRANTED;
    }
    private static PendingIntent intent(Context c){
        return PendingIntent.getBroadcast(c,9,new Intent(c,Receiver.class).setAction("com.stride.run.TRANSITION"),
            PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0));
    }
    /** Registers or removes activity-transition updates to match the setting. Safe to call often. */
    static void sync(Context context){
        Context c=context.getApplicationContext();
        if(!DaySteps.permitted(c))return;
        try{
            if(!enabled(c)){ActivityRecognition.getClient(c).removeActivityTransitionUpdates(intent(c));return;}
            List<ActivityTransition> t=new ArrayList<>();
            // Getting into a vehicle finishes an automatic session; SessionRules then cuts the ride out.
            t.add(new ActivityTransition.Builder().setActivityType(DetectedActivity.IN_VEHICLE).setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER).build());
            for(int type:new int[]{DetectedActivity.WALKING,DetectedActivity.RUNNING}){
                t.add(new ActivityTransition.Builder().setActivityType(type).setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER).build());
                t.add(new ActivityTransition.Builder().setActivityType(type).setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT).build());
            }
            ActivityRecognition.getClient(c).requestActivityTransitionUpdates(new ActivityTransitionRequest(t),intent(c));
        }catch(SecurityException ignored){}
    }

    /**
     * The silent check before a session: when Android sees walking, Kadamm notes the step counter and looks again
     * every 75 seconds for up to 6 minutes, showing nothing. Once 100 steps are taken it starts the real session,
     * back-dated to when the walking began and keeping those steps. If walking stops first, nothing happens.
     */
    static final long CHECK_EVERY_MS=75000L,CHECK_FOR_MS=6*60000L;
    private static PendingIntent checkIntent(Context c){return PendingIntent.getBroadcast(c,10,new Intent(c,Check.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    static void scheduleCheck(Context c){
        android.app.AlarmManager am=c.getSystemService(android.app.AlarmManager.class);if(am==null)return;
        am.setAndAllowWhileIdle(android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,android.os.SystemClock.elapsedRealtime()+CHECK_EVERY_MS,checkIntent(c));
    }
    static void clearCandidate(Context c){
        c.getSharedPreferences("stride_state",Context.MODE_PRIVATE).edit().remove("candidateAt").remove("candidateBase").apply();
        android.app.AlarmManager am=c.getSystemService(android.app.AlarmManager.class);if(am!=null)am.cancel(checkIntent(c));
    }
    public static class Check extends BroadcastReceiver {
        @Override public void onReceive(Context c,Intent i){
            SharedPreferences st=c.getSharedPreferences("stride_state",Context.MODE_PRIVATE);long at=st.getLong("candidateAt",0);
            if(at<=0||st.getBoolean("active",false)||!enabled(c)){clearCandidate(c);return;}
            PendingResult pr=goAsync();
            DaySteps.sample(c,()->{
                try{
                    long now=System.currentTimeMillis(),raw=DaySteps.rawCounter(c),base=st.getLong("candidateBase",raw);long steps=raw>=base?raw-base:raw;
                    if(steps>=SessionRules.MIN_STEPS){
                        String type=st.getString("candidateType","Walk");clearCandidate(c);
                        st.edit().putString("activityMode","outdoor").putString("activityType",type).apply();
                        Intent start=new Intent(c,RecorderService.class).setAction(RecorderService.ACTION_START).putExtra("auto",true).putExtra("type",type).putExtra("preSteps",(int)steps).putExtra("preMs",now-at);
                        try{if(Build.VERSION.SDK_INT>=26)c.startForegroundService(start);else c.startService(start);}catch(Exception ignored){}
                    }else if(now-at<CHECK_FOR_MS)scheduleCheck(c);
                    else clearCandidate(c);
                }finally{pr.finish();}
            });
        }
    }

    /** Receives walking/running transitions, and re-registers after a reboot or app update. */
    public static class Receiver extends BroadcastReceiver {
        @Override public void onReceive(Context c,Intent i){
            String a=i.getAction();
            if(Intent.ACTION_BOOT_COMPLETED.equals(a)||Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)){sync(c);StepSyncJob.schedule(c);return;}
            if(!enabled(c)||!ActivityTransitionResult.hasResult(i))return;
            ActivityTransitionResult result=ActivityTransitionResult.extractResult(i);if(result==null)return;
            for(ActivityTransitionEvent e:result.getTransitionEvents()){
                if(e.getTransitionType()==ActivityTransition.ACTIVITY_TRANSITION_EXIT){clearCandidate(c);continue;}
                if(e.getTransitionType()!=ActivityTransition.ACTIVITY_TRANSITION_ENTER)continue;
                android.content.SharedPreferences st=c.getSharedPreferences("stride_state",Context.MODE_PRIVATE);
                if(e.getActivityType()==DetectedActivity.IN_VEHICLE)clearCandidate(c);
                if(e.getActivityType()==DetectedActivity.IN_VEHICLE){
                    if(st.getBoolean("active",false)&&st.getBoolean("autoSession",false))try{c.startService(new Intent(c,RecorderService.class).setAction(RecorderService.ACTION_STOP));}catch(Exception ignored){}
                    return;
                }
                if(st.getBoolean("active",false))return;
                // A recording was just dropped for having no steps: ignore new starts for 10 minutes unless you've
                // really walked since (30+ steps), so a phone that keeps "seeing" walking doesn't keep the GPS on.
                long dropped=st.getLong("autoDroppedAt",0);
                if(System.currentTimeMillis()-dropped<10*60000L&&DaySteps.todayTotal(c)-st.getLong("autoDroppedSteps",0)<30)return;
                String type=e.getActivityType()==DetectedActivity.RUNNING?"Run":"Walk";
                // Start straight away, so the notification shows from the first step; a recording under 100 steps
                // is never saved, and its notification says so while it counts.
                android.util.Log.i("KadammAuto","walking detected, starting "+type);
                st.edit().putString("activityMode","outdoor").putString("activityType",type).apply();
                try{Intent start=new Intent(c,RecorderService.class).setAction(RecorderService.ACTION_START).putExtra("auto",true).putExtra("type",type);
                    if(Build.VERSION.SDK_INT>=26)c.startForegroundService(start);else c.startService(start);}catch(Exception ex){android.util.Log.w("KadammAuto","start refused",ex);}
                return;
            }
        }
    }
}
