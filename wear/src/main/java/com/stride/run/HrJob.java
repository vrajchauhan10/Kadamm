package com.stride.run;

import android.Manifest;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * All-day heart rate on any Wear OS watch, with the standard heart-rate sensor: every 15 minutes the watch takes a
 * reading (the median of up to 5 good samples within 40 seconds) and sends the readings not yet delivered to the phone
 * ("/kadamm/passive"). The phone uses them for resting heart rate, strain and stress when Health Connect has no heart
 * rate. Needs body-sensor access, and on Wear OS 4+ "all the time" access.
 */
public class HrJob extends JobService implements SensorEventListener {
    static final int ID=4101,NOW=4102;
    private SensorManager sm;private JobParameters params;private final List<Integer> got=new ArrayList<>();private final Handler h=new Handler(Looper.getMainLooper());

    static boolean allowed(Context c){
        if(c.checkSelfPermission(Manifest.permission.BODY_SENSORS)!=PackageManager.PERMISSION_GRANTED)return false;
        return Build.VERSION.SDK_INT<33||c.checkSelfPermission("android.permission.BODY_SENSORS_BACKGROUND")==PackageManager.PERMISSION_GRANTED;
    }
    static void schedule(Context c){
        JobScheduler js=c.getSystemService(JobScheduler.class);if(js==null)return;
        if(js.getPendingJob(ID)==null)js.schedule(new JobInfo.Builder(ID,new ComponentName(c,HrJob.class)).setPeriodic(15*60000L).setPersisted(true).build());
    }
    /** A reading now (when the phone asks for a fresh one). */
    static void now(Context c){JobScheduler js=c.getSystemService(JobScheduler.class);if(js!=null)js.schedule(new JobInfo.Builder(NOW,new ComponentName(c,HrJob.class)).setOverrideDeadline(0).build());}

    @Override public boolean onStartJob(JobParameters p){
        if(!allowed(this))return false;sm=getSystemService(SensorManager.class);Sensor s=sm==null?null:sm.getDefaultSensor(Sensor.TYPE_HEART_RATE);if(s==null)return false;
        params=p;got.clear();sm.registerListener(this,s,SensorManager.SENSOR_DELAY_NORMAL);h.postDelayed(this::finish,40000);return true;
    }
    @Override public boolean onStopJob(JobParameters p){stopSensor();return false;}
    @Override public void onSensorChanged(SensorEvent e){
        if(e.accuracy<SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM||e.values.length==0)return;int v=Math.round(e.values[0]);if(v<30||v>230)return;got.add(v);if(got.size()>=5)finish();
    }
    @Override public void onAccuracyChanged(Sensor s,int a){}
    private void stopSensor(){if(sm!=null)sm.unregisterListener(this);h.removeCallbacksAndMessages(null);}
    private void finish(){
        stopSensor();if(params==null)return;JobParameters p=params;params=null;
        if(!got.isEmpty()){List<Integer> s=new ArrayList<>(got);Collections.sort(s);queue(this,System.currentTimeMillis(),s.get(s.size()/2));}
        send(this);jobFinished(p,false);
    }
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("kadamm_watch",MODE_PRIVATE);}
    static void queue(Context c,long at,int bpm){
        try{JSONArray a=new JSONArray(prefs(c).getString("queue","[]"));a.put(new JSONArray().put(at).put(bpm));JSONArray keep=new JSONArray();for(int i=Math.max(0,a.length()-500);i<a.length();i++)keep.put(a.get(i));prefs(c).edit().putString("queue",keep.toString()).apply();}catch(Exception ignored){}
    }
    /** Sends the queued readings; they're cleared once the phone has them. */
    static void send(Context c){
        String q=prefs(c).getString("queue","[]");if(q.equals("[]"))return;
        try{String body=new JSONObject().put("hr",new JSONArray(q)).toString();
            Wearable.getNodeClient(c).getConnectedNodes().addOnSuccessListener(nodes->{for(Node n:nodes)Wearable.getMessageClient(c).sendMessage(n.getId(),"/kadamm/passive",body.getBytes(StandardCharsets.UTF_8)).addOnSuccessListener(x->{
                try{JSONArray now=new JSONArray(prefs(c).getString("queue","[]")),sent=new JSONArray(q),left=new JSONArray();for(int i=sent.length();i<now.length();i++)left.put(now.get(i));prefs(c).edit().putString("queue",left.toString()).apply();}catch(Exception ignored){}});});
        }catch(Exception ignored){}
    }
    /** The watch's name, model and sensors, for the phone's "connected watch" card. */
    static void hello(Context c){
        try{String name=Settings.Global.getString(c.getContentResolver(),"device_name");if(name==null||name.isEmpty())name=Build.MODEL;
            JSONArray caps=new JSONArray();SensorManager sm=c.getSystemService(SensorManager.class);PackageManager pm=c.getPackageManager();
            if(sm!=null){if(sm.getDefaultSensor(Sensor.TYPE_HEART_RATE)!=null)caps.put("heart rate");if(sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)!=null)caps.put("steps");if(sm.getDefaultSensor(Sensor.TYPE_PRESSURE)!=null)caps.put("barometer");
                for(Sensor s:sm.getSensorList(Sensor.TYPE_ALL)){String n=(s.getName()+" "+s.getStringType()).toLowerCase(java.util.Locale.US);
                    if((n.contains("spo2")||n.contains("oxygen"))&&!has(caps,"blood oxygen"))caps.put("blood oxygen");if((n.contains("skin")&&n.contains("temp"))&&!has(caps,"skin temperature"))caps.put("skin temperature");
                    if((n.contains("ecg")||n.contains("electrocardio"))&&!has(caps,"ECG"))caps.put("ECG");if((n.contains("bia")||n.contains("body comp"))&&!has(caps,"body composition"))caps.put("body composition");}}
            if(pm.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS))caps.put("GPS");
            String body=new JSONObject().put("name",name).put("model",Build.MANUFACTURER+" "+Build.MODEL).put("os","Wear OS "+Build.VERSION.RELEASE).put("caps",caps).put("allDay",allowed(c)).toString();
            Wearable.getNodeClient(c).getConnectedNodes().addOnSuccessListener(nodes->{for(Node n:nodes)Wearable.getMessageClient(c).sendMessage(n.getId(),"/kadamm/hello",body.getBytes(StandardCharsets.UTF_8));});
        }catch(Exception ignored){}
    }
    private static boolean has(JSONArray a,String s){for(int i=0;i<a.length();i++)if(s.equals(a.optString(i)))return true;return false;}
}
