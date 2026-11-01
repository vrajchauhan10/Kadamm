package com.stride.run;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.Random;

/**
 * Falling confetti that removes itself when done. The small burst marks a new badge; {@link #shower} is the
 * full-screen goal celebration: more pieces, staggered from the top, in rectangles, dots and curled ribbons.
 */
final class ConfettiView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final float[][] bits;private float progress=0;private final long duration;
    private static final int[] COLORS={Color.rgb(26,115,232),Color.rgb(52,168,83),Color.rgb(251,188,4),Color.rgb(234,67,53),Color.rgb(147,52,230),Color.rgb(77,144,254),Color.rgb(255,139,203)};

    ConfettiView(Context c){this(c,70,1800,false);}
    /** The goal shower: 220 pieces over 3.6 seconds. */
    static ConfettiView shower(Context c){return new ConfettiView(c,220,3600,true);}
    private ConfettiView(Context c,int count,long duration,boolean stagger){
        super(c);this.duration=duration;Random r=new Random();bits=new float[count][];
        // {x, y, drift, fall speed, spin, colour, start delay, shape}
        for(int i=0;i<count;i++)bits[i]=new float[]{r.nextFloat(),-r.nextFloat()*.3f-.05f,(r.nextFloat()-.5f)*.35f,.8f+r.nextFloat()*.7f,r.nextFloat()*360,COLORS[i%COLORS.length],stagger?r.nextFloat()*.45f:0,r.nextInt(3)};
        setClickable(false);
    }
    void play(){
        ValueAnimator a=ValueAnimator.ofFloat(0,1);a.setDuration(duration);a.setInterpolator(new LinearInterpolator());
        a.addUpdateListener(v->{progress=(float)v.getAnimatedValue();invalidate();});
        a.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator x){if(getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)getParent()).removeView(ConfettiView.this);}});
        a.start();
    }
    @Override protected void onDraw(Canvas c){
        float w=getWidth(),h=getHeight(),d=getResources().getDisplayMetrics().density;
        for(float[] b:bits){
            float t=Math.max(0,(progress-b[6])/(1-b[6]));if(t<=0)continue;
            float x=(b[0]+b[2]*t+.03f*(float)Math.sin(t*12+b[4]))*w,y=(b[1]+b[3]*t*t*.6f+b[3]*t*.5f)*h;
            p.setColor((int)b[5]);p.setAlpha((int)(255*(1-Math.max(0,t-.75f)/.25f)));
            c.save();c.rotate(b[4]+t*720,x,y);
            if(b[7]==0)c.drawRect(x-3*d,y-6*d,x+3*d,y+6*d,p);
            else if(b[7]==1)c.drawCircle(x,y,3.5f*d,p);
            else{p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.5f*d);p.setStrokeCap(Paint.Cap.ROUND);c.drawArc(x-6*d,y-6*d,x+6*d,y+6*d,0,200,false,p);p.setStyle(Paint.Style.FILL);}
            c.restore();
        }
    }
}
