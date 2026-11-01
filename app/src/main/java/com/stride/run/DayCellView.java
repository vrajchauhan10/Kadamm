package com.stride.run;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

/**
 * One calendar day as a bubble. The faint ring is the goal; the bubble inside grows with the day's steps.
 * A goal day fills the ring as a solid green bubble with a tick, and 150% of the goal adds a halo, so finished
 * and unfinished days read at a glance. A dot under the date marks recorded sessions.
 */
final class DayCellView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final RectF box=new RectF();private final KadammTheme t;
    private final int day;private final float fraction;private final boolean today,selected,session,future;

    /** fraction &lt; 0 means no step data for the day. */
    DayCellView(Context c,KadammTheme theme,int day,float fraction,boolean today,boolean selected,boolean session,boolean future){
        super(c);t=theme;this.day=day;this.fraction=fraction;this.today=today;this.selected=selected;this.session=session;this.future=future;
    }
    private float dp(float v){return v*getResources().getDisplayMetrics().density;}
    private static int alpha(int c,int a){return Color.argb(a,Color.red(c),Color.green(c),Color.blue(c));}

    @Override protected void onDraw(Canvas c){
        float w=getWidth(),h=getHeight(),cx=w/2,cy=h/2-dp(2),R=Math.min(w,h)/2-dp(4);
        boolean done=fraction>=1f,data=fraction>=0&&!future;
        if(selected){p.setStyle(Paint.Style.FILL);p.setColor(t.surfaceRaised);box.set(dp(2),dp(1),w-dp(2),h-dp(1));c.drawRoundRect(box,dp(12),dp(12),p);}
        if(data&&!done){
            // Goal ring, then the bubble scaled by area so twice the steps looks twice as big.
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));p.setColor(alpha(t.textMuted,90));p.setPathEffect(new DashPathEffect(new float[]{dp(2),dp(2.5f)},0));c.drawCircle(cx,cy,R,p);p.setPathEffect(null);
            if(fraction>0){float r=Math.max(dp(5),R*(float)Math.sqrt(fraction));p.setStyle(Paint.Style.FILL);p.setColor(Color.BLACK);
                p.setShader(new RadialGradient(cx-r*.3f,cy-r*.3f,r*1.4f,alpha(t.accentSecondary,t.dark?200:170),alpha(t.accent,t.dark?150:120),Shader.TileMode.CLAMP));c.drawCircle(cx,cy,r,p);p.setShader(null);}
        }else if(done){
            if(fraction>=1.5f){p.setStyle(Paint.Style.FILL);p.setColor(alpha(t.positive,60));c.drawCircle(cx,cy,R+dp(3),p);}
            p.setStyle(Paint.Style.FILL);p.setColor(Color.BLACK);p.setShader(new RadialGradient(cx-R*.35f,cy-R*.35f,R*1.5f,blend(t.positive,Color.WHITE,.25f),t.positive,Shader.TileMode.CLAMP));c.drawCircle(cx,cy,R,p);p.setShader(null);
            // Tick badge, top right.
            float bx=cx+R*.72f,by=cy-R*.72f,br=dp(6);p.setColor(t.surface);c.drawCircle(bx,by,br,p);p.setColor(t.positive);c.drawCircle(bx,by,br-dp(1.5f),p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1.4f));p.setStrokeCap(Paint.Cap.ROUND);p.setColor(Color.WHITE);c.drawLine(bx-dp(2.2f),by,bx-dp(.6f),by+dp(1.7f),p);c.drawLine(bx-dp(.6f),by+dp(1.7f),bx+dp(2.4f),by-dp(1.8f),p);
        }
        if(today){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));p.setColor(t.text);c.drawCircle(cx,cy,R+dp(2.5f),p);}
        p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(dp(13));
        p.setTypeface(Typeface.create(today||selected||done?"sans-serif-medium":"sans-serif",Typeface.NORMAL));
        p.setColor(done?Color.WHITE:future?alpha(t.textMuted,110):data?t.text:t.textMuted);
        c.drawText(String.valueOf(day),cx,cy+dp(4.5f),p);
        if(session){p.setColor(done?t.positive:t.accent);c.drawCircle(cx,h-dp(3.5f),dp(2.2f),p);}
        p.setTextAlign(Paint.Align.LEFT);
    }
    private static int blend(int a,int b,float f){return Color.rgb((int)(Color.red(a)+(Color.red(b)-Color.red(a))*f),(int)(Color.green(a)+(Color.green(b)-Color.green(a))*f),(int)(Color.blue(a)+(Color.blue(b)-Color.blue(a))*f));}
}
