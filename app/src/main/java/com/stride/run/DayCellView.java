package com.stride.run;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/** One calendar day: the date inside a small ring that fills toward the step goal, a dot when sessions were recorded. */
final class DayCellView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final RectF oval=new RectF();private final KadammTheme t;
    private final int day;private final float fraction;private final boolean today,selected,session,future;

    /** fraction &lt; 0 means no step data for the day. */
    DayCellView(Context c,KadammTheme theme,int day,float fraction,boolean today,boolean selected,boolean session,boolean future){
        super(c);t=theme;this.day=day;this.fraction=fraction;this.today=today;this.selected=selected;this.session=session;this.future=future;
    }
    private float dp(float v){return v*getResources().getDisplayMetrics().density;}

    @Override protected void onDraw(Canvas c){
        float w=getWidth(),h=getHeight(),cx=w/2,cy=h/2,r=Math.min(w,h)/2-dp(4);
        boolean done=fraction>=1f;
        if(selected){p.setStyle(Paint.Style.FILL);p.setColor(t.surfaceRaised);c.drawCircle(cx,cy,r+dp(2.5f),p);}
        if(done){p.setStyle(Paint.Style.FILL);p.setColor(t.accent);c.drawCircle(cx,cy,r,p);}
        else if(fraction>=0&&!future){
            oval.set(cx-r,cy-r,cx+r,cy+r);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(3));p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(t.border);c.drawArc(oval,0,360,false,p);
            if(fraction>0){p.setColor(t.accent);c.drawArc(oval,-90,Math.max(8f,fraction*360f),false,p);}
        }
        if(today&&!done){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1.5f));p.setColor(t.text);c.drawCircle(cx,cy,r+dp(2.5f),p);}
        p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(dp(13));
        p.setTypeface(Typeface.create(today||selected?"sans-serif-medium":"sans-serif",Typeface.NORMAL));
        p.setColor(done?t.accentOn:future?t.textMuted:t.text);
        c.drawText(String.valueOf(day),cx,cy+dp(4.5f),p);
        if(session){p.setColor(done?t.accentOn:t.accent);c.drawCircle(cx,cy+dp(10.5f),dp(2),p);}
        p.setTextAlign(Paint.Align.LEFT);
    }
}
