package com.stride.run;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;

/**
 * The month as a landscape: daily steps drawn as terrain, the goal as a dashed horizon, and everything above
 * it green. The biggest day gets a label. Tap, or hold and drag, to read any day's steps; days after today stay flat.
 */
final class LandscapeView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final KadammTheme t;private final long[] v;private final int goal,last;private int selected=-1;

    /** {@code values} has one entry per day of the month; {@code lastDay} is the last day that has happened (0-based), or -1. */
    LandscapeView(Context c,KadammTheme theme,long[] values,int goal,int lastDay){
        super(c);t=theme;v=values;this.goal=Math.max(1,goal);last=Math.min(values.length-1,lastDay);
        setContentDescription("Steps through the month as a landscape; peaks above the dashed horizon reached your goal. Touch to read a day.");
    }
    private float dp(float x){return x*getResources().getDisplayMetrics().density;}
    private float x(int i,float w){return dp(6)+i*(w-dp(12))/Math.max(1,v.length-1);}

    private long downAt;private boolean scrubbing;
    /**
     * A tap reads one day; press and hold, then drag, to scrub. A quick sideways swipe is left to the tab pager
     * (History is a tab), so the chart never traps it.
     */
    @Override public boolean onTouchEvent(MotionEvent e){
        int a=e.getActionMasked();
        if(a==MotionEvent.ACTION_DOWN){downAt=e.getEventTime();scrubbing=false;pick(e.getX());}
        else if(a==MotionEvent.ACTION_MOVE&&!scrubbing&&e.getEventTime()-downAt>280){scrubbing=true;RouteView.gestureActive=true;if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);}
        if(a==MotionEvent.ACTION_MOVE&&scrubbing)pick(e.getX());
        if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL){scrubbing=false;RouteView.gestureActive=false;}
        return true;
    }
    private void pick(float px){float w=getWidth();int i=Math.round((px-dp(6))/((w-dp(12))/Math.max(1,v.length-1)));i=Math.max(0,Math.min(Math.max(0,last),i));if(i!=selected){selected=i;performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);invalidate();}}

    @Override protected void onDraw(Canvas c){
        float w=getWidth(),h=getHeight(),top=dp(30),base=h-dp(16);int n=v.length;if(n==0)return;
        long max=goal;for(long x:v)max=Math.max(max,x);float scale=(base-top)/(max*1.05f),gy=base-goal*scale;
        Path area=new Path();area.moveTo(x(0,w),base);
        for(int i=0;i<n;i++){float y=base-(i<=last?v[i]:0)*scale;if(i==0)area.lineTo(x(0,w),y);else{float px=x(i-1,w),py=base-(i-1<=last?v[i-1]:0)*scale,mx=(px+x(i,w))/2;area.cubicTo(mx,py,mx,y,x(i,w),y);}}
        area.lineTo(x(n-1,w),base);area.close();
        p.setStyle(Paint.Style.FILL);p.setColor(t.dark?Color.rgb(31,42,61):Color.rgb(222,233,252));c.drawPath(area,p);
        c.save();c.clipRect(0,0,w,gy);p.setColor(Color.argb(225,Color.red(t.positive),Color.green(t.positive),Color.blue(t.positive)));c.drawPath(area,p);c.restore();
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1.6f));p.setColor(t.accent);c.drawPath(area,p);
        p.setStrokeWidth(dp(1));p.setColor(Color.argb(140,Color.red(t.text),Color.green(t.text),Color.blue(t.text)));p.setPathEffect(new DashPathEffect(new float[]{dp(4),dp(3)},0));c.drawLine(0,gy,w,gy,p);p.setPathEffect(null);
        p.setStyle(Paint.Style.FILL);p.setTextSize(dp(10));p.setColor(t.textMuted);p.setTextAlign(Paint.Align.LEFT);c.drawText("goal "+compact(goal),dp(2),gy-dp(4),p);
        p.setTextAlign(Paint.Align.CENTER);for(int i=0;i<n;i+=7)c.drawText(String.valueOf(i+1),x(i,w),h-dp(2),p);
        int peak=-1;for(int i=0;i<=last;i++)if(peak<0||v[i]>v[peak])peak=i;
        if(selected<0&&peak>=0&&v[peak]>0){p.setColor(v[peak]>=goal?t.positive:t.accent);p.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));c.drawText(compact(v[peak])+" peak",Math.max(dp(26),Math.min(w-dp(26),x(peak,w))),base-v[peak]*scale-dp(6),p);p.setTypeface(null);}
        if(selected>=0){float sx=x(selected,w),sy=base-v[selected]*scale;
            p.setColor(t.text);c.drawCircle(sx,sy,dp(4),p);
            String label=String.format(Locale.US,"%d %s · %,d steps",selected+1,v[selected]>=goal?"✓":"",v[selected]).replace("  "," ");
            p.setTextSize(dp(11));float tw=p.measureText(label)+dp(16),bx=Math.max(0,Math.min(w-tw,sx-tw/2));
            p.setColor(t.text);c.drawRoundRect(new RectF(bx,dp(2),bx+tw,dp(24)),dp(8),dp(8),p);p.setColor(t.background);p.setTextAlign(Paint.Align.LEFT);c.drawText(label,bx+dp(8),dp(17),p);}
        p.setTextAlign(Paint.Align.LEFT);
    }
    private static String compact(long v){return v>=1000?String.format(Locale.US,"%.1fk",v/1000f).replace(".0k","k"):String.valueOf(v);}
}
