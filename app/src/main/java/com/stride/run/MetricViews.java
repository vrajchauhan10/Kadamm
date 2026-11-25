package com.stride.run;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.View;

import java.util.List;
import java.util.Locale;

/** Small drawn views for the session analysis, reports and badges. All colours come from the theme or the zone palette. */
final class MetricViews {
    private MetricViews() {}
    /** Zone palette, light to vigorous: violet, blue, green, amber, red. */
    static final int[] ZONE_COLORS={Color.rgb(147,112,219),Color.rgb(66,133,244),Color.rgb(52,168,83),Color.rgb(251,168,4),Color.rgb(234,67,53)};
    static int alpha(int c,int a){return Color.argb(a,Color.red(c),Color.green(c),Color.blue(c));}
    static String mmss(double sec){long s=Math.round(sec);return s>=3600?String.format(Locale.US,"%d:%02d:%02d",s/3600,(s/60)%60,s%60):String.format(Locale.US,"%02d:%02d",s/60,s%60);}

    private abstract static class Base extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final KadammTheme t;
        Base(Context c,KadammTheme t){super(c);this.t=t;}
        float dp(float v){return v*getResources().getDisplayMetrics().density;}
    }

    /**
     * A value over time. DOTS colours each sample by the zone it falls in (cadence, stride); AREA draws a line
     * with a gradient fill (heart rate, fitness). A dashed line marks the average.
     */
    static final class Series extends Base {
        interface Fmt { String f(double v); }
        private final List<double[]> d;private final boolean dots;private final int color;private final double[] bounds;private final Fmt y,x;private final double avg;
        Series(Context c,KadammTheme t,List<double[]> data,boolean dots,int color,double[] zoneBounds,double avg,Fmt yFmt,Fmt xFmt){
            super(c,t);d=data;this.dots=dots;this.color=color;bounds=zoneBounds;this.avg=avg;y=yFmt;x=xFmt;
        }
        private int colorOf(double v){if(bounds==null)return color;int z=0;for(double b:bounds)if(v>=b)z++;return ZONE_COLORS[Math.min(4,z+(bounds.length<4?1:0))];}
        @Override protected void onDraw(Canvas c){
            if(d.size()<2)return;float w=getWidth(),h=getHeight(),left=dp(36),right=dp(8),top=dp(8),bottom=dp(22),cw=w-left-right,ch=h-top-bottom;
            double lo=Double.MAX_VALUE,hi=-Double.MAX_VALUE,x0=d.get(0)[0],x1=d.get(d.size()-1)[0];
            for(double[] q:d){lo=Math.min(lo,q[1]);hi=Math.max(hi,q[1]);}
            if(avg>0){lo=Math.min(lo,avg);hi=Math.max(hi,avg);}
            double span=Math.max(hi-lo,Math.max(1,Math.abs(hi)*.1));lo-=span*.15;hi+=span*.15;if(lo<0&&d.get(0)[1]>=0)lo=Math.max(0,lo);
            final double flo=lo,fhi=hi;
            p.setTextSize(dp(9.5f));p.setStrokeWidth(dp(1));
            for(int g=0;g<3;g++){double v=lo+(hi-lo)*(.15+.35*g);float gy=(float)(top+ch*(1-(v-lo)/(hi-lo)));p.setColor(alpha(t.border,200));c.drawLine(left,gy,left+cw,gy,p);p.setColor(t.textMuted);p.setTextAlign(Paint.Align.RIGHT);c.drawText(y.f(v),left-dp(5),gy+dp(3.5f),p);}
            if(dots){
                p.setStyle(Paint.Style.FILL);float r=dp(d.size()>300?1.9f:2.4f);
                for(double[] q:d){float px=(float)(left+cw*(q[0]-x0)/Math.max(1,x1-x0)),py=(float)(top+ch*(1-(q[1]-flo)/(fhi-flo)));p.setColor(q[2]>0?ZONE_COLORS[4]:colorOf(q[1]));c.drawCircle(px,py,r,p);}
            }else{
                Path line=new Path(),area=new Path();
                for(int i=0;i<d.size();i++){double[] q=d.get(i);float px=(float)(left+cw*(q[0]-x0)/Math.max(1,x1-x0)),py=(float)(top+ch*(1-(q[1]-flo)/(fhi-flo)));if(i==0){line.moveTo(px,py);area.moveTo(px,top+ch);area.lineTo(px,py);}else{line.lineTo(px,py);area.lineTo(px,py);}}
                area.lineTo(left+cw,top+ch);area.close();
                p.setStyle(Paint.Style.FILL);p.setColor(Color.BLACK);p.setShader(new LinearGradient(0,top,0,top+ch,alpha(color,t.dark?120:90),alpha(color,0),Shader.TileMode.CLAMP));c.drawPath(area,p);p.setShader(null);
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));p.setStrokeJoin(Paint.Join.ROUND);p.setColor(color);c.drawPath(line,p);p.setStyle(Paint.Style.FILL);
            }
            if(avg>0){float ay=(float)(top+ch*(1-(avg-lo)/(hi-lo)));p.setStyle(Paint.Style.STROKE);p.setColor(alpha(t.text,150));p.setStrokeWidth(dp(1));p.setPathEffect(new DashPathEffect(new float[]{dp(4),dp(3)},0));c.drawLine(left,ay,left+cw,ay,p);p.setPathEffect(null);p.setStyle(Paint.Style.FILL);}
            p.setColor(t.textMuted);p.setTextSize(dp(9.5f));
            for(int i=0;i<=3;i++){double v=x0+(x1-x0)*i/3;p.setTextAlign(i==0?Paint.Align.LEFT:i==3?Paint.Align.RIGHT:Paint.Align.CENTER);c.drawText(x.f(v),left+cw*i/3f,h-dp(5),p);}
            p.setTextAlign(Paint.Align.LEFT);
        }
    }

    /**
     * Relative effort as three bands, higher than usual on top, your recent average in the middle, lower at the
     * bottom; a marker shows where this session lands.
     */
    static final class Bands extends Base {
        private final double ratio;private final int accent;
        Bands(Context c,KadammTheme t,double ratio,int accent){super(c,t);this.ratio=ratio;this.accent=accent;}
        @Override protected void onDraw(Canvas c){
            float w=getWidth(),h=getHeight(),bh=h/3f;String[] names={"Higher than usual","Your 3-week average","Lower than usual"};int[] cs={Color.rgb(211,47,47),Color.rgb(123,31,162),Color.rgb(149,88,214)};
            p.setTextSize(dp(12));p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
            for(int i=0;i<3;i++){p.setColor(cs[i]);c.drawRect(0,i*bh,w,(i+1)*bh,p);p.setColor(Color.WHITE);c.drawText(names[i],dp(14),i*bh+bh/2+dp(4),p);}
            // 0.7× sits at the bottom of the middle band, 1.3× at its top; beyond that the marker moves into the outer bands.
            double r=Math.max(.2,Math.min(2,ratio));float y;
            if(r>=1.3)y=(float)(bh*(1-(Math.min(2,r)-1.3)/.7));else if(r<=.7)y=(float)(2*bh+bh*(.7-r)/.5);else y=(float)(bh+bh*(1.3-r)/.6);
            y=Math.max(dp(5),Math.min(h-dp(5),y));float mx=w*.86f;
            p.setColor(Color.WHITE);p.setStrokeWidth(dp(2));c.drawLine(mx,0,mx,h,p);c.drawCircle(mx,y,dp(6),p);p.setColor(accent);c.drawCircle(mx,y,dp(3.2f),p);
            p.setTypeface(null);
        }
    }
    static int blendC(int a,int b,float f){return Color.rgb((int)(Color.red(a)+(Color.red(b)-Color.red(a))*f),(int)(Color.green(a)+(Color.green(b)-Color.green(a))*f),(int)(Color.blue(a)+(Color.blue(b)-Color.blue(a))*f));}

    /** A ring split into coloured parts, with a label in the middle. */
    static final class Donut extends Base {
        private final double[] v;private final int[] cs;private final String center,sub;
        Donut(Context c,KadammTheme t,double[] values,int[] colors,String center,String sub){super(c,t);v=values;cs=colors;this.center=center;this.sub=sub;}
        @Override protected void onDraw(Canvas c){
            float s=Math.min(getWidth(),getHeight()),st=dp(14),cx=getWidth()/2f,cy=getHeight()/2f;RectF o=new RectF(cx-s/2+st/2,cy-s/2+st/2,cx+s/2-st/2,cy+s/2-st/2);
            double total=0;for(double x:v)total+=x;p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(st);p.setStrokeCap(Paint.Cap.BUTT);
            if(total<=0){p.setColor(t.surfaceRaised);c.drawOval(o,p);}
            float a=-90;for(int i=0;i<v.length;i++){if(v[i]<=0)continue;float sw=(float)(v[i]/total*360);p.setColor(cs[i]);c.drawArc(o,a+.8f,Math.max(.5f,sw-1.6f),false,p);a+=sw;}
            p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setColor(t.text);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextSize(dp(18));c.drawText(center,cx,cy+(sub==null?dp(6):dp(2)),p);
            if(sub!=null){p.setTypeface(null);p.setTextSize(dp(10));p.setColor(t.textMuted);c.drawText(sub,cx,cy+dp(16),p);}
            p.setTextAlign(Paint.Align.LEFT);p.setTypeface(null);
        }
    }

    /** A slow-to-fast colour scale with a marker, like a speedometer laid flat. */
    static final class Scale extends Base {
        private final float at;private final String lo,hi;private final int[] cs;
        Scale(Context c,KadammTheme t,float fraction,String lo,String hi){this(c,t,fraction,lo,hi,false);}
        /** {@code higherIsBetter}: red at the low end and green at the high end, for health markers. */
        Scale(Context c,KadammTheme t,float fraction,String lo,String hi,boolean higherIsBetter){super(c,t);at=Math.max(0,Math.min(1,fraction));this.lo=lo;this.hi=hi;cs=higherIsBetter?new int[]{ZONE_COLORS[4],ZONE_COLORS[3],ZONE_COLORS[2]}:new int[]{ZONE_COLORS[2],ZONE_COLORS[3],ZONE_COLORS[4]};}
        @Override protected void onDraw(Canvas c){
            float w=getWidth(),bh=dp(6),y=dp(14);p.setColor(Color.BLACK);
            p.setShader(new LinearGradient(0,0,w,0,cs,null,Shader.TileMode.CLAMP));c.drawRoundRect(new RectF(0,y,w,y+bh),bh/2,bh/2,p);p.setShader(null);
            float mx=Math.max(dp(6),Math.min(w-dp(6),w*at));p.setColor(t.text);Path tri=new Path();tri.moveTo(mx-dp(6),y-dp(9));tri.lineTo(mx+dp(6),y-dp(9));tri.lineTo(mx,y-dp(1));tri.close();c.drawPath(tri,p);
            p.setTextSize(dp(10));p.setColor(cs[0]);c.drawText(lo,0,y+bh+dp(14),p);p.setTextAlign(Paint.Align.RIGHT);p.setColor(cs[2]);c.drawText(hi,w,y+bh+dp(14),p);p.setTextAlign(Paint.Align.LEFT);
        }
    }

    /** Progress ring with a big number in the middle (intensity minutes against the WHO 150). */
    static final class Ring extends Base {
        private final float f;private final int color;private final String big,small;
        Ring(Context c,KadammTheme t,float fraction,int color,String big,String small){super(c,t);f=Math.max(0,fraction);this.color=color;this.big=big;this.small=small;}
        @Override protected void onDraw(Canvas c){
            float s=Math.min(getWidth(),getHeight()),st=dp(10),cx=getWidth()/2f,cy=getHeight()/2f;RectF o=new RectF(cx-s/2+st,cy-s/2+st,cx+s/2-st,cy+s/2-st);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(st);p.setStrokeCap(Paint.Cap.ROUND);p.setColor(alpha(color,45));c.drawOval(o,p);
            p.setColor(color);c.drawArc(o,-90,360*Math.min(1,f),false,p);
            if(f>1){p.setColor(blendC(color,Color.WHITE,.45f));c.drawArc(o,-90,360*Math.min(1,f-1),false,p);}
            p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setColor(t.text);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextSize(s*.22f);float inner=s-4*st;if(p.measureText(big)>inner*.86f)p.setTextSize(s*.22f*inner*.86f/p.measureText(big));c.drawText(big,cx,cy+s*.06f,p);
            p.setTypeface(null);p.setTextSize(Math.max(dp(9),s*.085f));p.setColor(t.textMuted);c.drawText(small,cx,cy+s*.2f,p);p.setTextAlign(Paint.Align.LEFT);
        }
    }

    /** Two paces over distance on one scale, faster higher: a solid area (yours) in front of a light one (grade-adjusted). */
    static final class Pair extends Base {
        private final List<double[]> a,b;private final int color;
        Pair(Context c,KadammTheme t,List<double[]> mine,List<double[]> adjusted,int color){super(c,t);a=mine;b=adjusted;this.color=color;}
        @Override protected void onDraw(Canvas c){
            if(a.size()<2||b.size()<2)return;float w=getWidth(),h=getHeight(),left=dp(40),top=dp(6),bottom=dp(20),cw=w-left-dp(6),ch=h-top-bottom;
            double lo=Double.MAX_VALUE,hi=0,x1=Math.max(a.get(a.size()-1)[0],b.get(b.size()-1)[0]);
            for(List<double[]> l:java.util.Arrays.asList(a,b))for(double[] q:l){lo=Math.min(lo,q[1]);hi=Math.max(hi,q[1]);}
            double pad=(hi-lo)*.1+5;lo-=pad;hi+=pad;final double flo=lo,fhi=hi;
            p.setTextSize(dp(9.5f));p.setStrokeWidth(dp(1));
            for(int g=0;g<3;g++){double v=lo+(hi-lo)*(.15+.35*g);float y=(float)(top+ch*(v-lo)/(hi-lo));p.setColor(alpha(t.border,200));c.drawLine(left,y,left+cw,y,p);p.setColor(t.textMuted);p.setTextAlign(Paint.Align.RIGHT);c.drawText(Insights.clock(v),left-dp(5),y+dp(3.5f),p);}
            for(int k=0;k<2;k++){List<double[]> l=k==0?b:a;Path area=new Path();area.moveTo(left,top+ch);
                for(double[] q:l)area.lineTo((float)(left+cw*q[0]/x1),(float)(top+ch*(q[1]-flo)/(fhi-flo)));
                area.lineTo((float)(left+cw*l.get(l.size()-1)[0]/x1),top+ch);area.close();p.setColor(k==0?alpha(color,90):alpha(color,230));p.setStyle(Paint.Style.FILL);c.drawPath(area,p);}
            p.setColor(t.textMuted);p.setTextAlign(Paint.Align.LEFT);c.drawText("0 km",left,h-dp(5),p);p.setTextAlign(Paint.Align.RIGHT);c.drawText(String.format(Locale.US,"%.1f km",x1),left+cw,h-dp(5),p);p.setTextAlign(Paint.Align.LEFT);
        }
    }

    /** A 0–100 % bar with ticks, overflowing past 100 % in a deeper shade (the daily plan). */
    static final class PlanBar extends Base {
        private final float f;private final int color;
        PlanBar(Context c,KadammTheme t,float fraction,int color){super(c,t);f=Math.max(0,fraction);this.color=color;}
        @Override protected void onDraw(Canvas c){
            float w=getWidth(),bh=dp(8),y=dp(4),full=w*(f>1?1/f:1);
            p.setColor(t.surfaceRaised);c.drawRoundRect(new RectF(0,y,w,y+bh),bh/2,bh/2,p);
            p.setColor(color);c.drawRoundRect(new RectF(0,y,Math.max(bh,w*Math.min(1,f)*(f>1?1/f:1)),y+bh),bh/2,bh/2,p);
            if(f>1){p.setColor(blendC(color,Color.rgb(150,60,0),.35f));c.drawRoundRect(new RectF(full,y,w,y+bh),bh/2,bh/2,p);p.setColor(t.background);c.drawRect(full-dp(1),y,full+dp(1),y+bh,p);}
            p.setTextSize(dp(10));p.setColor(t.textMuted);c.drawText("0%",0,y+bh+dp(14),p);p.setTextAlign(Paint.Align.RIGHT);c.drawText("100%",full,y+bh+dp(14),p);p.setTextAlign(Paint.Align.LEFT);
        }
    }
    /** Seven day bars on a 0–100 scale; today outlined, days to come (-1) as empty tracks. */
    static final class WeekBars extends Base {
        private final double[] v;private final String[] labels;private final int today,color;
        WeekBars(Context c,KadammTheme t,double[] values,String[] labels,int today,int color){super(c,t);v=values;this.labels=labels;this.today=today;this.color=color;}
        @Override protected void onDraw(Canvas c){
            float w=getWidth(),h=getHeight(),bottom=h-dp(16),top=dp(4),slot=w/v.length,bw=Math.min(dp(22),slot*.5f);
            p.setTextSize(dp(10));p.setTextAlign(Paint.Align.CENTER);
            for(int i=0;i<v.length;i++){float cx=slot*i+slot/2;p.setColor(t.surfaceRaised);c.drawRoundRect(new RectF(cx-bw/2,top,cx+bw/2,bottom),dp(4),dp(4),p);
                if(v[i]>0){float y=(float)(bottom-(bottom-top)*Math.min(1,v[i]/100));p.setColor(i==today?color:alpha(color,170));c.drawRoundRect(new RectF(cx-bw/2,y,cx+bw/2,bottom),dp(4),dp(4),p);}
                p.setColor(i==today?t.text:t.textMuted);c.drawText(labels[i],cx,h-dp(3),p);}
            p.setTextAlign(Paint.Align.LEFT);
        }
    }
    /** Steps by hour of the day as 24 small bars, with 6 am, noon and 6 pm marked. */
    static final class Hours extends Base {
        private final double[] v;private final int color;
        Hours(Context c,KadammTheme t,double[] hours,int color){super(c,t);v=hours;this.color=color;}
        @Override protected void onDraw(Canvas c){
            float w=getWidth(),h=getHeight(),bottom=h-dp(14),slot=w/24f;double mx=1;for(double x:v)mx=Math.max(mx,x);
            for(int i=0;i<24;i++){float bh=(float)((bottom-dp(2))*v[i]/mx);p.setColor(v[i]>0?alpha(color,v[i]>=mx*.5?255:150):t.surfaceRaised);c.drawRoundRect(new RectF(slot*i+dp(1),bottom-Math.max(dp(2),bh),slot*(i+1)-dp(1),bottom),dp(2),dp(2),p);}
            p.setTextSize(dp(9.5f));p.setColor(t.textMuted);p.setTextAlign(Paint.Align.CENTER);String[] l={"12a","6a","12p","6p"};for(int i=0;i<4;i++)c.drawText(l[i],slot*(i*6)+slot/2,h-dp(2),p);p.setTextAlign(Paint.Align.LEFT);
        }
    }
    /** Weekday × hour grid of average steps: the hours you actually move. */
    static final class Heatmap extends Base {
        private final double[][] v;private final int color;
        Heatmap(Context c,KadammTheme t,double[][] grid,int color){super(c,t);v=grid;this.color=color;}
        @Override protected void onDraw(Canvas c){
            float left=dp(16),w=getWidth()-left,h=getHeight()-dp(14),cw=w/24f,ch=h/7f;double mx=1;for(double[] r:v)for(double x:r)mx=Math.max(mx,x);
            String[] d={"M","T","W","T","F","S","S"};p.setTextSize(dp(9.5f));
            for(int r=0;r<7;r++){p.setColor(t.textMuted);c.drawText(d[r],0,ch*r+ch/2+dp(3.5f),p);
                for(int k=0;k<24;k++){double f=v[r][k]/mx;p.setColor(f<=0?t.surfaceRaised:alpha(color,(int)(40+215*Math.sqrt(f))));c.drawRoundRect(new RectF(left+cw*k+dp(.8f),ch*r+dp(.8f),left+cw*(k+1)-dp(.8f),ch*(r+1)-dp(.8f)),dp(2),dp(2),p);}}
            p.setColor(t.textMuted);p.setTextAlign(Paint.Align.CENTER);String[] l={"12a","6a","12p","6p"};for(int i=0;i<4;i++)c.drawText(l[i],left+cw*(i*6)+cw/2,getHeight()-dp(2),p);p.setTextAlign(Paint.Align.LEFT);
        }
    }

    /** A two-tone flame with the streak count in it; grey when the streak is zero. */
    static final class Flame extends Base {
        private final int days;
        Flame(Context c,KadammTheme t,int days){super(c,t);this.days=days;}
        @Override protected void onDraw(Canvas c){
            float w=getWidth(),h=getHeight();Path o=new Path();o.moveTo(w*.5f,0);o.cubicTo(w*.7f,h*.25f,w*.98f,h*.38f,w*.92f,h*.66f);o.cubicTo(w*.86f,h*.98f,w*.14f,h*.98f,w*.08f,h*.66f);o.cubicTo(w*.04f,h*.45f,w*.28f,h*.38f,w*.3f,h*.16f);o.cubicTo(w*.4f,h*.28f,w*.46f,h*.3f,w*.5f,0);o.close();
            boolean lit=days>0;p.setColor(Color.BLACK);p.setShader(new LinearGradient(0,0,0,h,lit?Color.rgb(255,196,0):t.surfaceRaised,lit?Color.rgb(244,81,30):t.border,Shader.TileMode.CLAMP));c.drawPath(o,p);p.setShader(null);
            Path in=new Path();in.moveTo(w*.5f,h*.36f);in.cubicTo(w*.62f,h*.52f,w*.76f,h*.6f,w*.72f,h*.76f);in.cubicTo(w*.68f,h*.93f,w*.32f,h*.93f,w*.28f,h*.76f);in.cubicTo(w*.26f,h*.62f,w*.42f,h*.56f,w*.5f,h*.36f);in.close();
            p.setColor(lit?Color.rgb(255,236,170):t.surface);c.drawPath(in,p);
            p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextSize(h*.22f);p.setColor(lit?Color.rgb(160,50,0):t.textMuted);c.drawText(String.valueOf(days),w*.5f,h*.84f,p);p.setTypeface(null);p.setTextAlign(Paint.Align.LEFT);
        }
    }

    /**
     * Today's steps as a dot matrix: one column per hour, one dot per step unit (chosen so the busiest hour is
     * at most 12 dots tall). Dots warm from the accent to amber as an hour gets busier; the peak hour carries a
     * label, the current hour a ring underneath. Touch a column to read that hour.
     */
    static final class DotDay extends Base {
        private final double[] h;private final int nowHour,accent;private int picked=-1;
        DotDay(Context c,KadammTheme t,double[] hours,int nowHour,int accent){super(c,t);h=hours;this.nowHour=nowHour;this.accent=accent;setContentDescription("Steps by hour today. Touch a column to read it.");}
        int unit(){double mx=0;for(double v:h)mx=Math.max(mx,v);int u=50;while(mx/u>12)u+=50;return u;}
        private float downX,downY;private boolean reading;
        /** A tap reads one hour; a sideways drag scrubs. A vertical drag is left to the page so it still scrolls. */
        @Override public boolean onTouchEvent(android.view.MotionEvent e){
            float slot=(getWidth()-dp(4))/24f;int k=(int)Math.max(0,Math.min(23,(e.getX()-dp(2))/slot));int a=e.getActionMasked();
            if(a==android.view.MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();reading=false;return true;}
            if(a==android.view.MotionEvent.ACTION_MOVE){float dx=Math.abs(e.getX()-downX),dy=Math.abs(e.getY()-downY);
                if(!reading&&dy>dp(8)&&dy>dx)return false;
                if(!reading&&dx>dp(8)){reading=true;RouteView.gestureActive=true;if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);}}
            if(reading||a==android.view.MotionEvent.ACTION_UP){if(k!=picked){picked=k;performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);invalidate();}}
            if(a==android.view.MotionEvent.ACTION_UP||a==android.view.MotionEvent.ACTION_CANCEL){reading=false;RouteView.gestureActive=false;}
            return true;
        }
        @Override protected void onDraw(Canvas c){
            float w=getWidth(),hgt=getHeight(),top=dp(30),bottom=hgt-dp(18),slot=(w-dp(4))/24f,r=Math.min(slot*.32f,dp(3.6f)),gap=r*2.6f;int u=unit();
            int peak=0;for(int i=1;i<24;i++)if(h[i]>h[peak])peak=i;
            int rows=(int)Math.floor((bottom-top)/gap);
            for(int i=0;i<24;i++){
                float cx=dp(2)+slot*i+slot/2;int dots=(int)Math.round(h[i]/u);if(h[i]>0&&dots==0)dots=1;dots=Math.min(dots,rows);
                boolean future=i>nowHour;
                p.setColor(future?alpha(t.border,120):alpha(t.textMuted,50));c.drawCircle(cx,bottom-r,r*.55f,p);
                for(int d=0;d<dots;d++){float f=(float)d/Math.max(1,rows-1);int col=blendC(accent,ZONE_COLORS[3],Math.min(1,f*1.4f));p.setColor(i==picked||picked<0?col:alpha(col,110));c.drawCircle(cx,bottom-r-d*gap,r,p);}
                if(i==nowHour){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1.2f));p.setColor(t.text);c.drawCircle(cx,bottom-r,r+dp(2.2f),p);p.setStyle(Paint.Style.FILL);}
            }
            p.setTextSize(dp(9.5f));p.setColor(t.textMuted);p.setTextAlign(Paint.Align.CENTER);String[] l={"12a","6a","12p","6p","12a"};for(int i=0;i<5;i++)c.drawText(l[i],dp(2)+slot*(i*6)+(i==4?-slot/2:slot/2),hgt-dp(3),p);
            int show=picked>=0?picked:h[peak]>0?peak:-1;
            if(show>=0){String txt=(picked>=0?"":"Peak ")+hourName(show)+" · "+String.format(Locale.US,"%,d",Math.round(h[show]))+" steps";
                p.setTextSize(dp(11));p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));float tw=p.measureText(txt)+dp(16);float cx=dp(2)+slot*show+slot/2,bx=Math.max(0,Math.min(w-tw,cx-tw/2));
                p.setColor(picked>=0?t.text:ZONE_COLORS[3]);c.drawRoundRect(new RectF(bx,dp(2),bx+tw,dp(24)),dp(12),dp(12),p);p.setColor(picked>=0?t.background:Color.rgb(60,36,0));p.setTextAlign(Paint.Align.LEFT);c.drawText(txt,bx+dp(8),dp(17),p);p.setTypeface(null);}
            p.setTextAlign(Paint.Align.LEFT);
        }
        static String hourName(int hr){int a=hr%12==0?12:hr%12,b=(hr+1)%12==0?12:(hr+1)%12;return a+"–"+b+((hr+1)%24<12?" am":" pm");}
    }

    /**
     * Kadamm's gradient view: the walk drawn as one hill. The climb rises on the left, the flat stretch is the
     * plateau and the descent falls on the right, each as wide as its share of time, with % and time under it.
     */
    static final class Terrain extends Base {
        private final long up,flat,down;
        Terrain(Context c,KadammTheme t,long up,long flat,long down){super(c,t);this.up=up;this.flat=flat;this.down=down;}
        @Override protected void onDraw(Canvas c){
            float w=getWidth(),h=getHeight(),base=h-dp(46),top=dp(10);double tot=Math.max(1,up+flat+down);
            float wu=(float)(w*up/tot),wf=(float)(w*flat/tot),wd=w-wu-wf,mid=top+dp(14);
            int[] cs={ZONE_COLORS[4],ZONE_COLORS[3],ZONE_COLORS[1]};
            p.setColor(alpha(t.border,255));c.drawRect(0,base,w,base+dp(2),p);
            Path u=new Path();u.moveTo(0,base);u.cubicTo(wu*.5f,base,wu*.6f,mid,wu,mid);u.lineTo(wu,base);u.close();
            Path f=new Path();f.moveTo(wu,base);f.lineTo(wu,mid);f.lineTo(wu+wf,mid);f.lineTo(wu+wf,base);f.close();
            Path d=new Path();d.moveTo(wu+wf,base);d.lineTo(wu+wf,mid);d.cubicTo(wu+wf+wd*.4f,mid,wu+wf+wd*.5f,base,w,base);d.close();
            Path[] parts={u,f,d};
            for(int i=0;i<3;i++){p.setColor(Color.BLACK);p.setShader(new LinearGradient(0,mid,0,base,cs[i],alpha(cs[i],t.dark?60:40),Shader.TileMode.CLAMP));c.drawPath(parts[i],p);p.setShader(null);}
            Path ridge=new Path();ridge.moveTo(0,base);ridge.cubicTo(wu*.5f,base,wu*.6f,mid,wu,mid);ridge.lineTo(wu+wf,mid);ridge.cubicTo(wu+wf+wd*.4f,mid,wu+wf+wd*.5f,base,w,base);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2.5f));p.setStrokeCap(Paint.Cap.ROUND);p.setColor(t.text);c.drawPath(ridge,p);p.setStyle(Paint.Style.FILL);
            String[] names={"↗ Uphill","→ Flat","↘ Downhill"};long[] v={up,flat,down};float[] cx={wu/2,wu+wf/2,wu+wf+wd/2};
            cx[0]=Math.max(dp(36),Math.min(cx[0],cx[1]-dp(76)));cx[2]=Math.min(w-dp(40),Math.max(cx[2],cx[1]+dp(76)));
            for(int i=0;i<3;i++){p.setTextAlign(Paint.Align.CENTER);p.setTextSize(dp(11));p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setColor(cs[i]);c.drawText(names[i]+"  "+Math.round(v[i]*100/tot)+"%",cx[i],base+dp(20),p);
                p.setTypeface(null);p.setTextSize(dp(10));p.setColor(t.textMuted);c.drawText(mmss(v[i]),cx[i],base+dp(36),p);}
            p.setTextAlign(Paint.Align.LEFT);
        }
    }

    /**
     * The Home goal rings: steps (outer, blue), brisk minutes (middle, green) and distance (inner, amber), each
     * against its daily goal. A ring past its goal keeps going round in a deeper shade. Values glide to new
     * readings instead of jumping, and the steps count sits in the middle.
     */
    static final class Rings extends Base {
        static final int[] COLORS={Color.rgb(77,144,254),Color.rgb(52,168,83),Color.rgb(251,168,4)};
        private final float[] shown=new float[3],target=new float[3];private String big="—",small="steps";private android.animation.ValueAnimator anim;
        Rings(Context c,KadammTheme t){super(c,t);}
        void set(float steps,float brisk,float dist,String bigText,String smallText){
            big=bigText;small=smallText;target[0]=Math.max(0,steps);target[1]=Math.max(0,brisk);target[2]=Math.max(0,dist);
            if(Math.abs(target[0]-shown[0])+Math.abs(target[1]-shown[1])+Math.abs(target[2]-shown[2])<.002f){invalidate();return;}
            float[] from=shown.clone();if(anim!=null)anim.cancel();anim=android.animation.ValueAnimator.ofFloat(0,1);anim.setDuration(900);anim.setInterpolator(new android.view.animation.DecelerateInterpolator(2f));
            anim.addUpdateListener(a->{float f=(float)a.getAnimatedValue();for(int i=0;i<3;i++)shown[i]=from[i]+(target[i]-from[i])*f;invalidate();});anim.start();
        }
        @Override protected void onDetachedFromWindow(){if(anim!=null)anim.cancel();super.onDetachedFromWindow();}
        @Override protected void onDraw(Canvas c){
            float s=Math.min(getWidth(),getHeight()),cx=getWidth()/2f,cy=getHeight()/2f,st=s*.098f,gap=st*1.16f;
            p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(st);
            for(int i=0;i<3;i++){
                float r=s/2-st/2-i*gap;RectF o=new RectF(cx-r,cy-r,cx+r,cy+r);int col=COLORS[i],light=blendC(col,Color.WHITE,.32f),deep=blendC(col,Color.BLACK,.18f);
                p.setShader(null);p.setColor(alpha(col,t.dark?48:38));c.drawOval(o,p);
                float v=shown[i];if(v<=0.001f)continue;
                // The ring shades from light at the start to deep at its leading end, like the ring is filling up.
                c.save();c.rotate(-90,cx,cy);
                float sweep=360*Math.min(1,v);
                android.graphics.SweepGradient g=new android.graphics.SweepGradient(cx,cy,new int[]{light,col,deep,deep},new float[]{0,Math.max(.01f,sweep/360f*.55f),Math.max(.02f,sweep/360f),1});
                p.setColor(Color.BLACK);p.setShader(g);c.drawArc(o,0,sweep,false,p);p.setShader(null);
                if(v>1){
                    // Past the goal: a second lap in the deep shade, with a soft shadow ahead of its end.
                    float lap=360*Math.min(.999f,v-1),ea=(float)Math.toRadians(lap);float ex=cx+(float)(r*Math.cos(ea)),ey=cy+(float)(r*Math.sin(ea));
                    p.setStyle(Paint.Style.FILL);p.setColor(Color.argb(t.dark?110:70,0,0,0));c.drawCircle(ex+st*.18f*(float)Math.cos(ea+Math.PI/2),ey+st*.18f*(float)Math.sin(ea+Math.PI/2),st*.52f,p);p.setStyle(Paint.Style.STROKE);
                    p.setColor(deep);c.drawArc(o,0,lap,false,p);
                }
                c.restore();
            }
            p.setShader(null);p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setColor(t.text);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextSize(s*.105f);c.drawText(big,cx,cy+s*.025f,p);
            p.setTypeface(null);p.setTextSize(s*.06f);p.setColor(t.textMuted);c.drawText(small,cx,cy+s*.11f,p);p.setTextAlign(Paint.Align.LEFT);
        }
    }

    /** Today 7 am–11 pm as a track: sitting stretches as blocks (amber when an hour or longer), and a marker for now. */
    static final class SitLine extends Base {
        private final java.util.List<int[]> sits;private final int nowMin;
        SitLine(Context c,KadammTheme t,java.util.List<int[]> sits,int nowMin){super(c,t);this.sits=sits;this.nowMin=nowMin;setContentDescription("Sitting stretches today from 7 am to 11 pm");}
        @Override protected void onDraw(Canvas c){
            float w=getWidth(),top=dp(6),bh=dp(18),start=420,span=960;
            p.setColor(alpha(ZONE_COLORS[2],t.dark?70:55));c.drawRoundRect(new RectF(0,top,w,top+bh),bh/2,bh/2,p);
            for(int[] s:sits){float x1=w*(s[0]-start)/span,x2=w*Math.min(span,s[0]+s[1]-start)/span;p.setColor(alpha(s[1]>=60?ZONE_COLORS[3]:t.textMuted,s.length>2?150:(s[1]>=60?255:170)));c.drawRoundRect(new RectF(Math.max(0,x1),top,Math.min(w,Math.max(x1+dp(4),x2)),top+bh),dp(5),dp(5),p);}
            if(nowMin>start&&nowMin<start+span){float x=w*(nowMin-start)/span;p.setColor(t.text);c.drawRoundRect(new RectF(x-dp(1.2f),top-dp(4),x+dp(1.2f),top+bh+dp(4)),dp(1),dp(1),p);}
            p.setTextSize(dp(9.5f));p.setColor(t.textMuted);String[] l={"7a","11a","3p","7p","11p"};
            for(int i=0;i<5;i++){p.setTextAlign(i==0?Paint.Align.LEFT:i==4?Paint.Align.RIGHT:Paint.Align.CENTER);c.drawText(l[i],w*i/4f,top+bh+dp(16),p);}p.setTextAlign(Paint.Align.LEFT);
        }
    }

    /** Fitness: the last four weeks as a solid line, then four weeks ahead with the plan (teal, dashed) and without (coral, dotted). */
    static final class Forecast extends Base {
        private final double[] hist,plan,stop;
        Forecast(Context c,KadammTheme t,double[] hist,double[] plan,double[] stop){super(c,t);this.hist=hist;this.plan=plan;this.stop=stop;setContentDescription("Fitness forecast for the next four weeks");}
        @Override protected void onDraw(Canvas c){
            int n=hist.length+plan.length;if(n<2)return;float w=getWidth(),h=getHeight(),left=dp(28),top=dp(10),bottom=h-dp(18),cw=w-left-dp(4);
            double lo=Double.MAX_VALUE,hi=0;for(double[] a:new double[][]{hist,plan,stop})for(double v:a){lo=Math.min(lo,v);hi=Math.max(hi,v);}
            double pad=Math.max(1,(hi-lo)*.15);lo=Math.max(0,lo-pad);hi+=pad;final double flo=lo,fhi=hi;
            java.util.function.IntToDoubleFunction xs=i->left+cw*i/(double)(n-1);java.util.function.DoubleUnaryOperator ys=v->top+(bottom-top)*(1-(v-flo)/(fhi-flo));
            p.setTextSize(dp(9.5f));p.setColor(t.textMuted);p.setTextAlign(Paint.Align.RIGHT);c.drawText(String.valueOf(Math.round(hi)),left-dp(4),top+dp(4),p);c.drawText(String.valueOf(Math.round(lo)),left-dp(4),bottom,p);
            float nowX=(float)xs.applyAsDouble(hist.length-1);p.setColor(alpha(t.border,255));p.setStrokeWidth(dp(1));c.drawLine(nowX,top,nowX,bottom,p);
            p.setTextAlign(Paint.Align.CENTER);p.setColor(t.textMuted);c.drawText("now",nowX,h-dp(4),p);p.setTextAlign(Paint.Align.LEFT);c.drawText("4 weeks ago",left,h-dp(4),p);p.setTextAlign(Paint.Align.RIGHT);c.drawText("in 4 weeks",w-dp(2),h-dp(4),p);p.setTextAlign(Paint.Align.LEFT);
            p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(dp(2.5f));
            Path ph=new Path();for(int i=0;i<hist.length;i++){float x=(float)xs.applyAsDouble(i),y=(float)ys.applyAsDouble(hist[i]);if(i==0)ph.moveTo(x,y);else ph.lineTo(x,y);}p.setColor(t.text);c.drawPath(ph,p);
            double startV=hist.length>0?hist[hist.length-1]:plan[0];
            for(int k=0;k<2;k++){double[] a=k==0?plan:stop;Path pp=new Path();pp.moveTo(nowX,(float)ys.applyAsDouble(startV));for(int i=0;i<a.length;i++)pp.lineTo((float)xs.applyAsDouble(hist.length+i),(float)ys.applyAsDouble(a[i]));
                p.setColor(k==0?GoalCapsule.COLORS[0]:GoalCapsule.COLORS[1]);p.setPathEffect(new DashPathEffect(k==0?new float[]{dp(6),dp(4)}:new float[]{dp(2),dp(4)},0));c.drawPath(pp,p);p.setPathEffect(null);}
            p.setStyle(Paint.Style.FILL);
        }
    }

    /**
     * Pace consistency: one bar per 250 m, its height the speed of that stretch against your median (the line).
     * Blue bars are surges (12 %+ faster), amber ones slow stretches (12 %+ slower, often a stop), green the rest.
     */
    static final class PaceBars extends Base {
        private final double[] v;private final double med;
        PaceBars(Context c,KadammTheme t,java.util.List<Double> pace){super(c,t);v=new double[pace.size()];for(int i=0;i<v.length;i++)v[i]=pace.get(i);double[] s=v.clone();java.util.Arrays.sort(s);med=s.length==0?1:s[s.length/2];setContentDescription("Pace of each 250 metres against your median");}
        @Override protected void onDraw(Canvas c){
            if(v.length==0)return;float w=getWidth(),h=getHeight(),bottom=h-dp(14),top=dp(4),slot=w/v.length,bw=Math.max(dp(2),Math.min(dp(14),slot*.7f));
            double hi=0;for(double x:v)hi=Math.max(hi,med/x);hi=Math.max(hi,1.3);double lo=.5;
            float midY=(float)(bottom-(bottom-top)*(1-lo)/(hi-lo));
            for(int i=0;i<v.length;i++){double sp=med/v[i];float y=(float)(bottom-(bottom-top)*Math.max(0,(sp-lo))/(hi-lo));float cx=slot*i+slot/2;
                p.setColor(sp>=1/.88?ZONE_COLORS[1]:sp<=.88?ZONE_COLORS[3]:ZONE_COLORS[2]);c.drawRoundRect(new RectF(cx-bw/2,Math.min(y,bottom-dp(2)),cx+bw/2,bottom),dp(2),dp(2),p);}
            p.setColor(t.text);p.setStrokeWidth(dp(1));p.setPathEffect(new DashPathEffect(new float[]{dp(4),dp(3)},0));c.drawLine(0,midY,w,midY,p);p.setPathEffect(null);
            p.setTextSize(dp(9.5f));p.setColor(t.textMuted);p.setTextAlign(Paint.Align.LEFT);c.drawText("start",0,h-dp(2),p);p.setTextAlign(Paint.Align.RIGHT);c.drawText(String.format(Locale.US,"%.2f km",v.length*.25),w,h-dp(2),p);p.setTextAlign(Paint.Align.LEFT);
        }
    }
    /**
     * The month so far and where it's heading: cumulative steps (solid), the forecast (dashed) with its likely range
     * (shaded), and the month's goal (a dotted line).
     */
    static final class MonthPath extends Base {
        private final double[][] d;private final double target;private final int color;
        MonthPath(Context c,KadammTheme t,double[][] path,double target,int color){super(c,t);d=path;this.target=target;this.color=color;setContentDescription("Steps this month and the forecast to the end of the month");}
        @Override protected void onDraw(Canvas c){
            int n=d.length;if(n<2)return;float w=getWidth(),h=getHeight(),left=dp(34),top=dp(8),bottom=h-dp(16),cw=w-left-dp(6);
            double hi=target;for(double[] q:d)hi=Math.max(hi,q[3]);hi*=1.05;final double fhi=hi;
            java.util.function.IntToDoubleFunction xs=i->left+cw*i/(double)(n-1);java.util.function.DoubleUnaryOperator ys=v->top+(bottom-top)*(1-v/fhi);
            p.setTextSize(dp(9.5f));p.setColor(t.textMuted);p.setTextAlign(Paint.Align.RIGHT);
            for(int g=0;g<=2;g++){double v=hi*g/2;float y=(float)ys.applyAsDouble(v);p.setColor(alpha(t.border,200));p.setStrokeWidth(dp(1));c.drawLine(left,y,left+cw,y,p);p.setColor(t.textMuted);c.drawText(v>=1000?Math.round(v/1000)+"k":String.valueOf(Math.round(v)),left-dp(4),y+dp(3.5f),p);}
            p.setTextAlign(Paint.Align.LEFT);c.drawText("1",left,h-dp(3),p);p.setTextAlign(Paint.Align.RIGHT);c.drawText(String.valueOf(n),left+cw,h-dp(3),p);p.setTextAlign(Paint.Align.LEFT);
            int last=-1;for(int i=0;i<n;i++)if(d[i][0]>=0)last=i;
            // Range
            Path band=new Path();boolean st=false;for(int i=Math.max(0,last);i<n;i++){float x=(float)xs.applyAsDouble(i),y=(float)ys.applyAsDouble(d[i][3]);if(!st){band.moveTo(x,y);st=true;}else band.lineTo(x,y);}
            for(int i=n-1;i>=Math.max(0,last);i--)band.lineTo((float)xs.applyAsDouble(i),(float)ys.applyAsDouble(d[i][2]));band.close();p.setStyle(Paint.Style.FILL);p.setColor(alpha(color,t.dark?60:45));c.drawPath(band,p);
            // Goal
            float gy=(float)ys.applyAsDouble(target);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1.5f));p.setColor(ZONE_COLORS[3]);p.setPathEffect(new DashPathEffect(new float[]{dp(2),dp(4)},0));c.drawLine(left,gy,left+cw,gy,p);p.setPathEffect(null);
            // Forecast
            Path f=new Path();for(int i=Math.max(0,last);i<n;i++){float x=(float)xs.applyAsDouble(i),y=(float)ys.applyAsDouble(d[i][1]);if(i==Math.max(0,last))f.moveTo(x,y);else f.lineTo(x,y);}
            p.setStrokeWidth(dp(2.5f));p.setStrokeCap(Paint.Cap.ROUND);p.setColor(color);p.setPathEffect(new DashPathEffect(new float[]{dp(6),dp(4)},0));c.drawPath(f,p);p.setPathEffect(null);
            // Actual
            Path a=new Path();for(int i=0;i<=last;i++){float x=(float)xs.applyAsDouble(i),y=(float)ys.applyAsDouble(d[i][0]);if(i==0)a.moveTo(x,y);else a.lineTo(x,y);}
            p.setColor(t.text);c.drawPath(a,p);p.setStyle(Paint.Style.FILL);
            if(last>=0){p.setColor(color);c.drawCircle((float)xs.applyAsDouble(last),(float)ys.applyAsDouble(d[last][0]),dp(4),p);}
            p.setColor(ZONE_COLORS[3]);p.setTextAlign(Paint.Align.LEFT);c.drawText("goal",left+dp(4),gy-dp(4),p);
        }
    }

    /**
     * A night's sleep stages over time (hypnogram): awake at the top, then REM, light and deep at the bottom, each a
     * coloured block. Stages are {minutes from bedtime, minutes, Health Connect stage type}.
     */
    static final class Hypno extends Base {
        private final org.json.JSONArray st;private final int total;private final long start;
        Hypno(Context c,KadammTheme t,org.json.JSONArray stages,int totalMin,long startMs){super(c,t);st=stages;total=Math.max(1,totalMin);start=startMs;setContentDescription("Sleep stages through the night");}
        static int row(int stage){switch(stage){case 1:case 3:case 7:return 0;case 6:return 1;case 5:return 3;default:return 2;}}
        static final int[] COLORS={Color.rgb(242,139,130),Color.rgb(130,177,255),Color.rgb(66,133,244),Color.rgb(98,42,190)};
        @Override protected void onDraw(Canvas c){
            float w=getWidth(),h=getHeight(),left=dp(44),bottom=h-dp(16),rowH=(bottom-dp(4))/4f;String[] names={"Awake","REM","Light","Deep"};
            p.setTextSize(dp(10));p.setTextAlign(Paint.Align.RIGHT);
            for(int r=0;r<4;r++){float y=dp(4)+rowH*r;p.setColor(t.textMuted);c.drawText(names[r],left-dp(6),y+rowH/2+dp(3.5f),p);p.setColor(alpha(t.border,160));c.drawLine(left,y+rowH,w,y+rowH,p);}
            for(int i=0;i<st.length();i++){org.json.JSONArray s=st.optJSONArray(i);if(s==null)continue;int r=row(s.optInt(2));float x0=left+(w-left)*s.optInt(0)/(float)total,x1=left+(w-left)*(s.optInt(0)+s.optInt(1))/(float)total;float y=dp(4)+rowH*r;
                p.setColor(COLORS[r]);c.drawRoundRect(new RectF(x0,y+dp(3),Math.max(x0+dp(1.5f),x1),y+rowH-dp(3)),dp(2),dp(2),p);}
            p.setColor(t.textMuted);p.setTextAlign(Paint.Align.LEFT);java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("h:mm a",Locale.getDefault());
            c.drawText(f.format(new java.util.Date(start)),left,h-dp(3),p);p.setTextAlign(Paint.Align.RIGHT);c.drawText(f.format(new java.util.Date(start+total*60000L)),w,h-dp(3),p);p.setTextAlign(Paint.Align.LEFT);
        }
    }

    /** Tier colours for badges: bronze (easy), silver (medium), gold (hard), amethyst (extreme). */
    static final int[][] TIER={{Color.rgb(214,140,86),Color.rgb(140,78,40)},{Color.rgb(214,222,232),Color.rgb(120,132,148)},{Color.rgb(255,214,92),Color.rgb(196,128,8)},{Color.rgb(196,140,255),Color.rgb(98,42,190)}};
    static final String[] TIER_NAMES={"Easy","Medium","Hard","Extreme"};

    /** A badge as a medal: a tier-coloured hexagon with the icon, or a grey one with a progress arc while locked. */
    static final class Medal extends Base {
        private final int tier,icon;private final boolean earned;private final float progress;
        Medal(Context c,KadammTheme t,int tier,int icon,boolean earned,float progress){super(c,t);this.tier=tier;this.icon=icon;this.earned=earned;this.progress=Math.max(0,Math.min(1,progress));}
        @Override protected void onDraw(Canvas c){
            float s=Math.min(getWidth(),getHeight()),cx=getWidth()/2f,cy=getHeight()/2f-(earned?s*.04f:0),r=s*(earned?.40f:.44f);
            Path hex=new Path();for(int i=0;i<6;i++){double a=Math.toRadians(60*i-90);float x=(float)(cx+r*Math.cos(a)),y=(float)(cy+r*Math.sin(a));if(i==0)hex.moveTo(x,y);else hex.lineTo(x,y);}hex.close();
            int[] tc=TIER[tier];
            if(earned){
                // Ribbon tails behind the medal.
                p.setColor(blendC(tc[1],Color.BLACK,.15f));Path rib=new Path();rib.moveTo(cx-r*.42f,cy+r*.55f);rib.lineTo(cx-r*.62f,cy+r*1.02f);rib.lineTo(cx-r*.38f,cy+r*.9f);rib.lineTo(cx-r*.22f,cy+r*1.05f);rib.lineTo(cx-r*.05f,cy+r*.6f);rib.close();c.drawPath(rib,p);
                Path rib2=new Path();rib2.moveTo(cx+r*.42f,cy+r*.55f);rib2.lineTo(cx+r*.62f,cy+r*1.02f);rib2.lineTo(cx+r*.38f,cy+r*.9f);rib2.lineTo(cx+r*.22f,cy+r*1.05f);rib2.lineTo(cx+r*.05f,cy+r*.6f);rib2.close();c.drawPath(rib2,p);
                p.setColor(Color.BLACK);p.setShader(new LinearGradient(cx-r,cy-r,cx+r,cy+r,tc[0],tc[1],Shader.TileMode.CLAMP));c.drawPath(hex,p);p.setShader(null);
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1.5f));p.setColor(alpha(Color.WHITE,90));c.drawPath(hex,p);p.setStyle(Paint.Style.FILL);
                // Inner face and a highlight, for a struck-metal look.
                Path inner=new Path();float ri=r*.78f;for(int i=0;i<6;i++){double a=Math.toRadians(60*i-90);float x=(float)(cx+ri*Math.cos(a)),y=(float)(cy+ri*Math.sin(a));if(i==0)inner.moveTo(x,y);else inner.lineTo(x,y);}inner.close();
                p.setShader(new LinearGradient(cx,cy-ri,cx,cy+ri,tc[1],blendC(tc[1],Color.BLACK,.25f),Shader.TileMode.CLAMP));c.drawPath(inner,p);p.setShader(null);
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1.2f));p.setColor(alpha(Color.WHITE,120));c.drawPath(inner,p);p.setStyle(Paint.Style.FILL);
                if(tier==3){p.setColor(alpha(Color.WHITE,200));for(int i=0;i<3;i++){double a=Math.toRadians(-60+i*60);c.drawCircle((float)(cx+r*1.02f*Math.cos(a)),(float)(cy-r*.9f+Math.abs(i-1)*r*.1f),dp(1.6f),p);}}
            }else{
                p.setColor(t.surfaceRaised);c.drawPath(hex,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));p.setColor(t.border);c.drawPath(hex,p);
                if(progress>0){p.setStrokeWidth(dp(3));p.setStrokeCap(Paint.Cap.ROUND);p.setColor(tc[0]);c.drawArc(new RectF(cx-r*.9f,cy-r*.9f,cx+r*.9f,cy+r*.9f),-90,360*progress,false,p);}
                p.setStyle(Paint.Style.FILL);
            }
            Drawable d=getContext().getDrawable(icon);
            if(d!=null){d=d.mutate();d.setTint(earned?Color.WHITE:t.textMuted);int is=(int)(r*.82f);d.setBounds((int)(cx-is/2f),(int)(cy-is/2f),(int)(cx+is/2f),(int)(cy+is/2f));d.setAlpha(earned?255:150);d.draw(c);}
        }
    }
}
