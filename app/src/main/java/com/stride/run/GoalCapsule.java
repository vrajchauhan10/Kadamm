package com.stride.run;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.LinearInterpolator;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

/**
 * The daily-goals capsule: one day's progress as a glass capsule of liquid, layered from three goals. Steps fill
 * the bottom (teal, worth up to 60 %), brisk minutes the middle (coral, up to 25 %) and distance the top (indigo,
 * up to 15 %). Each layer stops growing at its own goal, so 100 % means all three are met: a perfect day, with
 * bubbles. Layer surfaces are waves with their own rhythm, so the colours meet in curves rather than lines.
 * Used by the Home card (animated) and the home-screen widget (a still frame).
 */
final class GoalCapsule {
    static final int[] COLORS={Color.rgb(0,191,166),Color.rgb(255,107,107),Color.rgb(124,108,242)};
    static final int[] WEIGHTS={60,25,15};
    static final String[] NAMES={"Steps","Brisk","Distance"};

    /** Today's progress: values, goals, each goal's fraction met (0–1), and each layer's share of the day in %. */
    static final class Day {
        long steps;int stepGoal,briskGoal;double brisk,km,kmGoal;boolean data;final double[] done=new double[3],share=new double[3];
        int total(){double t=0;for(double s:share)t+=s;return (int)Math.round(t);}
        String pending(int i){
            if(done[i]>=1)return "done ✓";
            if(i==0)return String.format(Locale.US,"%,d to go",Math.max(0,stepGoal-steps));
            if(i==1)return Math.max(1,Math.round(briskGoal-brisk))+" min to go";
            return String.format(Locale.US,"%.1f km to go",Math.max(.1,kmGoal-km));
        }
        String value(int i){return i==0?String.format(Locale.US,"%,d",steps):i==1?Math.round(brisk)+" min":String.format(Locale.US,"%.1f km",km);}
        String goal(int i){return i==0?String.format(Locale.US,"of %,d",stepGoal):i==1?"of "+briskGoal+" min":String.format(Locale.US,"of %.1f km",kmGoal);}
    }

    static Day today(Context c){
        Day d=new Day();android.content.SharedPreferences s=c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE);
        d.stepGoal=s.getInt("stepGoal",10000);d.briskGoal=s.getInt("briskGoalMin",30);d.kmGoal=s.getInt("distanceGoalM",5000)/1000d;
        d.data=DaySteps.hasTodayData(c);d.steps=DaySteps.todayTotal(c);d.brisk=briskToday(c);d.km=DaySteps.distanceToday(c)[0];
        d.done[0]=d.data?Math.min(1,d.steps/(double)Math.max(1,d.stepGoal)):0;d.done[1]=Math.min(1,d.brisk/Math.max(1,d.briskGoal));d.done[2]=Math.min(1,d.km/Math.max(.1,d.kmGoal));
        for(int i=0;i<3;i++)d.share[i]=d.done[i]*WEIGHTS[i];
        return d;
    }
    /** Brisk minutes today (100+ steps a minute): Health Connect's minute steps, or today's sessions; the larger. */
    static double briskToday(Context c){
        String today=DaySteps.todayKey();JSONObject m=DaySteps.minuteDays(c).optJSONObject(today);double hc=m!=null?m.optDouble("brisk",0):0,ses=0;
        if(SessionMetrics.ready()){JSONArray all=DaySteps.sessions(c);for(int i=0;i<all.length();i++){JSONObject o=all.optJSONObject(i);if(o!=null&&today.equals(o.optString("day",""))&&!Insights.vehicle(o)){SessionMetrics x=SessionMetrics.of(c,o);ses+=(x.zoneS[2]+x.zoneS[3]+x.zoneS[4])/60d;}}}
        return Math.max(hc,ses);
    }

    // ---- Drawing ----
    private static Path wave(float left,float right,float top,float bottom,float amp,float phase,float len){
        Path p=new Path();p.moveTo(left,bottom);p.lineTo(left,top);
        for(float x=left;x<=right+1;x+=3)p.lineTo(x,top+amp*(float)Math.sin((x-left)/len*Math.PI*2+phase));
        p.lineTo(right,bottom);p.close();return p;
    }
    /** Draws the capsule into a box w×h; {@code share} is each layer's % of the day, {@code phase} moves the waves. */
    static void draw(Canvas c,float w,float h,double[] share,float phase,boolean dark,float dp){
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);float labelH=dp*24,cw=Math.min(w,(h-labelH)*.46f),x0=(w-cw)/2,y0=dp*2,ch=h-dp*4-labelH;RectF body=new RectF(x0,y0,x0+cw,y0+ch);float rad=cw/2;
        p.setColor(dark?Color.rgb(22,22,26):Color.rgb(238,240,244));c.drawRoundRect(body,rad,rad,p);
        c.save();Path clip=new Path();clip.addRoundRect(body,rad,rad,Path.Direction.CW);c.clipPath(clip);
        double total=0;for(double s:share)total+=s;
        // Layers from the top one down, so each lower layer's wave is drawn over the one above's bottom edge.
        float[] tops=new float[3];double cum=0;for(int i=0;i<3;i++){cum+=share[i];tops[i]=(float)(y0+ch-ch*Math.min(1,cum/100));}
        for(int i=2;i>=0;i--){
            if(share[i]<=0)continue;float swell=1f+.35f*(float)Math.sin(phase*.5f+i),amp=dp*(5f+i*1.4f)*swell,len=cw*(1.6f-i*.22f),ph=phase*(1+i*.35f)+i*1.9f;int col=COLORS[i];
            p.setColor(Color.BLACK);p.setShader(new LinearGradient(0,tops[i],0,y0+ch,blend(col,Color.WHITE,.12f),blend(col,Color.BLACK,.10f),Shader.TileMode.CLAMP));
            c.drawPath(wave(x0-dp,x0+cw+dp,tops[i],y0+ch,amp,ph,len),p);p.setShader(null);
            p.setColor(Color.argb(70,Color.red(col),Color.green(col),Color.blue(col)));c.drawPath(wave(x0-dp,x0+cw+dp,tops[i]+dp*2.5f,y0+ch,amp*.7f,ph+1.3f,len*1.35f),p);
        }
        if(total>=99.5){p.setColor(Color.argb(130,255,255,255));float[][] b={{.3f,.78f,3.5f},{.62f,.6f,2.6f},{.45f,.38f,2.1f},{.7f,.85f,3f},{.36f,.5f,1.8f}};
            for(float[] q:b){float by=(y0+ch*q[1]-(phase*12*dp)%(ch*.5f));if(by<y0+dp*6)by+=ch*.5f;c.drawCircle(x0+cw*q[0],by,q[2]*dp,p);}}
        c.restore();
        // Glass: a rim and a soft shine down the left side.
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp);p.setColor(dark?Color.argb(60,255,255,255):Color.argb(40,0,0,0));c.drawRoundRect(body,rad,rad,p);p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(dark?28:60,255,255,255));c.drawRoundRect(new RectF(x0+cw*.14f,y0+ch*.30f,x0+cw*.24f,y0+ch*.66f),cw*.05f,cw*.05f,p);
        p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        // The day's % sits under the capsule, clear of the liquid.
        int pct=(int)Math.round(total);p.setTextSize(dp*17);p.setColor(pct>=100?Color.rgb(52,168,83):dark?Color.WHITE:Color.rgb(32,33,36));c.drawText(pct+"%",x0+cw/2,y0+ch+labelH-dp*3,p);
    }
    static int blend(int a,int b,float f){return Color.rgb((int)(Color.red(a)+(Color.red(b)-Color.red(a))*f),(int)(Color.green(a)+(Color.green(b)-Color.green(a))*f),(int)(Color.blue(a)+(Color.blue(b)-Color.blue(a))*f));}

    /** A still frame for the home-screen widget. */
    static Bitmap bitmap(Context c,Day d,int wDp,int hDp){
        float dp=c.getResources().getDisplayMetrics().density;Bitmap b=Bitmap.createBitmap((int)(wDp*dp),(int)(hDp*dp),Bitmap.Config.ARGB_8888);
        draw(new Canvas(b),b.getWidth(),b.getHeight(),d.share,1.2f,true,dp);return b;
    }

    /**
     * The animated capsule: the waves roll for a few seconds when it appears or a value changes, then settle, so
     * the screen can go idle (better for battery, and for screen readers that wait for stillness).
     */
    static final class CapsuleView extends View {
        private final boolean dark;private final double[] shown=new double[3],target=new double[3];private float phase;private ValueAnimator drift,level;
        CapsuleView(Context c,boolean dark){super(c);this.dark=dark;setContentDescription("Daily goals capsule");}
        void set(Day d){
            double[] from=shown.clone();System.arraycopy(d.share,0,target,0,3);boolean same=true;for(int i=0;i<3;i++)if(Math.abs(target[i]-shown[i])>.05)same=false;if(same)return;
            if(isAttachedToWindow())startDrift();
            if(level!=null)level.cancel();level=ValueAnimator.ofFloat(0,1);level.setDuration(1100);level.setInterpolator(new android.view.animation.DecelerateInterpolator(2f));
            level.addUpdateListener(a->{float f=(float)a.getAnimatedValue();for(int i=0;i<3;i++)shown[i]=from[i]+(target[i]-from[i])*f;invalidate();});level.start();
        }
        @Override protected void onAttachedToWindow(){super.onAttachedToWindow();startDrift();}
        @Override protected void onDetachedFromWindow(){if(drift!=null)drift.cancel();if(level!=null)level.cancel();super.onDetachedFromWindow();}
        @Override public void onVisibilityAggregated(boolean visible){super.onVisibilityAggregated(visible);if(visible)startDrift();else if(drift!=null)drift.pause();}
        private void startDrift(){
            float scale=1f;try{scale=android.provider.Settings.Global.getFloat(getContext().getContentResolver(),"animator_duration_scale",1f);}catch(Exception ignored){}
            if(scale<=0)return;if(drift!=null){if(drift.isPaused())drift.resume();return;}
            float from=phase;drift=ValueAnimator.ofFloat(from,from+(float)(Math.PI*2)*.85f);drift.setDuration(12000);drift.setInterpolator(new android.view.animation.DecelerateInterpolator(1.4f));
            drift.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator a){drift=null;}});
            drift.addUpdateListener(a->{phase=(float)a.getAnimatedValue();invalidate();});drift.start();
        }
        @Override protected void onDraw(Canvas c){GoalCapsule.draw(c,getWidth(),getHeight(),shown,phase,dark,getResources().getDisplayMetrics().density);}
    }
}
