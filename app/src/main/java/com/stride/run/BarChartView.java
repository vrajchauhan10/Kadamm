package com.stride.run;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Daily step bars for a report period against a dashed goal line; goal days in blue, the rest lighter. */
final class BarChartView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final KadammTheme t;private final long[] values;private final String[] labels;private final int goal;
    BarChartView(Context c,KadammTheme theme,long[] values,String[] labels,int goal){super(c);t=theme;this.values=values;this.labels=labels;this.goal=Math.max(1,goal);setContentDescription("Steps for each day of the period, compared with your goal");}
    private float dp(float v){return v*getResources().getDisplayMetrics().density;}
    @Override protected void onDraw(Canvas c){
        float w=getWidth(),h=getHeight(),top=dp(10),bottom=dp(18),ch=h-top-bottom;int n=values.length;float slot=w/n;
        long max=goal;for(long v:values)max=Math.max(max,v);float scale=ch/(max*1.08f),base=top+ch;
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));p.setColor(t.textMuted);p.setPathEffect(new DashPathEffect(new float[]{dp(4),dp(4)},0));
        c.drawLine(0,base-goal*scale,w,base-goal*scale,p);p.setPathEffect(null);p.setStyle(Paint.Style.FILL);
        for(int i=0;i<n;i++){
            float bw=Math.max(dp(3),Math.min(dp(22),slot*.6f)),cx=slot*(i+.5f),bh=values[i]*scale;
            p.setColor(values[i]>=goal?t.accent:t.accentSecondary);if(values[i]<goal)p.setAlpha(170);
            c.drawRoundRect(new RectF(cx-bw/2,base-Math.max(values[i]>0?dp(3):dp(1.5f),bh),cx+bw/2,base),dp(4),dp(4),p);p.setAlpha(255);
            if(n<=7||i%5==0||i==n-1){p.setColor(t.textMuted);p.setTextSize(dp(9));p.setTextAlign(Paint.Align.CENTER);c.drawText(labels[i],cx,h-dp(4),p);}
        }
        p.setTextAlign(Paint.Align.LEFT);
    }
}
