package com.stride.run;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;

/**
 * Daily step bars for a report period against a dashed goal line; goal days in full blue, the rest lighter,
 * days still to come as faint stubs. Touch or drag across the bars to see a bubble with that day's steps.
 */
final class BarChartView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final RectF r=new RectF();private final KadammTheme t;
    private final long[] values;private final String[] labels,names;private final int goal,future;private int selected=-1;

    /** {@code names} are the full labels shown in the bubble; the last {@code future} bars are days not reached yet. */
    BarChartView(Context c,KadammTheme theme,long[] values,String[] labels,String[] names,int goal,int future){
        super(c);t=theme;this.values=values;this.labels=labels;this.names=names;this.goal=Math.max(1,goal);this.future=Math.max(0,future);
        setContentDescription("Steps for each day of the period, compared with your goal. Touch a bar to see its steps.");
    }
    private float dp(float v){return v*getResources().getDisplayMetrics().density;}
    private float top(){return dp(46);}

    @Override public boolean onTouchEvent(MotionEvent e){
        int a=e.getActionMasked();
        if(a==MotionEvent.ACTION_DOWN){RouteView.gestureActive=true;if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);}
        if(a==MotionEvent.ACTION_DOWN||a==MotionEvent.ACTION_MOVE){
            int n=values.length,i=Math.max(0,Math.min(n-1,(int)(e.getX()/(getWidth()/(float)n))));
            if(i!=selected){selected=i;performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);invalidate();}
        }
        if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL)RouteView.gestureActive=false;
        return true;
    }

    @Override protected void onDraw(Canvas c){
        float w=getWidth(),h=getHeight(),top=top(),bottom=dp(18),ch=h-top-bottom;int n=values.length;float slot=w/n;
        long max=goal;for(long v:values)max=Math.max(max,v);float scale=ch/(max*1.08f),base=top+ch;
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));p.setColor(t.textMuted);p.setPathEffect(new DashPathEffect(new float[]{dp(4),dp(4)},0));
        float gy=base-goal*scale;c.drawLine(0,gy,w,gy,p);p.setPathEffect(null);p.setStyle(Paint.Style.FILL);
        p.setTextSize(dp(9));p.setTextAlign(Paint.Align.RIGHT);p.setColor(t.textMuted);c.drawText(compact(goal)+" goal",w,gy-dp(4),p);
        for(int i=0;i<n;i++){
            float bw=Math.max(dp(3),Math.min(dp(22),slot*.6f)),cx=slot*(i+.5f),bh=values[i]*scale;boolean upcoming=i>=n-future,sel=i==selected;
            r.set(cx-bw/2,base-Math.max(values[i]>0?dp(3):dp(1.5f),bh),cx+bw/2,base);
            if(upcoming){p.setColor(Color.argb(60,Color.red(t.textMuted),Color.green(t.textMuted),Color.blue(t.textMuted)));r.top=base-dp(2);}
            else if(values[i]>=goal){p.setColor(Color.BLACK);p.setShader(new LinearGradient(0,r.top,0,base,t.accent,Color.argb(200,Color.red(t.accent),Color.green(t.accent),Color.blue(t.accent)),Shader.TileMode.CLAMP));}
            else p.setColor(Color.argb(selected<0||sel?170:110,Color.red(t.accentSecondary),Color.green(t.accentSecondary),Color.blue(t.accentSecondary)));
            if(selected>=0&&!sel&&values[i]>=goal&&!upcoming)p.setAlpha(150);
            c.drawRoundRect(r,dp(4),dp(4),p);p.setShader(null);p.setAlpha(255);
            if(n<=7||i%5==0||i==n-1||sel){p.setColor(sel?t.text:t.textMuted);p.setTextSize(dp(9));p.setTextAlign(Paint.Align.CENTER);c.drawText(labels[i],cx,h-dp(4),p);}
        }
        if(selected>=0&&selected<n)bubble(c,slot*(selected+.5f),base-Math.max(dp(3),values[selected]*scale),w,selected>=n-future);
        p.setTextAlign(Paint.Align.LEFT);
    }
    /** The tooltip: day name, steps, and how it compares with the goal. */
    private void bubble(Canvas c,float x,float barTop,float w,boolean upcoming){
        long v=values[selected];String title=selected<names.length?names[selected]:labels[selected];
        String value=upcoming?"Not yet":String.format(Locale.US,"%,d steps",v);String sub=upcoming?"":v>=goal?"✓ goal reached":String.format(Locale.US,"%d%% of goal",Math.round(v*100f/goal));
        Paint tp=new Paint(Paint.ANTI_ALIAS_FLAG);tp.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));
        tp.setTextSize(dp(13));float vw=tp.measureText(value);tp.setTextSize(dp(10));float tw=tp.measureText(title+(sub.isEmpty()?"":"  ·  "+sub));
        float bw=Math.max(vw,tw)+dp(20),bh=dp(42),bx=Math.max(0,Math.min(w-bw,x-bw/2)),by=dp(2);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));p.setColor(Color.argb(120,Color.red(t.textMuted),Color.green(t.textMuted),Color.blue(t.textMuted)));p.setPathEffect(new DashPathEffect(new float[]{dp(2),dp(3)},0));c.drawLine(x,by+bh,x,barTop,p);p.setPathEffect(null);
        p.setStyle(Paint.Style.FILL);p.setColor(t.text);r.set(bx,by,bx+bw,by+bh);c.drawRoundRect(r,dp(10),dp(10),p);
        tp.setColor(t.background);tp.setTextSize(dp(10));tp.setAlpha(190);c.drawText(title+(sub.isEmpty()?"":"  ·  "+sub),bx+dp(10),by+dp(15),tp);
        tp.setAlpha(255);tp.setTextSize(dp(13));c.drawText(value,bx+dp(10),by+dp(33),tp);
        if(!upcoming&&v>=goal){p.setColor(t.positive);c.drawCircle(bx+bw-dp(9),by+dp(9),dp(3),p);}
    }
    private static String compact(long v){return v>=1000?(v%1000==0?v/1000+"k":String.format(Locale.US,"%.1fk",v/1000f)):String.valueOf(v);}
}
