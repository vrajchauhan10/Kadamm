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
        int n=v.length;if(n==0)return;
        float w=getWidth(),h=getHeight(),top=dp(34),base=h-dp(22);
        // Headroom so the goal sits well inside the chart and the peak label never clips.
        long hi=0;for(int i=0;i<=last;i++)hi=Math.max(hi,v[i]);double max=Math.max(goal*1.3,hi*1.12);float scale=(float)((base-top)/max),gy=base-goal*scale;
        // Past days only: the line ends today instead of dropping to zero for the days to come.
        Path area=new Path(),line=new Path();int end=Math.max(0,last);
        for(int i=0;i<=end;i++){float x=x(i,w),y=base-v[i]*scale;
            if(i==0){area.moveTo(x,base);area.lineTo(x,y);line.moveTo(x,y);}
            else{float px=x(i-1,w),py=base-v[i-1]*scale,mx=(px+x)/2;area.cubicTo(mx,py,mx,y,x,y);line.cubicTo(mx,py,mx,y,x,y);}}
        area.lineTo(x(end,w),base);area.close();
        p.setStyle(Paint.Style.FILL);p.setColor(Color.BLACK);p.setShader(new android.graphics.LinearGradient(0,top,0,base,MetricViews.alpha(t.accent,t.dark?150:110),MetricViews.alpha(t.accent,18),android.graphics.Shader.TileMode.CLAMP));c.drawPath(area,p);p.setShader(null);
        c.save();c.clipRect(0,0,w,gy);p.setColor(MetricViews.alpha(t.positive,t.dark?210:190));c.drawPath(area,p);c.restore();
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));p.setStrokeJoin(Paint.Join.ROUND);p.setColor(t.accent);c.drawPath(line,p);
        // Days still to come: a faint dotted floor.
        if(end<n-1){p.setStrokeWidth(dp(1.5f));p.setColor(MetricViews.alpha(t.textMuted,90));p.setPathEffect(new DashPathEffect(new float[]{dp(1.5f),dp(4)},0));c.drawLine(x(end,w)+dp(4),base,x(n-1,w),base,p);p.setPathEffect(null);}
        // Goal horizon with its label in a pill on the right.
        p.setStrokeWidth(dp(1));p.setColor(MetricViews.alpha(t.text,120));p.setPathEffect(new DashPathEffect(new float[]{dp(4),dp(3)},0));c.drawLine(0,gy,w,gy,p);p.setPathEffect(null);
        p.setStyle(Paint.Style.FILL);p.setTextSize(dp(10));String gl="goal "+compact(goal);float gw=p.measureText(gl)+dp(12);
        p.setColor(t.surfaceRaised);c.drawRoundRect(new RectF(w-gw,gy-dp(9),w,gy+dp(9)),dp(9),dp(9),p);p.setColor(t.textMuted);p.setTextAlign(Paint.Align.LEFT);c.drawText(gl,w-gw+dp(6),gy+dp(3.5f),p);
        // A dot per day: green on goal days.
        for(int i=0;i<=end;i++){if(v[i]<=0)continue;float x=x(i,w),y=base-v[i]*scale;p.setColor(t.background);c.drawCircle(x,y,dp(3.4f),p);p.setColor(v[i]>=goal?t.positive:t.accent);c.drawCircle(x,y,dp(2.4f),p);}
        // Today ring.
        if(last>=0){float x=x(last,w),y=base-v[last]*scale;p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1.5f));p.setColor(t.text);c.drawCircle(x,y,dp(5.5f),p);p.setStyle(Paint.Style.FILL);}
        p.setTextAlign(Paint.Align.CENTER);p.setColor(t.textMuted);for(int i=0;i<n;i+=7){if(last>=0&&last%7!=0&&Math.abs(x(i,w)-x(last,w))<dp(30))continue;c.drawText(String.valueOf(i+1),x(i,w),h-dp(5),p);}
        if(last>=0&&last%7!=0){p.setColor(t.text);p.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));c.drawText("today",Math.min(w-dp(16),x(last,w)),h-dp(5),p);p.setTypeface(null);}
        int peak=-1;for(int i=0;i<=last;i++)if(peak<0||v[i]>v[peak])peak=i;
        if(selected<0&&peak>=0&&v[peak]>0)pill(c,compact(v[peak])+" peak · "+(peak+1),x(peak,w),base-v[peak]*scale,v[peak]>=goal?t.positive:t.accent,w);
        if(selected>=0){float sx=x(selected,w),sy=base-v[selected]*scale;
            p.setColor(t.text);p.setStrokeWidth(dp(1));c.drawLine(sx,top-dp(4),sx,base,p);c.drawCircle(sx,sy,dp(4.5f),p);
            pill(c,String.format(Locale.US,"%d · %,d steps%s",selected+1,v[selected],v[selected]>=goal?" ✓":""),sx,top-dp(2),t.text,w);}
        p.setTextAlign(Paint.Align.LEFT);
    }
    /** A rounded label centred above (x, y), kept inside the view. */
    private void pill(Canvas c,String text,float x,float y,int color,float w){
        p.setTextSize(dp(10.5f));p.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));float tw=p.measureText(text)+dp(14),bx=Math.max(0,Math.min(w-tw,x-tw/2)),by=Math.max(0,y-dp(28));
        p.setColor(color);c.drawRoundRect(new RectF(bx,by,bx+tw,by+dp(20)),dp(10),dp(10),p);p.setColor(t.dark&&color==t.text?t.background:Color.WHITE);p.setTextAlign(Paint.Align.LEFT);c.drawText(text,bx+dp(7),by+dp(14),p);p.setTypeface(null);
    }
    private static String compact(long v){return v>=1000?String.format(Locale.US,"%.1fk",v/1000f).replace(".0k","k"):String.valueOf(v);}
}
