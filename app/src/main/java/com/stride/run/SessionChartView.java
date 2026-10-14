package com.stride.run;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import java.util.List;

/** Pace or elevation against distance, with walking stretches shaded blue and running stretches red. */
final class SessionChartView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final KadammTheme t;private final List<double[]> data;private final boolean pace;
    static final int RUN=Color.rgb(234,67,53);

    /** data: {km, value, isRun}. For pace the axis is flipped so faster sits higher. */
    SessionChartView(Context c,KadammTheme theme,List<double[]> data,boolean pace){
        super(c);t=theme;this.data=data;this.pace=pace;
        setContentDescription(pace?"Pace along the route; running sections shaded red, walking blue":"Elevation along the route");
    }
    private float dp(float v){return v*getResources().getDisplayMetrics().density;}
    private String label(double v){return pace?Insights.clock(v):String.format(java.util.Locale.US,"%.0f m",v);}

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);if(data.size()<2)return;
        float w=getWidth(),h=getHeight(),left=dp(44),right=dp(6),top=dp(8),bottom=dp(20),cw=w-left-right,ch=h-top-bottom;
        double maxX=data.get(data.size()-1)[0],lo=Double.MAX_VALUE,hi=-Double.MAX_VALUE;
        for(double[] d:data){lo=Math.min(lo,d[1]);hi=Math.max(hi,d[1]);}
        if(hi-lo<(pace?30:4)){double mid=(hi+lo)/2,half=pace?15:2;lo=mid-half;hi=mid+half;}
        double pad=(hi-lo)*.08;lo-=pad;hi+=pad;
        final double flo=lo,fhi=hi;
        java.util.function.DoubleUnaryOperator xs=x->left+cw*(maxX<=0?0:x/maxX);
        java.util.function.DoubleUnaryOperator ys=v->pace?top+ch*(v-flo)/(fhi-flo):top+ch*(1-(v-flo)/(fhi-flo));
        // shade walk / run stretches
        p.setStyle(Paint.Style.FILL);
        for(int i=1;i<data.size();i++){
            boolean run=data.get(i)[2]>0;p.setColor(run?Color.argb(46,234,67,53):Color.argb(30,Color.red(t.accent),Color.green(t.accent),Color.blue(t.accent)));
            c.drawRect((float)xs.applyAsDouble(data.get(i-1)[0]),top,(float)xs.applyAsDouble(data.get(i)[0])+1,top+ch,p);
        }
        p.setColor(t.border);p.setStrokeWidth(dp(1));c.drawLine(left,top+ch,left+cw,top+ch,p);
        Path line=new Path();
        for(int i=0;i<data.size();i++){float x=(float)xs.applyAsDouble(data.get(i)[0]),y=(float)ys.applyAsDouble(data.get(i)[1]);if(i==0)line.moveTo(x,y);else line.lineTo(x,y);}
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));p.setStrokeJoin(Paint.Join.ROUND);p.setColor(t.text);c.drawPath(line,p);
        p.setStyle(Paint.Style.FILL);p.setColor(t.textMuted);p.setTextSize(dp(10));
        p.setTextAlign(Paint.Align.RIGHT);
        c.drawText(label(pace?flo:fhi),left-dp(6),top+dp(9),p);c.drawText(label(pace?fhi:flo),left-dp(6),top+ch,p);
        p.setTextAlign(Paint.Align.LEFT);c.drawText("0 km",left,h-dp(5),p);
        p.setTextAlign(Paint.Align.RIGHT);c.drawText(String.format(java.util.Locale.US,"%.2f km",maxX),left+cw,h-dp(5),p);p.setTextAlign(Paint.Align.LEFT);
    }
}
