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
    static int stopAfterMin(Context c){return s(c).getInt("autoStopMin",5);}
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
            for(int type:new int[]{DetectedActivity.WALKING,DetectedActivity.RUNNING}){
                t.add(new ActivityTransition.Builder().setActivityType(type).setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER).build());
                t.add(new ActivityTransition.Builder().setActivityType(type).setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT).build());
            }
            ActivityRecognition.getClient(c).requestActivityTransitionUpdates(new ActivityTransitionRequest(t),intent(c));
        }catch(SecurityException ignored){}
    }

    /** Receives walking/running transitions, and re-registers after a reboot or app update. */
    public static class Receiver extends BroadcastReceiver {
        @Override public void onReceive(Context c,Intent i){
            String a=i.getAction();
            if(Intent.ACTION_BOOT_COMPLETED.equals(a)||Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)){sync(c);StepSyncJob.schedule(c);return;}
            if(!enabled(c)||!ActivityTransitionResult.hasResult(i))return;
            ActivityTransitionResult result=ActivityTransitionResult.extractResult(i);if(result==null)return;
            for(ActivityTransitionEvent e:result.getTransitionEvents()){
                if(e.getTransitionType()!=ActivityTransition.ACTIVITY_TRANSITION_ENTER)continue;
                if(c.getSharedPreferences("stride_state",Context.MODE_PRIVATE).getBoolean("active",false))return;
                String type=e.getActivityType()==DetectedActivity.RUNNING?"Run":"Walk";
                c.getSharedPreferences("stride_state",Context.MODE_PRIVATE).edit().putString("activityMode","outdoor").putString("activityType",type).apply();
                try{
                    Intent start=new Intent(c,RecorderService.class).setAction(RecorderService.ACTION_START).putExtra("auto",true).putExtra("type",type);
                    if(Build.VERSION.SDK_INT>=26)c.startForegroundService(start);else c.startService(start);
                }catch(Exception ignored){
                    // Android refused a background start (battery optimisation on): the next transition will retry.
                }
                return;
            }
        }
    }
}
