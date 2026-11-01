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
import android.widget.RemoteViews;

import java.util.Calendar;
import java.util.Locale;
import java.util.Map;

/** Large home-screen widget (4×2): both rings, today's numbers and this week's step bars. */
public class WeekWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context c,AppWidgetManager manager,int[] ids){DaySteps.sample(c,()->render(c,manager,ids));}

    static void refresh(Context c){
        AppWidgetManager m=AppWidgetManager.getInstance(c);int[] ids=m.getAppWidgetIds(new ComponentName(c,WeekWidget.class));
        if(ids!=null&&ids.length>0)render(c.getApplicationContext(),m,ids);
    }
    private static void render(Context c,AppWidgetManager manager,int[] ids){
        int goal=c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE).getInt("stepGoal",10000),moveGoal=c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE).getInt("activeGoalMin",30);
        boolean data=DaySteps.hasTodayData(c);long steps=DaySteps.todayTotal(c),minutes=DaySteps.moveMinutesToday(c);
        RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.widget_week);
        v.setImageViewBitmap(R.id.ww_ring,StepsWidget.ring(c,data?steps:-1,goal,Math.min(1f,minutes/(float)Math.max(1,moveGoal))));
        v.setTextViewText(R.id.ww_steps,data?String.format(Locale.US,"%,d",steps):"—");
        v.setTextViewText(R.id.ww_goal,data?(steps>=goal?String.format(Locale.US,"of %,d steps · goal reached",goal):String.format(Locale.US,"of %,d steps · %,d to go",goal,goal-steps)):"Open Kadamm to set up steps");
        v.setTextViewText(R.id.ww_more,String.format(Locale.US,"%.2f km  ·  %,d active kcal  ·  %d min",DaySteps.distanceToday(c)[0],DaySteps.activeKcalToday(c),minutes));
        v.setImageViewBitmap(R.id.ww_bars,bars(c,goal));
        v.setOnClickPendingIntent(R.id.ww_root,PendingIntent.getActivity(c,8,new Intent(c,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
        for(int id:ids)manager.updateAppWidget(id,v);
    }
    /** Monday-to-Sunday bars; goal days solid blue, today white. */
    private static Bitmap bars(Context c,int goal){
        float d=c.getResources().getDisplayMetrics().density;int w=(int)(300*d),h=(int)(64*d);
        Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas cv=new Canvas(b);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Map<String,Long> totals=DaySteps.allDayTotals(c);Progress.Period wk=Progress.week(0);Calendar day=(Calendar)wk.start.clone();String today=DaySteps.todayKey();
        long[] v=new long[7];long max=goal;for(int i=0;i<7;i++,day.add(Calendar.DAY_OF_YEAR,1)){Long x=totals.get(DaySteps.key(day.getTimeInMillis()));v[i]=x==null?0:x;max=Math.max(max,v[i]);}
        float slot=w/7f,base=h-14*d,ch=base-2*d;day=(Calendar)wk.start.clone();
        String[] names={"M","T","W","T","F","S","S"};
        float gy=base-goal*ch/max;p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(d);p.setColor(Color.argb(110,154,160,166));p.setPathEffect(new android.graphics.DashPathEffect(new float[]{3*d,3*d},0));cv.drawLine(0,gy,w,gy,p);p.setPathEffect(null);p.setStyle(Paint.Style.FILL);
        for(int i=0;i<7;i++,day.add(Calendar.DAY_OF_YEAR,1)){
            boolean isToday=DaySteps.key(day.getTimeInMillis()).equals(today);float bw=slot*.55f,cx=slot*(i+.5f),bh=Math.max(3*d,v[i]*ch/max);
            RectF bar=new RectF(cx-bw/2,base-bh,cx+bw/2,base);
            p.setColor(Color.BLACK);
            if(v[i]>=goal)p.setShader(new android.graphics.LinearGradient(0,bar.top,0,base,Color.rgb(129,201,149),Color.rgb(30,142,62),android.graphics.Shader.TileMode.CLAMP));
            else p.setShader(new android.graphics.LinearGradient(0,bar.top,0,base,isToday?Color.rgb(168,199,250):Color.argb(200,77,144,254),isToday?Color.rgb(77,144,254):Color.argb(110,77,144,254),android.graphics.Shader.TileMode.CLAMP));
            cv.drawRoundRect(bar,5*d,5*d,p);p.setShader(null);
            if(isToday){p.setColor(Color.WHITE);cv.drawCircle(cx,base-bh-5*d,2.2f*d,p);}
            p.setColor(isToday?Color.WHITE:Color.rgb(154,160,166));p.setTextSize(10*d);p.setTextAlign(Paint.Align.CENTER);cv.drawText(names[i],cx,h-2*d,p);
        }
        return b;
    }
}
