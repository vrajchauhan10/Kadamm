package com.stride.run;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.Locale;

/** Daily goal ring. Fills from the value it last showed, so a refresh never restarts it from zero. */
final class ProgressRingView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval=new RectF();
    private final KadammTheme theme;
    private float shown=0,target=-1;
    private long steps=0;private int goal=10000;private boolean available=false;
    private ValueAnimator animator;private float inner=0;private boolean compact=false;
    /** Compact: thinner ring with just the percentage, for small places like the menu. */
    void setCompact(boolean value){compact=value;invalidate();}

    /** Inner ring: move minutes as a fraction of their goal. */
    void setInner(float fraction){fraction=Math.max(0,Math.min(1,fraction));if(fraction!=inner){inner=fraction;invalidate();}}

    ProgressRingView(Context c,KadammTheme theme){super(c);this.theme=theme;}

    /** Goal-progress colour, shared with the rest of the dashboard. */
    static int colorFor(KadammTheme t,double percent){
        return percent>=100?t.positive:t.accent;
    }

    void setProgress(long steps,int goal,boolean available){
        this.steps=steps;this.goal=Math.max(1,goal);this.available=available;
        float next=available?(float)steps/this.goal:0f;
        setContentDescription(available?String.format(Locale.US,"%,d of %,d steps today",steps,this.goal):"Steps not available yet");
        if(next==target){invalidate();return;}
        boolean first=target<0;target=next;
        if(animator!=null)animator.cancel();
        float scale=1f;
        try{scale=android.provider.Settings.Global.getFloat(getContext().getContentResolver(),"animator_duration_scale",1f);}catch(Exception ignored){}
        if(scale<=0f||Math.abs(next-shown)<.002f){shown=next;invalidate();return;}
        animator=ValueAnimator.ofFloat(shown,next);
        animator.setDuration(first?900:450);animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a->{shown=(float)a.getAnimatedValue();invalidate();});
        animator.start();
    }

    private float dp(float v){return v*getResources().getDisplayMetrics().density;}

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float w=getWidth(),h=getHeight(),stroke=compact?dp(6):dp(11),r=Math.min(w,h)/2-stroke/2-dp(2),cx=w/2,cy=h/2;
        oval.set(cx-r,cy-r,cx+r,cy+r);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(stroke);p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(theme.surfaceRaised);c.drawArc(oval,0,360,false,p);
        int color=colorFor(theme,shown*100);
        if(shown>0){p.setColor(color);c.drawArc(oval,-90,Math.max(1.5f,Math.min(1f,shown)*360f),false,p);}

        if(compact){
            p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setColor(theme.text);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextSize(dp(13));
            c.drawText(available?String.format(Locale.US,"%d%%",Math.round(steps*100f/goal)):"—",cx,cy+dp(4.5f),p);p.setTextAlign(Paint.Align.LEFT);return;
        }
        float r2=r-stroke-dp(4);oval.set(cx-r2,cy-r2,cx+r2,cy+r2);p.setStrokeWidth(dp(8));
        p.setColor(theme.surfaceRaised);c.drawArc(oval,0,360,false,p);
        if(inner>0){p.setColor(theme.accentSecondary);c.drawArc(oval,-90,Math.max(1.5f,inner*360f),false,p);}
        p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);
        // The number counts up with the arc, and lands exactly on the real value.
        long display=!available?0:target>0?Math.round(steps*Math.min(1f,shown/target)):steps;
        if(animator==null||!animator.isRunning())display=steps;
        String big=available?String.format(Locale.US,"%,d",display):"—";
        p.setColor(theme.text);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        float size=dp(26);p.setTextSize(size);
        float room=r2*2-dp(8)-dp(16);
        while(p.measureText(big)>room&&size>dp(14)){size-=dp(1);p.setTextSize(size);}
        c.drawText(big,cx,cy+size*.18f,p);
        p.setTypeface(Typeface.DEFAULT);p.setColor(theme.textMuted);p.setTextSize(dp(11));
        c.drawText("steps",cx,cy+size*.18f+dp(16),p);
        p.setTextSize(dp(9));p.setLetterSpacing(.12f);
        c.drawText("TODAY",cx,cy-size*.72f,p);
        p.setLetterSpacing(0f);p.setTextAlign(Paint.Align.LEFT);
    }
}
