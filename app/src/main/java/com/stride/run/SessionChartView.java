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
    private String label(double v){return pace?Insights.clock(v)+"/km":String.format(java.util.Locale.US,"%.0f m",v);}

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);if(data.size()<2)return;
        float w=getWidth(),h=getHeight(),left=dp(52),right=dp(10),top=dp(10),bottom=dp(24),cw=w-left-right,ch=h-top-bottom;
        double maxX=data.get(data.size()-1)[0],lo=Double.MAX_VALUE,hi=-Double.MAX_VALUE,sum=0;
        for(double[] d:data){lo=Math.min(lo,d[1]);hi=Math.max(hi,d[1]);sum+=d[1];}
        double avg=sum/data.size();
        if(hi-lo<(pace?60:6)){double mid=(hi+lo)/2,half=pace?30:3;lo=mid-half;hi=mid+half;}
        double pad=(hi-lo)*.1;lo-=pad;hi+=pad;if(!pace)lo=Math.max(lo,Math.floor(lo));
        final double flo=lo,fhi=hi;
        java.util.function.DoubleUnaryOperator xs=x->left+cw*(maxX<=0?0:x/maxX);
        java.util.function.DoubleUnaryOperator ys=v->pace?top+ch*(v-flo)/(fhi-flo):top+ch*(1-(v-flo)/(fhi-flo));
        // walk / run bands
        p.setStyle(Paint.Style.FILL);
        for(int i=1;i<data.size();i++){
            boolean run=data.get(i)[2]>0;p.setColor(run?Color.argb(34,234,67,53):Color.argb(22,Color.red(t.accent),Color.green(t.accent),Color.blue(t.accent)));
            c.drawRect((float)xs.applyAsDouble(data.get(i-1)[0]),top,(float)xs.applyAsDouble(data.get(i)[0])+1,top+ch,p);
        }
        // three gridlines with labels
        p.setTextSize(dp(10));p.setTextAlign(Paint.Align.RIGHT);
        for(int g=0;g<3;g++){
            double v=flo+(fhi-flo)*g/2.0;float y=(float)ys.applyAsDouble(v);
            p.setColor(t.border);p.setStrokeWidth(dp(1));c.drawLine(left,y,left+cw,y,p);
            p.setColor(t.textMuted);c.drawText(label(v),left-dp(6),y+dp(3.5f),p);
        }
        // area under elevation
        if(!pace){Path area=new Path();area.moveTo((float)xs.applyAsDouble(data.get(0)[0]),top+ch);for(double[] d:data)area.lineTo((float)xs.applyAsDouble(d[0]),(float)ys.applyAsDouble(d[1]));area.lineTo((float)xs.applyAsDouble(maxX),top+ch);area.close();p.setColor(Color.argb(40,Color.red(t.accent),Color.green(t.accent),Color.blue(t.accent)));c.drawPath(area,p);}
        // line, coloured by walk (blue) or run (red)
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2.5f));p.setStrokeCap(Paint.Cap.ROUND);
        for(int i=1;i<data.size();i++){
            p.setColor(data.get(i)[2]>0?RUN:t.accent);
            c.drawLine((float)xs.applyAsDouble(data.get(i-1)[0]),(float)ys.applyAsDouble(data.get(i-1)[1]),(float)xs.applyAsDouble(data.get(i)[0]),(float)ys.applyAsDouble(data.get(i)[1]),p);
        }
        // average line
        float ay=(float)ys.applyAsDouble(avg);p.setColor(t.text);p.setStrokeWidth(dp(1));p.setPathEffect(new android.graphics.DashPathEffect(new float[]{dp(5),dp(4)},0));c.drawLine(left,ay,left+cw,ay,p);p.setPathEffect(null);
        p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.RIGHT);p.setColor(t.text);p.setTextSize(dp(10));c.drawText("avg "+label(avg),left+cw,ay-dp(4),p);
        // distance axis: start, middle, end
        p.setColor(t.textMuted);p.setTextAlign(Paint.Align.LEFT);c.drawText("0 km",left,h-dp(6),p);
        p.setTextAlign(Paint.Align.CENTER);c.drawText(String.format(java.util.Locale.US,"%.2f km",maxX/2),left+cw/2,h-dp(6),p);
        p.setTextAlign(Paint.Align.RIGHT);c.drawText(String.format(java.util.Locale.US,"%.2f km",maxX),left+cw,h-dp(6),p);p.setTextAlign(Paint.Align.LEFT);
    }
}
