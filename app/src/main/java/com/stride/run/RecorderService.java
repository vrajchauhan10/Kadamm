package com.stride.run;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class RecorderService extends Service implements LocationListener, SensorEventListener {
    public static final String ACTION_START="com.stride.run.START", ACTION_TOGGLE="com.stride.run.TOGGLE", ACTION_STOP="com.stride.run.STOP", ACTION_DISCARD="com.stride.run.DISCARD", ACTION_SET_TYPE="com.stride.run.SET_TYPE", ACTION_NOTIFICATION_TOGGLE="com.stride.run.NOTIFICATION_TOGGLE", ACTION_NOTIFICATION_FINISH="com.stride.run.NOTIFICATION_FINISH", ACTION_RESTORE="com.stride.run.RESTORE";
    static volatile boolean running=false;
    private static final String CHANNEL="stride_tracking";private static final int NOTIFICATION=7;private int dailyStepGoal(){return getSharedPreferences("kadamm_settings",MODE_PRIVATE).getInt("stepGoal",10000);}
    private android.content.SharedPreferences prefs;private LocationManager locations;private SensorManager sensors;private Sensor stepCounter,stepDetector;private Handler handler;private JSONArray route;private Location last;private boolean active=false,paused=false,manualType=false,stepsAvailable=false,gpsConfirmed=false;private long elapsedBase=0,runStart=0,lastMovementElapsed=0,lastStepElapsed=0,lastStatsElapsed=0,sessionStartedAt=0,lastNotificationElapsed=0;private float rawStepStart=-1,lastRawStep=-1;private int lastDistanceStepCount=0;private int detectedSteps=0,baseSteps=0,runCandidates=0,walkCandidates=0,gpsCandidateCount=0;private double gpsCandidateDistance=0;private String currentType="Walk",candidateType="Walk";private long walkDuration=0,runDuration=0;private double walkDistance=0,runDistance=0,smoothedSpeed=-1;
    private final Runnable ticker=new Runnable(){public void run(){if(active){if(!paused)save();updateTrackingNotification();handler.postDelayed(this,1000);}}};
    @Override public void onCreate(){super.onCreate();running=true;prefs=getSharedPreferences("stride_state",MODE_PRIVATE);locations=(LocationManager)getSystemService(LOCATION_SERVICE);sensors=(SensorManager)getSystemService(SENSOR_SERVICE);stepCounter=sensors.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);stepDetector=sensors.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);handler=new Handler(Looper.getMainLooper());}
    @Override public int onStartCommand(Intent intent,int flags,int id){// A null intent is Android restarting the service after killing it: pick the session back up, never start a new one.
        String action=intent==null||intent.getAction()==null?ACTION_RESTORE:intent.getAction();if(!active&&!ACTION_START.equals(action))restoreSession();if(ACTION_RESTORE.equals(action)){if(!active)stopSelf();}else if(ACTION_START.equals(action))startSession();else if(ACTION_TOGGLE.equals(action)||ACTION_NOTIFICATION_TOGGLE.equals(action))toggle();else if(ACTION_STOP.equals(action)||ACTION_NOTIFICATION_FINISH.equals(action))stopSession();else if(ACTION_DISCARD.equals(action))discardSession();else if(ACTION_SET_TYPE.equals(action)&&active){manualType=true;currentType=intent.getStringExtra("type");if(!"Run".equals(currentType))currentType="Walk";prefs.edit().putString("currentType",currentType).putBoolean("manualType",true).apply();}return START_STICKY;}
    private void startSession(){if(active){toggle();return;}active=true;paused=false;manualType=false;elapsedBase=0;sessionStartedAt=System.currentTimeMillis();runStart=SystemClock.elapsedRealtime();lastStatsElapsed=runStart;lastMovementElapsed=0;lastStepElapsed=0;gpsConfirmed=false;gpsCandidateCount=0;gpsCandidateDistance=0;lastRawStep=-1;last=null;kVar=-1;altRef=Double.NaN;altSmooth=Double.NaN;elevGain=0;movingDuration=0;route=new JSONArray();detectedSteps=0;baseSteps=0;rawStepStart=-1;currentType=prefs.getString("activityType","Walk");if(!"Run".equals(currentType))currentType="Walk";candidateType=currentType;walkDuration=runDuration=0;walkDistance=runDistance=0;runCandidates=walkCandidates=0;smoothedSpeed=-1;prefs.edit().putLong("sessionStartTimeMs",sessionStartedAt).putBoolean("active",true).putBoolean("paused",false).putBoolean("moving",false).putBoolean("manualType",false).putString("currentType",currentType).putLong("elapsed",0).putFloat("distance",0).putInt("steps",0).putString("route","[]").putFloat("accuracy",-1).putBoolean("gpsFix",false).putBoolean("stepsAvailable",false).putFloat("elevGainM",0).putBoolean("hasElevation",false).putLong("movingMs",0).putFloat("speedKmh",0).apply();createChannel();promoteForeground(notification("indoor".equals(prefs.getString("activityMode","outdoor"))?"Indoor session · counting steps":"Waiting for GPS · walking detection ready"));if(!"indoor".equals(prefs.getString("activityMode","outdoor")))beginLocation();registerSteps();handler.post(ticker);}
    /** Rebuilds an interrupted session from its last saved state and keeps recording. */
    private void restoreSession(){
        if(!prefs.getBoolean("active",false))return;
        active=true;paused=prefs.getBoolean("paused",false);manualType=prefs.getBoolean("manualType",false);
        elapsedBase=prefs.getLong("elapsed",0);sessionStartedAt=prefs.getLong("sessionStartTimeMs",System.currentTimeMillis()-elapsedBase);
        runStart=SystemClock.elapsedRealtime();lastStatsElapsed=runStart;lastMovementElapsed=0;lastStepElapsed=0;gpsConfirmed=false;gpsCandidateCount=0;gpsCandidateDistance=0;last=null;kVar=-1;altRef=Double.NaN;altSmooth=Double.NaN;elevGain=prefs.getFloat("elevGainM",0);movingDuration=prefs.getLong("movingMs",0);
        try{route=new JSONArray(prefs.getString("route","[]"));}catch(Exception e){route=new JSONArray();}
        baseSteps=prefs.getInt("steps",0);detectedSteps=baseSteps;rawStepStart=-1;lastRawStep=-1;
        currentType=prefs.getString("currentType","Walk");candidateType=currentType;
        walkDuration=prefs.getLong("walkDurationMs",0);runDuration=prefs.getLong("runDurationMs",0);walkDistance=prefs.getFloat("walkDistanceKm",0);runDistance=prefs.getFloat("runDistanceKm",0);
        createChannel();
        if(!promoteForeground(notification(paused?"Activity paused":"Recording resumed"))){active=false;stopSelf();return;}
        if(!paused){if(!"indoor".equals(prefs.getString("activityMode","outdoor")))beginLocation();registerSteps();}
        handler.removeCallbacks(ticker);handler.post(ticker);
    }
    /**
     * Called when the app opens. A session whose service died is resumed if it was alive in the last
     * ten minutes; an older one is saved to History as it stood, so the app never shows a stuck timer.
     */
    static void recover(Context c){
        android.content.SharedPreferences p=c.getSharedPreferences("stride_state",MODE_PRIVATE);
        if(!p.getBoolean("active",false)||running)return;
        // Only the app itself decides to give up on a session; a refused background restart waits for it.
        if(!(c instanceof android.app.Activity))return;
        long elapsed=p.getLong("elapsed",0),started=p.getLong("sessionStartTimeMs",System.currentTimeMillis()-elapsed);
        long beat=p.getLong("heartbeatMs",started+elapsed);
        if(c instanceof android.app.Activity&&System.currentTimeMillis()-beat<90*60000L){
            try{Intent i=new Intent(c,RecorderService.class).setAction(ACTION_RESTORE);if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);else c.startService(i);return;}catch(Exception ignored){}
        }
        float km=p.getFloat("distance",0);int steps=p.getInt("steps",0);
        if(elapsed>=30000||steps>0||km>0){
            try{
                JSONObject s=new JSONObject();long wd=p.getLong("walkDurationMs",0),rd=p.getLong("runDurationMs",0);String type=p.getString("currentType","Walk"),mode=p.getString("activityMode","outdoor");
                s.put("id",beat);s.put("startTimeMs",started);s.put("endTimeMs",beat);s.put("day",new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date(beat)));s.put("date",new SimpleDateFormat("EEE, d MMM yyyy",Locale.getDefault()).format(new Date(beat)));s.put("time",new SimpleDateFormat("h:mm a",Locale.getDefault()).format(new Date(beat)));
                s.put("type",wd>0&&rd>0?"Mixed":type);s.put("activityMode",mode);s.put("activityLabel",("indoor".equals(mode)?"Indoor ":"Outdoor ")+type.toLowerCase(Locale.US)+" · recovered after an interruption");s.put("distanceEstimated","indoor".equals(mode));
                s.put("durationMs",elapsed);s.put("movingMs",p.getLong("movingMs",elapsed));if(p.getBoolean("hasElevation",false))s.put("elevGainM",p.getFloat("elevGainM",0));s.put("distanceKm",km);s.put("steps",steps);s.put("stepsAvailable",p.getBoolean("stepsAvailable",false));s.put("calories",Math.round(km*65));
                s.put("walkDistanceKm",p.getFloat("walkDistanceKm",0));s.put("runDistanceKm",p.getFloat("runDistanceKm",0));s.put("walkDurationMs",wd);s.put("runDurationMs",rd);s.put("route",p.getString("route","[]"));
                MainActivity.saveSession(c.getSharedPreferences("kadamm_history",MODE_PRIVATE),s);
            }catch(Exception ignored){}
        }
        p.edit().putBoolean("active",false).putBoolean("paused",false).putBoolean("moving",false).putBoolean("gpsFix",false).remove("sessionStartTimeMs").remove("heartbeatMs").putLong("elapsed",0).putFloat("distance",0).putInt("steps",0).putString("route","[]").apply();
    }
    private void toggle(){if(!active)return;if(paused){paused=false;last=null;kVar=-1;runStart=SystemClock.elapsedRealtime();lastStatsElapsed=runStart;rawStepStart=-1;lastRawStep=-1;detectedSteps=baseSteps;if(!"indoor".equals(prefs.getString("activityMode","outdoor")))beginLocation();registerSteps();}else{save();elapsedBase+=SystemClock.elapsedRealtime()-runStart;paused=true;baseSteps=prefs.getInt("steps",baseSteps);detectedSteps=baseSteps;rawStepStart=-1;locations.removeUpdates(this);sensors.unregisterListener(this);}prefs.edit().putBoolean("paused",paused).apply();save();if(Build.VERSION.SDK_INT>=24)promoteForeground(notification(paused?"Activity paused":"Recording your activity"));}
    private void stopSession(){if(!active){stopSelf();return;}save();long duration=prefs.getLong("elapsed",0);float km=prefs.getFloat("distance",0);int stepCount=prefs.getInt("steps",0);try{JSONObject session=new JSONObject();long now=System.currentTimeMillis();session.put("id",now);session.put("startTimeMs",sessionStartedAt);session.put("endTimeMs",now);session.put("day",new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date(now)));session.put("date",new SimpleDateFormat("EEE, d MMM yyyy",Locale.getDefault()).format(new Date(now)));session.put("time",new SimpleDateFormat("h:mm a",Locale.getDefault()).format(new Date(now)));session.put("type",walkDuration>0&&runDuration>0?"Mixed":currentType);session.put("activityMode",prefs.getString("activityMode","outdoor"));session.put("activityLabel",("indoor".equals(prefs.getString("activityMode","outdoor"))?"Indoor ":"Outdoor ")+currentType.toLowerCase(Locale.US));session.put("distanceEstimated","indoor".equals(prefs.getString("activityMode","outdoor"))&&!prefs.getBoolean("distanceCorrected",false));session.put("durationMs",duration);session.put("movingMs",movingDuration);if(prefs.getBoolean("hasElevation",false))session.put("elevGainM",elevGain);session.put("distanceKm",km);session.put("steps",stepCount);session.put("stepsAvailable",stepsAvailable);session.put("calories",Math.round(km*65));session.put("walkDistanceKm",walkDistance);session.put("runDistanceKm",runDistance);session.put("walkDurationMs",walkDuration);session.put("runDurationMs",runDuration);session.put("route",route==null?prefs.getString("route","[]"):route.toString());MainActivity.saveSession(getSharedPreferences("kadamm_history",MODE_PRIVATE),session);prefs.edit().putString("lastSession",session.toString()).apply();}catch(Exception ignored){}cleanup();}
    private void discardSession(){if(!active){stopSelf();return;}cleanup();prefs.edit().putLong("elapsed",0).putFloat("distance",0).putInt("steps",0).putString("route","[]").putBoolean("moving",false).putBoolean("gpsFix",false).apply();}
    private void cleanup(){active=false;paused=false;handler.removeCallbacks(ticker);locations.removeUpdates(this);sensors.unregisterListener(this);prefs.edit().putBoolean("active",false).putBoolean("paused",false).remove("sessionStartTimeMs").putBoolean("moving",false).apply();if(Build.VERSION.SDK_INT>=24)stopForeground(STOP_FOREGROUND_REMOVE);else stopForeground(true);stopSelf();}
    private void beginLocation(){boolean fine=checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;boolean coarse=checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;if(!fine&&!coarse)return;try{if(fine&&locations.isProviderEnabled(LocationManager.GPS_PROVIDER))locations.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,0,this,Looper.getMainLooper());else locations.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,2000,0,this,Looper.getMainLooper());}catch(SecurityException|IllegalArgumentException ignored){}}
    private void registerSteps(){stepsAvailable=(stepCounter!=null||stepDetector!=null)&&DaySteps.permitted(this);prefs.edit().putBoolean("stepsAvailable",stepsAvailable).apply();if(stepCounter!=null)sensors.registerListener(this,stepCounter,SensorManager.SENSOR_DELAY_NORMAL);else if(stepDetector!=null)sensors.registerListener(this,stepDetector,SensorManager.SENSOR_DELAY_NORMAL);}
    @Override public void onLocationChanged(Location location){if(!active||paused||location==null||"indoor".equals(prefs.getString("activityMode","outdoor")))return;float acc=location.hasAccuracy()?location.getAccuracy():1000f;long now=SystemClock.elapsedRealtime();prefs.edit().putFloat("accuracy",acc).putBoolean("gpsFix",acc<=20f).putLong("lastFixElapsed",now).putFloat("latitude",(float)location.getLatitude()).putFloat("longitude",(float)location.getLongitude()).apply();if(acc>20f)return;
        location=smooth(location,acc);
        if(last==null){last=new Location(location);addPoint(location,route.length()>0);save();return;}
        float d=last.distanceTo(location);long delta=Math.max(1,now-last.getElapsedRealtimeNanos()/1000000L);float speed=d/(delta/1000f);
        if(d>=100||speed>12f){gpsConfirmed=false;gpsCandidateCount=0;gpsCandidateDistance=0;kVar=-1;last=new Location(location);addPoint(location,true);return;}
        float movementThreshold=Math.max(4f,acc*.75f);boolean stepMotion=stepsAvailable&&lastStepElapsed>0&&now-lastStepElapsed<=10000;
        if(location.hasSpeed()&&(stepMotion||!stepsAvailable||gpsConfirmed))updateDetectedType(location.getSpeed());
        // Keep measuring from the same reference until the phone has really moved; a walker covers the threshold in a few fixes.
        if(d<movementThreshold){if(delta>15000){last=new Location(location);gpsCandidateCount=0;gpsCandidateDistance=0;}return;}
        if(speed<.4f){last=new Location(location);gpsCandidateCount=0;gpsCandidateDistance=0;return;}
        if(!stepMotion&&!gpsConfirmed){
            // No step evidence: accept only sustained movement on accurate fixes, so stationary GPS drift adds nothing.
            if(stepsAvailable&&acc>12f){last=new Location(location);return;}
            gpsCandidateCount++;gpsCandidateDistance+=d;last=new Location(location);
            if(gpsCandidateCount<(stepsAvailable?4:2))return;
            gpsConfirmed=true;d=(float)gpsCandidateDistance;gpsCandidateCount=0;gpsCandidateDistance=0;
        }
        lastMovementElapsed=now;double km=d/1000d;
        prefs.edit().putFloat("distance",prefs.getFloat("distance",0)+(float)km).putFloat("speedKmh",speed*3.6f).apply();
        if("Run".equals(currentType))runDistance+=km;else walkDistance+=km;
        trackAltitude(location);addPoint(location,false);last=new Location(location);save();
    }
    private double kLat,kLon,altRef=Double.NaN,altSmooth=Double.NaN,elevGain=0;private float kVar=-1;private long kTime,movingDuration=0;
    /** One-dimensional Kalman filter on position: trusts accurate fixes, damps jittery ones. */
    private Location smooth(Location l,float acc){
        long t=l.getElapsedRealtimeNanos()/1000000L;
        if(kVar<0){kLat=l.getLatitude();kLon=l.getLongitude();kVar=acc*acc;}
        else{float q=3f;kVar+=Math.max(0,t-kTime)/1000f*q*q;float k=kVar/(kVar+acc*acc);kLat+=k*(l.getLatitude()-kLat);kLon+=k*(l.getLongitude()-kLon);kVar=(1-k)*kVar;}
        kTime=t;Location out=new Location(l);out.setLatitude(kLat);out.setLongitude(kLon);return out;
    }
    /** Elevation gain from smoothed altitude while moving; imprecise fixes and climbs under 5 m are treated as GPS noise. */
    private void trackAltitude(Location l){
        if(!l.hasAltitude()||(Build.VERSION.SDK_INT>=26&&l.hasVerticalAccuracy()&&l.getVerticalAccuracyMeters()>8f))return;
        altSmooth=Double.isNaN(altSmooth)?l.getAltitude():altSmooth*.85+l.getAltitude()*.15;
        if(Double.isNaN(altRef))altRef=altSmooth;
        else if(altSmooth-altRef>=5){elevGain+=altSmooth-altRef;altRef=altSmooth;}
        else if(altRef-altSmooth>=5)altRef=altSmooth;
        prefs.edit().putFloat("elevGainM",(float)elevGain).putBoolean("hasElevation",true).apply();
    }
    private void updateDetectedType(float speed){if(manualType)return;smoothedSpeed=smoothedSpeed<0?speed:(smoothedSpeed*.65+speed*.35);String target=smoothedSpeed>=2.25?"Run":smoothedSpeed<=1.75?"Walk":currentType;if(target.equals(currentType)){runCandidates=walkCandidates=0;candidateType=currentType;return;}if(!target.equals(candidateType)){candidateType=target;runCandidates=walkCandidates=0;}if("Run".equals(target))runCandidates++;else walkCandidates++;int count="Run".equals(target)?runCandidates:walkCandidates;if(count>=4){currentType=target;runCandidates=walkCandidates=0;prefs.edit().putString("currentType",currentType).apply();}}
    private void addPoint(Location l,boolean gap){try{JSONObject p=new JSONObject();p.put("lat",l.getLatitude());p.put("lon",l.getLongitude());if(gap)p.put("break",true);route.put(p);}catch(Exception ignored){}}
    @Override public void onSensorChanged(SensorEvent e){if(!active||paused)return;if(e.sensor.getType()==Sensor.TYPE_STEP_COUNTER){DaySteps.ingest(this,e.values[0]);StepsWidget.refresh(this);if(rawStepStart<0)rawStepStart=e.values[0];int total=baseSteps+Math.max(0,(int)(e.values[0]-rawStepStart));int delta=Math.max(0,total-prefs.getInt("steps",0));if(delta>0)onSteps(delta,total);lastRawStep=e.values[0];}else if(e.sensor.getType()==Sensor.TYPE_STEP_DETECTOR){detectedSteps++;onSteps(1,detectedSteps);}}
    private void onSteps(int delta,int total){lastMovementElapsed=SystemClock.elapsedRealtime();lastStepElapsed=lastMovementElapsed;prefs.edit().putInt("steps",total).apply();if("indoor".equals(prefs.getString("activityMode","outdoor"))){double km=delta*prefs.getInt("strideLengthCm",72)/100f/1000d;double distance=prefs.getFloat("distance",0)+km;prefs.edit().putFloat("distance",(float)distance).apply();if("Run".equals(currentType))runDistance+=km;else walkDistance+=km;save();}}
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy){}
    private void save(){long now=SystemClock.elapsedRealtime();long elapsed=elapsedBase+(active&&!paused?now-runStart:0);long segment=active&&!paused?Math.max(0,now-lastStatsElapsed):0;lastStatsElapsed=now;if("Run".equals(currentType))runDuration+=segment;else walkDuration+=segment;prefs.edit().putBoolean("stepsEstimated",false).putInt("steps",stepCounter==null&&stepDetector==null?0:prefs.getInt("steps",0)).apply();boolean moving=active&&!paused&&((lastStepElapsed>0&&now-lastStepElapsed<=8000)||(lastMovementElapsed>0&&now-lastMovementElapsed<=8000));if(moving)movingDuration+=segment;else prefs.edit().putFloat("speedKmh",0).apply();prefs.edit().putBoolean("moving",moving).putLong("heartbeatMs",System.currentTimeMillis()).putLong("movingMs",movingDuration).putLong("elapsed",elapsed).putLong("walkDurationMs",walkDuration).putLong("runDurationMs",runDuration).putFloat("walkDistanceKm",(float)walkDistance).putFloat("runDistanceKm",(float)runDistance).putString("route",route==null?prefs.getString("route","[]"):route.toString()).apply();if(gpsConfirmed&&lastMovementElapsed>0&&now-lastMovementElapsed>10000)gpsConfirmed=false;}
    private boolean promoteForeground(Notification notification){try{if(Build.VERSION.SDK_INT>=29){int type=0;if(Build.VERSION.SDK_INT>=34)type="indoor".equals(prefs.getString("activityMode","outdoor"))?android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH:android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;else if(!"indoor".equals(prefs.getString("activityMode","outdoor")))type=android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;startForeground(NOTIFICATION,notification,type);}else startForeground(NOTIFICATION,notification);return true;}catch(Exception e){return false;}}
    private void updateTrackingNotification(){if(Build.VERSION.SDK_INT<24||!active)return;long now=SystemClock.elapsedRealtime();if(now-lastNotificationElapsed<5000)return;lastNotificationElapsed=now;String mode=prefs.getString("activityMode","outdoor");String msg=paused?"Activity paused":!"indoor".equals(mode)&&!prefs.getBoolean("gpsFix",false)?"GPS signal weak · route paused":prefs.getBoolean("moving",false)?currentType+" · "+String.format(Locale.US,"%.2f km",prefs.getFloat("distance",0))+" · "+(prefs.getInt("steps",0))+" session steps":("indoor".equals(mode)?"No steps detected":"Not moving")+" · recording";getSystemService(NotificationManager.class).notify(NOTIFICATION,notification(msg));}
    private void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(CHANNEL,"Activity tracking",NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager.class).createNotificationChannel(c);}}
    private Notification notification(String text){Intent open=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);long ms=prefs.getLong("elapsed",0);long totalSeconds=ms/1000;float km=prefs.getFloat("distance",0);long pace=km>0?Math.round(totalSeconds/km):0;String detail=String.format(Locale.US,"%s · %s · %d session steps · %d:%02d /km · ~%d kcal",String.format(Locale.US,"%.2f km",km),formatDuration(ms),prefs.getInt("steps",0),pace/60,pace%60,Math.round(km*65));String daily=String.format(Locale.US,"Today: %,d / %,d steps · %,d to goal",dailyStepTotal(),dailyStepGoal(),Math.max(0,dailyStepGoal()-dailyStepTotal()));Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);b.setContentTitle("Kadamm — "+(paused?"Activity paused":currentType+" in progress")).setContentText(daily).setStyle(new Notification.BigTextStyle().bigText(detail+"\n"+daily+"\n"+text)).setSmallIcon(android.R.drawable.ic_menu_directions).setContentIntent(pi).setOngoing(true).setOnlyAlertOnce(true);Intent toggle=new Intent(this,RecorderService.class).setAction(ACTION_NOTIFICATION_TOGGLE);PendingIntent togglePi=PendingIntent.getService(this,1,toggle,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);Intent finish=new Intent(this,RecorderService.class).setAction(ACTION_NOTIFICATION_FINISH);PendingIntent finishPi=PendingIntent.getService(this,2,finish,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);b.addAction(android.R.drawable.ic_media_pause,paused?"Resume":"Pause",togglePi).addAction(android.R.drawable.ic_menu_close_clear_cancel,"Finish",finishPi);return b.build();}
    private int dailyStepTotal(){return (int)Math.min(Integer.MAX_VALUE,DaySteps.todayTotal(this));}
    private String formatDuration(long ms){long seconds=ms/1000;return seconds>=3600?String.format(Locale.US,"%d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60):String.format(Locale.US,"%02d:%02d",seconds/60,seconds%60);}
    @Override public void onDestroy(){running=false;if(handler!=null)handler.removeCallbacks(ticker);if(locations!=null)locations.removeUpdates(this);if(sensors!=null)sensors.unregisterListener(this);super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
