package com.stride.run;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/**
 * One calendar day in the streak-chain calendar. A goal day is a solid green capsule; when the day before or
 * after (in the same week row) was also a goal day, the capsule runs to the cell edge on that side, so a streak
 * reads as one unbroken bar. Other days are dark pills with a thin bar showing how far toward the goal they got.
 * A dot under the date marks recorded sessions.
 */
final class DayCellView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final RectF box=new RectF();private final Path path=new Path();private final KadammTheme t;
    private final int day;private final float fraction;private final boolean today,selected,session,future,linkLeft,linkRight;

    /** fraction &lt; 0 means no step data for the day; links join this goal day to a neighbouring goal day. */
    DayCellView(Context c,KadammTheme theme,int day,float fraction,boolean today,boolean selected,boolean session,boolean future,boolean linkLeft,boolean linkRight){
        super(c);t=theme;this.day=day;this.fraction=fraction;this.today=today;this.selected=selected;this.session=session;this.future=future;this.linkLeft=linkLeft;this.linkRight=linkRight;
    }
    private float dp(float v){return v*getResources().getDisplayMetrics().density;}
    private static int alpha(int c,int a){return Color.argb(a,Color.red(c),Color.green(c),Color.blue(c));}

    @Override protected void onDraw(Canvas c){
        float w=getWidth(),h=getHeight(),pad=dp(3),top=dp(5),bottom=h-dp(9),r=(bottom-top)/2;
        boolean done=fraction>=1f,data=fraction>=0&&!future;
        float left=done&&linkLeft?0:pad,right=done&&linkRight?w:w-pad;
        box.set(left,top,right,bottom);
        float[] radii={done&&linkLeft?0:r,done&&linkLeft?0:r,done&&linkRight?0:r,done&&linkRight?0:r,done&&linkRight?0:r,done&&linkRight?0:r,done&&linkLeft?0:r,done&&linkLeft?0:r};
        path.reset();path.addRoundRect(box,radii,Path.Direction.CW);
        p.setStyle(Paint.Style.FILL);
        if(done){p.setColor(fraction>=1.5f?blend(t.positive,Color.WHITE,.12f):t.positive);c.drawPath(path,p);}
        else if(data){p.setColor(t.dark?Color.rgb(31,42,61):Color.rgb(232,240,254));c.drawPath(path,p);
            // How close the day got: a short bar inside the pill.
            if(fraction>0){float bw=(box.width()-r*1.2f)*Math.min(1,fraction),bx=box.left+r*.6f,by=box.bottom-dp(5);p.setColor(alpha(t.accent,220));c.drawRoundRect(new RectF(bx,by-dp(2.5f),bx+Math.max(dp(3),bw),by),dp(2),dp(2),p);}}
        else if(!future){p.setColor(t.dark?Color.rgb(22,22,26):Color.rgb(241,241,243));c.drawPath(path,p);}
        if(selected||today){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(selected?2:1.5f));p.setColor(selected?t.accent:t.text);RectF o=new RectF(pad-dp(1.5f),top-dp(1.5f),w-pad+dp(1.5f),bottom+dp(1.5f));c.drawRoundRect(o,r+dp(1.5f),r+dp(1.5f),p);p.setStyle(Paint.Style.FILL);}
        p.setTextAlign(Paint.Align.CENTER);p.setTextSize(dp(13));
        p.setTypeface(Typeface.create(today||selected||done?"sans-serif-medium":"sans-serif",Typeface.NORMAL));
        p.setColor(done?(t.dark?Color.rgb(11,42,20):Color.WHITE):future?alpha(t.textMuted,110):data?t.text:t.textMuted);
        c.drawText(String.valueOf(day),w/2,(top+bottom)/2+dp(4.5f)-(data&&!done&&fraction>0?dp(2):0),p);
        if(session){p.setColor(done?t.positive:t.accent);c.drawCircle(w/2,h-dp(3.5f),dp(2.2f),p);}
        p.setTextAlign(Paint.Align.LEFT);
    }
    private static int blend(int a,int b,float f){return Color.rgb((int)(Color.red(a)+(Color.red(b)-Color.red(a))*f),(int)(Color.green(a)+(Color.green(b)-Color.green(a))*f),(int)(Color.blue(a)+(Color.blue(b)-Color.blue(a))*f));}
}
