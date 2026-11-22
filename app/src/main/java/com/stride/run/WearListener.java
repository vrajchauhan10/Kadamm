package com.stride.run;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Build;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import java.nio.charset.StandardCharsets;

/** Messages from Kadamm on the watch: start, pause or resume, finish, heart rate, and "send me the latest". */
public class WearListener extends WearableListenerService {
    @Override public void onMessageReceived(MessageEvent e){
        String path=e.getPath(),data=e.getData()==null?"":new String(e.getData(),StandardCharsets.UTF_8);
        switch(path){
            case "/kadamm/hr":try{WearBridge.addHeartRate(this,Integer.parseInt(data.trim()));}catch(Exception ignored){}break;
            case "/kadamm/sync":WearBridge.pushToday(this);WearBridge.pushLive(this);break;
            case "/kadamm/start":
                if(getSharedPreferences("stride_state",MODE_PRIVATE).getBoolean("active",false)){WearBridge.pushLive(this);break;}
                String type="Run".equals(data)?"Run":"Walk";
                getSharedPreferences("stride_state",MODE_PRIVATE).edit().putString("activityType",type).putString("activityMode","outdoor").apply();
                send(RecorderService.ACTION_START,true);break;
            case "/kadamm/toggle":send(RecorderService.ACTION_TOGGLE,false);break;
            case "/kadamm/stop":send(RecorderService.ACTION_STOP,false);break;
        }
    }
    private void send(String action,boolean start){
        Intent i=new Intent(this,RecorderService.class).setAction(action);
        try{if(start&&Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}
        catch(Exception ex){
            // Android can refuse to start a session from the background; then one tap on the phone starts it.
            NotificationManager nm=getSystemService(NotificationManager.class);if(nm==null)return;
            if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel("kadamm_watch","From your watch",NotificationManager.IMPORTANCE_HIGH));
            nm.notify(7430,Reminders.rich(this,"kadamm_watch",7430,"Start your walk","Your watch asked to start a session. Tap to begin.","Today",true).build());
        }
        WearBridge.pushLive(this);
    }
}
