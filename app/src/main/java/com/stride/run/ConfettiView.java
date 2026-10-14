package com.stride.run;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.Random;

/** A one-off burst of falling confetti for a new personal record. Removes itself when done. */
final class ConfettiView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final float[][] bits=new float[70][];private float progress=0;
    private static final int[] COLORS={Color.rgb(26,115,232),Color.rgb(52,168,83),Color.rgb(251,188,4),Color.rgb(234,67,53),Color.rgb(147,52,230)};

    ConfettiView(Context c){
        super(c);Random r=new Random();
        for(int i=0;i<bits.length;i++)bits[i]=new float[]{r.nextFloat(),-r.nextFloat()*.3f,(r.nextFloat()-.5f)*.4f,.7f+r.nextFloat()*.6f,r.nextFloat()*360,COLORS[i%COLORS.length]};
        setClickable(false);
    }
    void play(){
        ValueAnimator a=ValueAnimator.ofFloat(0,1);a.setDuration(1800);a.setInterpolator(new DecelerateInterpolator());
        a.addUpdateListener(v->{progress=(float)v.getAnimatedValue();invalidate();});
        a.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator x){if(getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)getParent()).removeView(ConfettiView.this);}});
        a.start();
    }
    @Override protected void onDraw(Canvas c){
        float w=getWidth(),h=getHeight(),d=getResources().getDisplayMetrics().density;
        for(float[] b:bits){
            float x=(b[0]+b[2]*progress)*w,y=(b[1]+b[3]*progress)*h;
            p.setColor((int)b[5]);p.setAlpha((int)(255*(1-Math.max(0,progress-.7f)/.3f)));
            c.save();c.rotate(b[4]+progress*540,x,y);c.drawRect(x-3*d,y-6*d,x+3*d,y+6*d,p);c.restore();
        }
    }
}
