package com.stride.run;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * Small drawn visuals for dashboard tiles and the streak card:
 * a progress track, mini day bars, a walk/run split, and the goal-streak chain.
 */
final class MiniChartView extends View {
    static final int PROGRESS=0,BARS=1,SPLIT=2,CHAIN=3;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final RectF r=new RectF();private final KadammTheme t;
    private final int mode,color,color2;
    private float fraction,fraction2;private long[] values=new long[0];private long goal;private boolean[] hits=new boolean[0];private String[] labels=new String[0];private int todayIndex=-1;

    MiniChartView(Context c,KadammTheme theme,int mode,int color,int color2){super(c);t=theme;this.mode=mode;this.color=color;this.color2=color2;}
    private float dp(float v){return v*getResources().getDisplayMetrics().density;}

    /** PROGRESS: fraction of a target (over 1 shows a full bar). */
    MiniChartView progress(float f){fraction=Math.max(0,f);invalidate();return this;}
    /** SPLIT: two shares of a whole, e.g. walking and running distance. */
    MiniChartView split(float a,float b){fraction=Math.max(0,a);fraction2=Math.max(0,b);invalidate();return this;}
    /** BARS: one value per day, oldest first; the last is today. A dashed line marks the goal when it fits. */
    MiniChartView bars(long[] v,long goal){values=v;this.goal=goal;invalidate();return this;}
    /** CHAIN: goal reached per day, oldest first, with day letters; {@code today} is the index still in progress. */
    MiniChartView chain(boolean[] hit,String[] dayLabels,int today){hits=hit;labels=dayLabels;todayIndex=today;invalidate();return this;}

    private int alpha(int c,int a){return Color.argb(a,Color.red(c),Color.green(c),Color.blue(c));}

    @Override protected void onDraw(Canvas c){
        float w=getWidth(),h=getHeight();p.setShader(null);p.setStyle(Paint.Style.FILL);
        if(mode==PROGRESS){
            float bh=Math.min(h,dp(8));float y=(h-bh)/2;r.set(0,y,w,y+bh);p.setColor(alpha(color,t.dark?50:38));c.drawRoundRect(r,bh/2,bh/2,p);
            float f=Math.min(1,fraction);if(f>0){r.set(0,y,Math.max(bh,w*f),y+bh);p.setColor(Color.BLACK);p.setShader(new LinearGradient(0,0,r.right,0,alpha(color,170),color,Shader.TileMode.CLAMP));c.drawRoundRect(r,bh/2,bh/2,p);p.setShader(null);}
            if(fraction>=1){p.setColor(Color.WHITE);c.drawCircle(w-bh/2,y+bh/2,bh*.22f,p);}
        }else if(mode==SPLIT){
            float bh=Math.min(h,dp(8)),y=(h-bh)/2,total=fraction+fraction2;r.set(0,y,w,y+bh);p.setColor(alpha(t.textMuted,t.dark?50:38));c.drawRoundRect(r,bh/2,bh/2,p);
            if(total>0){float a=w*fraction/total,gap=fraction>0&&fraction2>0?dp(3):0;
                if(fraction>0){p.setColor(color);r.set(0,y,Math.max(bh,a-gap/2),y+bh);c.drawRoundRect(r,bh/2,bh/2,p);}
                if(fraction2>0){p.setColor(color2);r.set(Math.min(w-bh,a+gap/2),y,w,y+bh);c.drawRoundRect(r,bh/2,bh/2,p);}}
        }else if(mode==BARS){
            int n=values.length;if(n==0)return;long max=Math.max(1,goal);for(long v:values)max=Math.max(max,v);float slot=w/n,bw=Math.min(dp(10),slot*.62f),scale=(h-dp(2))/(max*1.05f);
            for(int i=0;i<n;i++){float cx=slot*(i+.5f),bh=Math.max(values[i]>0?dp(3):dp(2),values[i]*scale);boolean today=i==n-1;
                p.setColor(today?color:goal>0&&values[i]>=goal?alpha(color,200):alpha(color,t.dark?95:80));r.set(cx-bw/2,h-bh,cx+bw/2,h);c.drawRoundRect(r,dp(3),dp(3),p);}
            if(goal>0&&goal<=max){float gy=h-goal*scale;p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));p.setColor(alpha(t.textMuted,150));p.setPathEffect(new DashPathEffect(new float[]{dp(3),dp(3)},0));c.drawLine(0,gy,w,gy,p);p.setPathEffect(null);p.setStyle(Paint.Style.FILL);}
        }else if(mode==CHAIN){
            int n=hits.length;if(n==0)return;float slot=w/n,rad=Math.min(slot*.36f,dp(13)),cy=rad+dp(2);
            // Links between consecutive goal days make a streak read as one unbroken chain.
            p.setStrokeWidth(dp(4));p.setStrokeCap(Paint.Cap.ROUND);
            for(int i=1;i<n;i++)if(hits[i]&&hits[i-1]){p.setColor(color);c.drawLine(slot*(i-.5f),cy,slot*(i+.5f),cy,p);}
            p.setTextAlign(Paint.Align.CENTER);
            for(int i=0;i<n;i++){float cx=slot*(i+.5f);boolean today=i==todayIndex;
                if(hits[i]){p.setStyle(Paint.Style.FILL);p.setColor(color);c.drawCircle(cx,cy,rad,p);
                    p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));p.setColor(t.accentOn);float s=rad*.42f;c.drawLine(cx-s,cy,cx-s*.25f,cy+s*.7f,p);c.drawLine(cx-s*.25f,cy+s*.7f,cx+s,cy-s*.6f,p);}
                else{p.setStyle(Paint.Style.FILL);p.setColor(t.surfaceRaised);c.drawCircle(cx,cy,rad,p);
                    if(today){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));p.setColor(color);p.setPathEffect(new DashPathEffect(new float[]{dp(3),dp(3)},0));c.drawCircle(cx,cy,rad-dp(1),p);p.setPathEffect(null);}}
                p.setStyle(Paint.Style.FILL);p.setTextSize(dp(10));p.setColor(today?t.text:t.textMuted);if(i<labels.length)c.drawText(labels[i],cx,cy+rad+dp(14),p);
            }
            p.setTextAlign(Paint.Align.LEFT);
        }
    }
}
