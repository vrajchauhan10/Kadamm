package com.stride.run;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

/**
 * Keeps home-screen widgets close to real time without a running service: while at least one Kadamm widget is
 * placed, the step counter is read every 5 minutes (one quick sensor reading) and the widgets redrawn. During a
 * session and while Kadamm is open they update every few seconds anyway. Stops itself when no widget is placed.
 */
public class WidgetTick extends BroadcastReceiver {
    static final long EVERY_MS=5*60000L;
    static boolean anyPlaced(Context c){AppWidgetManager m=AppWidgetManager.getInstance(c);
        for(Class<?> k:new Class<?>[]{StepsWidget.class,WeekWidget.class,GoalsWidget.class}){int[] ids=m.getAppWidgetIds(new ComponentName(c,k));if(ids!=null&&ids.length>0)return true;}return false;}
    private static PendingIntent intent(Context c){return PendingIntent.getBroadcast(c,14,new Intent(c,WidgetTick.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    static void schedule(Context c){
        Context app=c.getApplicationContext();AlarmManager am=app.getSystemService(AlarmManager.class);if(am==null)return;
        if(!anyPlaced(app)){am.cancel(intent(app));return;}
        am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME,SystemClock.elapsedRealtime()+EVERY_MS,intent(app));
    }
    @Override public void onReceive(Context c,Intent i){
        PendingResult pr=goAsync();
        DaySteps.sample(c,()->{try{StepsWidget.refresh(c);schedule(c);}finally{pr.finish();}});
    }
}
