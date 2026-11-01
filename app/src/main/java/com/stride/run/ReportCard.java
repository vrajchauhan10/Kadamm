package com.stride.run;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import java.util.Locale;

/** Shareable image of a weekly or monthly report, in the same style as the activity share card. */
final class ReportCard {
    private ReportCard() {}
    static Bitmap render(Context c,Progress.Period per,Progress.Summary s,Progress.Summary prev,int goal,boolean dark){
        int w=1080,h=1350;final int BG=dark?ShareCard.BLACK:Color.WHITE,TEXT=dark?Color.WHITE:Color.rgb(11,11,13),MUTED=dark?Color.rgb(154,160,166):Color.rgb(95,99,104),LINE=dark?Color.rgb(42,42,46):Color.rgb(227,227,230),BLUE=dark?ShareCard.ROUTE:ShareCard.BLUE;
        Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas cv=new Canvas(b);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);cv.drawColor(BG);
        Typeface med=Typeface.create("sans-serif-medium",Typeface.NORMAL);
        Drawable logo=c.getDrawable(dark?R.drawable.ic_kadamm_logo_dark:R.drawable.ic_kadamm_logo);if(logo!=null){logo.setBounds(64,64,64+88,64+88);logo.draw(cv);}
        p.setColor(TEXT);p.setTypeface(med);p.setTextSize(46);p.setLetterSpacing(.16f);cv.drawText("KADAMM",174,124,p);p.setLetterSpacing(0f);
        p.setTextSize(64);String title=per.title;p.setTextSize(64);while(p.measureText(title)>w-128&&p.getTextSize()>40)p.setTextSize(p.getTextSize()-2);cv.drawText(title,64,270,p);
        p.setTypeface(Typeface.DEFAULT);p.setColor(MUTED);p.setTextSize(36);cv.drawText(per.label,64,326,p);
        p.setColor(BLUE);p.setTypeface(med);p.setTextSize(150);String big=String.format(Locale.US,"%,d",s.steps);cv.drawText(big,60,500,p);
        p.setColor(MUTED);p.setTypeface(Typeface.DEFAULT);p.setTextSize(38);
        String change=prev.steps>0?String.format(Locale.US,"steps · %+d%% vs before",Math.round((s.steps-prev.steps)*100.0/prev.steps)):"steps";cv.drawText(change,66,560,p);
        String[][] st={{"DISTANCE",String.format(Locale.US,"%.1f km",Math.max(s.km,DaySteps.stepKm(c,s.steps)))},{"ACTIVE TIME",String.format(Locale.US,"%dh %02dm",s.activeMs/3600000,(s.activeMs/60000)%60)},{"GOAL DAYS",s.goalDays+" / "+s.elapsed},
            {"SESSIONS",String.valueOf(s.sessions)},{"DAILY AVERAGE",String.format(Locale.US,"%,d",s.steps/Math.max(1,s.elapsed))},{"BEST DAY",String.format(Locale.US,"%,d",s.bestSteps)}};
        p.setColor(LINE);p.setStrokeWidth(2);cv.drawLine(64,620,w-64,620,p);
        float colW=(w-128)/3f;
        for(int i=0;i<st.length;i++){float x=64+(i%3)*colW,y=690+(i/3)*150;p.setColor(MUTED);p.setTextSize(28);p.setLetterSpacing(.1f);cv.drawText(st[i][0],x,y,p);p.setLetterSpacing(0f);p.setColor(TEXT);p.setTypeface(med);p.setTextSize(58);cv.drawText(st[i][1],x,y+72,p);p.setTypeface(Typeface.DEFAULT);}
        // daily bars
        long[] daily=s.daily;if(daily.length>62){int wk=(daily.length+6)/7;long[] x=new long[wk];for(int i=0;i<daily.length;i++)x[i/7]+=daily[i];daily=x;}
        float top=1000,base=1220,cw=w-128;boolean weekly=daily!=s.daily;long max=weekly?goal*7L:goal;for(long v:daily)max=Math.max(max,v);int n=daily.length;float slot=cw/n;
        p.setColor(MUTED);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setPathEffect(new DashPathEffect(new float[]{12,12},0));float gy=base-(base-top)*(weekly?goal*7f:goal)/(float)max;cv.drawLine(64,gy,w-64,gy,p);p.setPathEffect(null);p.setStyle(Paint.Style.FILL);
        for(int i=0;i<n;i++){float bw=Math.min(70,slot*.62f),cx=64+slot*(i+.5f),bh=Math.max(6,(base-top)*daily[i]/(float)max);p.setColor(daily[i]>=(weekly?goal*7L:goal)?BLUE:Color.argb(110,Color.red(BLUE),Color.green(BLUE),Color.blue(BLUE)));cv.drawRoundRect(new RectF(cx-bw/2,base-bh,cx+bw/2,base),10,10,p);
            if(n<=7&&!weekly){p.setColor(MUTED);p.setTextSize(30);p.setTextAlign(Paint.Align.CENTER);cv.drawText(s.labels[i],cx,base+44,p);p.setTextAlign(Paint.Align.LEFT);}}
        p.setColor(MUTED);p.setTextSize(30);cv.drawText("Tracked with Kadamm  ·  Every step counts",64,h-50,p);
        return b;
    }
}
