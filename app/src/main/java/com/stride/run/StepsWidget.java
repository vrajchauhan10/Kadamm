package com.stride.run;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.widget.RemoteViews;

import java.util.Locale;

/** Home-screen widget: the dashboard's step and move-minute rings, drawn to a bitmap. */
public class StepsWidget extends AppWidgetProvider {
    private static long lastRefresh=0;

    @Override public void onUpdate(Context c,AppWidgetManager manager,int[] ids){
        DaySteps.sample(c,()->render(c,manager,ids));
    }

    /** Redraws every placed widget, at most once every 30 seconds. */
    static void refresh(Context c){
        long now=SystemClock.elapsedRealtime();if(now-lastRefresh<30000&&lastRefresh>0)return;lastRefresh=now;
        AppWidgetManager manager=AppWidgetManager.getInstance(c);
        int[] ids=manager.getAppWidgetIds(new ComponentName(c,StepsWidget.class));
        if(ids!=null&&ids.length>0)render(c.getApplicationContext(),manager,ids);
        WeekWidget.refresh(c.getApplicationContext());
    }

    private static void render(Context c,AppWidgetManager manager,int[] ids){
        int goal=c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE).getInt("stepGoal",10000);
        int moveGoal=c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE).getInt("activeGoalMin",30);
        boolean data=DaySteps.hasTodayData(c);long steps=DaySteps.todayTotal(c);long minutes=DaySteps.moveMinutesToday(c);
        RemoteViews views=new RemoteViews(c.getPackageName(),R.layout.widget_steps);
        views.setImageViewBitmap(R.id.widget_ring,ring(c,data?steps:-1,goal,Math.min(1f,minutes/(float)Math.max(1,moveGoal))));
        views.setTextViewText(R.id.widget_caption,data?String.format(Locale.US,"of %,d · %d move min",goal,minutes):"Open Kadamm to set up steps");
        views.setOnClickPendingIntent(R.id.widget_root,PendingIntent.getActivity(c,5,new Intent(c,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
        for(int id:ids)manager.updateAppWidget(id,views);
    }

    static Bitmap ring(Context c,long steps,int goal,float move){
        float d=c.getResources().getDisplayMetrics().density;int size=(int)(132*d);
        Bitmap b=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);Canvas cv=new Canvas(b);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        float stroke=11*d,r=size/2f-stroke/2-2*d,cx=size/2f,cy=size/2f;RectF o=new RectF(cx-r,cy-r,cx+r,cy+r);
        float f=steps<0?0:Math.min(1f,steps/(float)Math.max(1,goal));
        p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(stroke);
        p.setColor(Color.rgb(54,57,62));cv.drawArc(o,0,360,false,p);
        if(f>0){android.graphics.SweepGradient sg=f>=1?new android.graphics.SweepGradient(cx,cy,new int[]{Color.rgb(129,201,149),Color.rgb(30,142,62),Color.rgb(129,201,149)},null):new android.graphics.SweepGradient(cx,cy,new int[]{Color.rgb(77,144,254),Color.rgb(168,199,250),Color.rgb(77,144,254)},null);android.graphics.Matrix m=new android.graphics.Matrix();m.setRotate(-90,cx,cy);sg.setLocalMatrix(m);p.setShader(sg);cv.drawArc(o,-90,Math.max(2f,f*360),false,p);p.setShader(null);}
        float r2=r-stroke-4*d;o.set(cx-r2,cy-r2,cx+r2,cy+r2);p.setStrokeWidth(8*d);
        p.setColor(Color.rgb(54,57,62));cv.drawArc(o,0,360,false,p);
        if(move>0){p.setColor(Color.rgb(129,201,149));cv.drawArc(o,-90,Math.max(2f,move*360),false,p);}
        p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setColor(Color.rgb(232,234,237));p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        String text=steps<0?"—":String.format(Locale.US,"%,d",steps);float ts=24*d;p.setTextSize(ts);
        while(p.measureText(text)>r2*2-22*d&&ts>12*d){ts-=d;p.setTextSize(ts);}
        cv.drawText(text,cx,cy+ts*.2f,p);
        p.setTypeface(Typeface.DEFAULT);p.setColor(f>=1?Color.rgb(129,201,149):Color.rgb(154,160,166));p.setTextSize(10*d);cv.drawText(steps<0?"steps":f>=1?"goal ✓":Math.round(f*100)+"% of goal",cx,cy+ts*.2f+14*d,p);
        return b;
    }
}
