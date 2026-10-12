package com.stride.run;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;

/** Seven days of full-day step totals against the daily goal. Bars take the goal-progress colour. */
final class WeeklyActivityView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Map<String,Long> totals;private final int goal;private final KadammTheme theme;

    WeeklyActivityView(Context context,Map<String,Long> totals,int goal,KadammTheme theme){
        super(context);this.totals=totals;this.goal=Math.max(1,goal);this.theme=theme;
        setContentDescription("Steps for each of the last seven days, compared with your daily goal");
    }
    private float dp(float v){return v*getResources().getDisplayMetrics().density;}
    private static String compact(long n){return n>=10000?String.format(Locale.US,"%.0fk",n/1000d):n>=1000?String.format(Locale.US,"%.1fk",n/1000d):String.valueOf(n);}

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float w=getWidth(),h=getHeight(),left=dp(4),right=dp(34),top=dp(18),bottom=dp(22),chartH=h-top-bottom,slot=(w-left-right)/7;
        long[] steps=new long[7];long max=goal;
        Calendar today=Calendar.getInstance();
        for(int i=0;i<7;i++){
            Calendar d=(Calendar)today.clone();d.add(Calendar.DAY_OF_YEAR,-(6-i));
            Long v=totals.get(DaySteps.key(d.getTimeInMillis()));
            steps[i]=v==null?0:v;max=Math.max(max,steps[i]);
        }
        float scale=chartH/(max*1.08f),base=top+chartH,goalY=base-goal*scale;

        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));p.setColor(theme.border);
        c.drawLine(left,base,w-right,base,p);
        p.setColor(theme.textMuted);p.setPathEffect(new DashPathEffect(new float[]{dp(4),dp(4)},0));
        c.drawLine(left,goalY,w-right,goalY,p);
        p.setPathEffect(null);p.setStyle(Paint.Style.FILL);
        p.setTextSize(dp(9));p.setTextAlign(Paint.Align.LEFT);
        c.drawText(compact(goal),w-right+dp(5),goalY+dp(3),p);

        for(int i=0;i<7;i++){
            float cx=left+slot*(i+.5f),bw=Math.min(dp(22),slot*.5f),bh=steps[i]*scale;
            boolean isToday=i==6;
            if(steps[i]>0){
                p.setColor(ProgressRingView.colorFor(theme,steps[i]*100d/goal));
                p.setAlpha(isToday?255:185);
                c.drawRoundRect(new RectF(cx-bw/2,base-Math.max(dp(3),bh),cx+bw/2,base),dp(5),dp(5),p);
                p.setAlpha(255);
            }else{
                p.setColor(theme.surfaceRaised);
                c.drawRoundRect(new RectF(cx-bw/2,base-dp(3),cx+bw/2,base),dp(2),dp(2),p);
            }
            p.setTextAlign(Paint.Align.CENTER);p.setTextSize(dp(9));
            if(steps[i]>0){p.setColor(isToday?theme.text:theme.textMuted);c.drawText(compact(steps[i]),cx,base-Math.max(dp(3),bh)-dp(4),p);}
            Calendar d=(Calendar)today.clone();d.add(Calendar.DAY_OF_YEAR,-(6-i));
            p.setColor(isToday?theme.text:theme.textMuted);
            c.drawText(isToday?"Today":new SimpleDateFormat("EEE",Locale.getDefault()).format(d.getTime()),cx,h-dp(5),p);
        }
        p.setTextAlign(Paint.Align.LEFT);
    }
}
