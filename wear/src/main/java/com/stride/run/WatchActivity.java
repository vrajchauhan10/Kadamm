package com.stride.run;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.gms.wearable.DataClient;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Kadamm on the watch: today's steps ring, the coach's three challenges, and session controls. Start, pause and
 * finish run the session on the phone (which has the GPS route and all the analysis); while a session runs, the
 * watch shows its time, distance and pace and sends its heart rate to the phone every 5 seconds.
 * Runs on any Wear OS 3+ watch paired with a phone that has Kadamm 9.6 or later.
 */
public class WatchActivity extends Activity implements DataClient.OnDataChangedListener, SensorEventListener {
    private static final int TEAL=Color.rgb(0,191,166),CORAL=Color.rgb(255,107,107),INDIGO=Color.rgb(124,108,242),GREEN=Color.rgb(52,168,83),MUTED=Color.rgb(150,154,160);
    private LinearLayout content;private JSONObject today,live;private final Handler handler=new Handler(Looper.getMainLooper());
    private SensorManager sensors;private Sensor hrSensor;private int bpm;private long lastHrSent;private boolean hrOn;private long liveAt;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);ScrollView sv=new ScrollView(this);sv.setBackgroundColor(Color.BLACK);sv.setFillViewport(true);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setGravity(Gravity.CENTER_HORIZONTAL);int side=dp(18);content.setPadding(side,dp(22),side,dp(36));
        sv.addView(content);setContentView(sv);sv.requestFocus();
        sensors=(SensorManager)getSystemService(SENSOR_SERVICE);hrSensor=sensors==null?null:sensors.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        render();
    }
    @Override protected void onResume(){
        super.onResume();Wearable.getDataClient(this).addListener(this);
        Wearable.getDataClient(this).getDataItems(Uri.parse("wear://*/kadamm/")).addOnSuccessListener(items->{for(DataItem it:items)read(it);items.release();render();});
        Wearable.getDataClient(this).getDataItems(Uri.parse("wear://*/kadamm/"),DataClient.FILTER_PREFIX).addOnSuccessListener(items->{for(DataItem it:items)read(it);items.release();render();});
        message("/kadamm/sync","");handler.post(tick);HrJob.hello(this);HrJob.send(this);if(HrJob.allowed(this))HrJob.schedule(this);
        // While Kadamm is open on the watch, heart rate streams to the phone every 5 seconds.
        startHr();
    }
    @Override protected void onPause(){Wearable.getDataClient(this).removeListener(this);handler.removeCallbacks(tick);stopHr();super.onPause();}

    @Override public void onDataChanged(DataEventBuffer events){for(DataEvent e:events)if(e.getType()==DataEvent.TYPE_CHANGED)read(e.getDataItem());render();}
    private void read(DataItem it){
        try{String path=it.getUri().getPath();String json=DataMapItem.fromDataItem(it).getDataMap().getString("json");if(json==null)return;
            if("/kadamm/today".equals(path))today=new JSONObject(json);else if("/kadamm/live".equals(path)){boolean was=live!=null&&live.optBoolean("active");live=new JSONObject(json);liveAt=SystemClock.elapsedRealtime();if(was!=live.optBoolean("active"))buzz();}}catch(Exception ignored){}
    }
    private boolean active(){return live!=null&&live.optBoolean("active");}

    /** Once a second while a session runs: the clock moves on between updates from the phone. */
    private final Runnable tick=new Runnable(){@Override public void run(){if(active()&&!live.optBoolean("paused"))render();handler.postDelayed(this,1000);}};

    private void render(){
        content.removeAllViews();boolean act=active();
        if(act)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        TextView brand=tx("KADAMM",11,TEAL,true);brand.setLetterSpacing(.2f);content.addView(brand);
        if(act)renderLive();else renderToday();
    }

    private void renderLive(){
        boolean paused=live.optBoolean("paused");long el=live.optLong("elapsedMs");if(!paused)el+=SystemClock.elapsedRealtime()-liveAt;
        content.addView(tx(paused?"Paused":live.optString("type","Walk")+" · recording",12,paused?CORAL:MUTED,false));
        long s=el/1000;content.addView(tx(s>=3600?String.format(Locale.US,"%d:%02d:%02d",s/3600,(s/60)%60,s%60):String.format(Locale.US,"%d:%02d",s/60,s%60),40,Color.WHITE,true));
        LinearLayout r=row();content.addView(r);
        stat(r,String.format(Locale.US,"%.2f",live.optDouble("km")),"km");stat(r,String.format(Locale.US,"%,d",live.optLong("steps")),"steps");
        LinearLayout r2=row();content.addView(r2);long pace=live.optLong("paceS",0);
        stat(r2,pace>0&&pace<3600?String.format(Locale.US,"%d:%02d",pace/60,pace%60):"—","/km");stat(r2,bpm>0?String.valueOf(bpm):"—","♥ bpm");
        LinearLayout btns=row();LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(48));bp.topMargin=dp(12);content.addView(btns,bp);
        TextView pause=button(paused?"▶":"❚❚",paused?TEAL:Color.rgb(48,48,52));pause.setOnClickListener(v->{message("/kadamm/toggle","");buzz();});
        TextView stop=button("■ Finish",CORAL);stop.setOnClickListener(v->{message("/kadamm/stop","");buzz();});
        LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,-1,1);a.rightMargin=dp(6);btns.addView(pause,a);btns.addView(stop,new LinearLayout.LayoutParams(0,-1,1.4f));
        if(hrSensor==null)note("No heart-rate sensor on this watch");else if(checkSelfPermission(Manifest.permission.BODY_SENSORS)!=PackageManager.PERMISSION_GRANTED)note("Allow body sensors to add heart rate");
    }

    private void renderToday(){
        if(today==null){content.addView(new Ring(this,0,0),new LinearLayout.LayoutParams(dp(130),dp(130)));note("Open Kadamm on your phone to sync");startButtons();return;}
        long steps=today.optLong("steps");int goal=today.optInt("goal",10000);
        Ring ring=new Ring(this,steps,goal);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(dp(140),dp(140));rp.topMargin=dp(6);content.addView(ring,rp);
        int streak=today.optInt("streak");if(streak>1)content.addView(tx("🔥 "+streak+"-day streak",12,Color.rgb(255,159,67),true));
        LinearLayout r=row();content.addView(r);stat(r,today.optLong("brisk")+"","brisk min");stat(r,String.format(Locale.US,"%.1f",today.optDouble("km")),"km");
        startButtons();
        // The coach
        TextView h=tx("COACH KADAMM · "+today.optInt("done")+"/3",10,INDIGO,true);h.setLetterSpacing(.12f);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-2,-2);hp.topMargin=dp(16);content.addView(h,hp);
        JSONArray ch=today.optJSONArray("challenges");int[] cols={TEAL,CORAL,INDIGO};
        if(ch!=null)for(int i=0;i<ch.length();i++){JSONObject c=ch.optJSONObject(i);if(c==null)continue;boolean done=c.optBoolean("done");
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(12),dp(8),dp(12),dp(8));card.setBackground(shape(done?Color.rgb(20,52,30):Color.rgb(28,28,32),dp(14)));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=dp(6);content.addView(card,cp);
            card.addView(tx((done?"✓ ":"")+c.optString("title"),13,done?GREEN:Color.WHITE,true));card.addView(tx(c.optString("progress"),11,MUTED,false));
            View bar=new Bar(this,(float)c.optDouble("f"),done?GREEN:cols[i%3]);LinearLayout.LayoutParams bl=new LinearLayout.LayoutParams(-1,dp(4));bl.topMargin=dp(5);card.addView(bar,bl);}
        TextView m=tx(today.optString("message"),12,Color.rgb(220,222,226),false);m.setGravity(Gravity.CENTER);LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,-2);mp.topMargin=dp(10);content.addView(m,mp);
        heartRow();
    }
    /** Heart rate now, and the switch for all-day heart rate on the phone. */
    private void heartRow(){
        if(hrSensor==null)return;
        TextView now=tx(bpm>0?"♥ "+bpm+" bpm · on your phone too":"♥ Measuring…",13,CORAL,true);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-2,-2);np.topMargin=dp(14);content.addView(now,np);
        if(!HrJob.allowed(this)){TextView on=button("All-day heart rate",Color.rgb(48,48,52));LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(-1,dp(44));op.topMargin=dp(8);content.addView(on,op);on.setOnClickListener(v->allDay());
            note("Every 15 min, for recovery, strain and stress on your phone");}
        else note("All-day heart rate is on: every 15 minutes");
    }
    private void startButtons(){
        LinearLayout btns=row();LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(46));bp.topMargin=dp(12);content.addView(btns,bp);
        TextView walk=button("▶ Walk",TEAL),run=button("Run",Color.rgb(48,48,52));
        walk.setOnClickListener(v->{message("/kadamm/start","Walk");buzz();toastNote("Starting on your phone…");});run.setOnClickListener(v->{message("/kadamm/start","Run");buzz();toastNote("Starting on your phone…");});
        LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,-1,1.5f);a.rightMargin=dp(6);btns.addView(walk,a);btns.addView(run,new LinearLayout.LayoutParams(0,-1,1));
    }
    private void toastNote(String s){android.widget.Toast.makeText(this,s,android.widget.Toast.LENGTH_SHORT).show();}

    // ---- Heart rate during a session ----
    private void startHr(){
        if(hrOn||hrSensor==null)return;
        if(checkSelfPermission(Manifest.permission.BODY_SENSORS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.BODY_SENSORS},1);return;}
        hrOn=sensors.registerListener(this,hrSensor,SensorManager.SENSOR_DELAY_NORMAL);
    }
    private void stopHr(){if(hrOn&&sensors!=null)sensors.unregisterListener(this);hrOn=false;bpm=0;}
    @Override public void onRequestPermissionsResult(int q,String[] p,int[] r){super.onRequestPermissionsResult(q,p,r);if(HrJob.allowed(this)){HrJob.schedule(this);HrJob.now(this);}HrJob.hello(this);if(q==1)startHr();render();}
    /** All-day heart rate needs "all the time" body-sensor access on Wear OS 4+, asked for after the normal one. */
    private void allDay(){
        if(checkSelfPermission(Manifest.permission.BODY_SENSORS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.BODY_SENSORS},1);return;}
        if(android.os.Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.BODY_SENSORS_BACKGROUND")!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{"android.permission.BODY_SENSORS_BACKGROUND"},2);return;}
        HrJob.schedule(this);HrJob.now(this);render();
    }
    @Override public void onSensorChanged(SensorEvent e){
        if(e.accuracy<SensorManager.SENSOR_STATUS_ACCURACY_LOW||e.values.length==0)return;int v=Math.round(e.values[0]);if(v<30||v>230)return;boolean first=bpm==0;bpm=v;if(first&&!active())render();
        long now=SystemClock.elapsedRealtime();if(now-lastHrSent>=5000){lastHrSent=now;message("/kadamm/hr",String.valueOf(v));}
    }
    @Override public void onAccuracyChanged(Sensor s,int a){}

    /** Sends a message to the phone (every connected node; normally just the one phone). */
    private void message(String path,String data){
        Wearable.getNodeClient(this).getConnectedNodes().addOnSuccessListener(nodes->{for(Node n:nodes)Wearable.getMessageClient(this).sendMessage(n.getId(),path,data.getBytes(StandardCharsets.UTF_8));});
    }
    private void buzz(){try{Vibrator v=getSystemService(Vibrator.class);if(v!=null)v.vibrate(VibrationEffect.createOneShot(40,VibrationEffect.DEFAULT_AMPLITUDE));}catch(Exception ignored){}}

    // ---- Small view helpers ----
    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private TextView tx(String s,float sp,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);t.setGravity(Gravity.CENTER);if(bold)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return t;}
    private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER);return r;}
    private void stat(LinearLayout r,String v,String label){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setGravity(Gravity.CENTER);c.setPadding(dp(6),dp(6),dp(6),0);
        c.addView(tx(v,20,Color.WHITE,true));c.addView(tx(label,10,MUTED,false));r.addView(c,new LinearLayout.LayoutParams(0,-2,1));}
    private void note(String s){TextView t=tx(s,11,MUTED,false);t.setPadding(0,dp(8),0,0);content.addView(t);}
    private GradientDrawable shape(int color,float r){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(r);return g;}
    private TextView button(String s,int color){TextView t=tx(s,14,Color.WHITE,true);t.setBackground(shape(color,dp(24)));t.setClickable(true);return t;}

    /** Today's steps against the goal, as a ring with the count inside. */
    static final class Ring extends View {
        private final long steps;private final int goal;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Ring(Activity a,long steps,int goal){super(a);this.steps=steps;this.goal=goal;}
        @Override protected void onDraw(Canvas c){
            float d=getResources().getDisplayMetrics().density,w=getWidth(),h=getHeight(),sw=10*d;RectF o=new RectF(sw,sw,w-sw,h-sw);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(sw);p.setStrokeCap(Paint.Cap.ROUND);p.setColor(Color.rgb(36,36,40));c.drawArc(o,0,360,false,p);
            float f=goal<=0?0:Math.min(1,steps/(float)goal);p.setColor(f>=1?GREEN:TEAL);if(f>0)c.drawArc(o,-90,360*f,false,p);
            p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setColor(Color.WHITE);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextSize(28*d);
            c.drawText(goal<=0?"—":String.format(Locale.US,"%,d",steps),w/2,h/2+8*d,p);
            p.setTextSize(11*d);p.setColor(MUTED);p.setTypeface(Typeface.DEFAULT);c.drawText(goal<=0?"steps":String.format(Locale.US,"of %,d",goal),w/2,h/2+26*d,p);
        }
    }
    static final class Bar extends View {
        private final float f;private final int color;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Bar(Activity a,float f,int color){super(a);this.f=Math.max(0,Math.min(1,f));this.color=color;}
        @Override protected void onDraw(Canvas c){float h=getHeight();p.setColor(Color.rgb(50,50,56));c.drawRoundRect(0,0,getWidth(),h,h/2,h/2,p);p.setColor(color);c.drawRoundRect(0,0,Math.max(h,getWidth()*f),h,h/2,h/2,p);}
    }
}
