package com.stride.run;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.widget.RemoteViews;

/** Home-screen widget: the daily-goals capsule with steps, brisk minutes and distance, and what's still to go. */
public class GoalsWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context c,AppWidgetManager manager,int[] ids){DaySteps.sample(c,()->render(c,manager,ids));WidgetTick.schedule(c);}

    /** Redraws placed widgets; called with the steps widget's refresh, so at most every 30 seconds. */
    static void refresh(Context c){
        AppWidgetManager manager=AppWidgetManager.getInstance(c);int[] ids=manager.getAppWidgetIds(new ComponentName(c,GoalsWidget.class));
        if(ids!=null&&ids.length>0)render(c,manager,ids);
    }
    private static CharSequence line(GoalCapsule.Day d,int i){
        SpannableStringBuilder b=new SpannableStringBuilder();int st=b.length();b.append("● ").append(GoalCapsule.NAMES[i]);
        b.setSpan(new ForegroundColorSpan(GoalCapsule.COLORS[i]),st,b.length(),0);b.setSpan(new StyleSpan(android.graphics.Typeface.BOLD),st,b.length(),0);
        b.append("  ").append(d.value(i)).append(" · ");int p=b.length();b.append(d.pending(i));
        if(d.done[i]>=1)b.setSpan(new ForegroundColorSpan(0xFF81C995),p,b.length(),0);
        return b;
    }
    private static void render(Context c,AppWidgetManager manager,int[] ids){
        GoalCapsule.Day d=GoalCapsule.today(c);int total=d.total();
        RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.widget_goals);
        v.setImageViewBitmap(R.id.goals_capsule,GoalCapsule.bitmap(c,d,58,120));
        v.setTextViewText(R.id.goals_title,total>=100?"Perfect day ✦":"Daily goals · "+total+"%");
        SpannableStringBuilder big=new SpannableStringBuilder(String.format(java.util.Locale.US,"%,d",d.steps));int e=big.length();big.append(" steps");big.setSpan(new android.text.style.RelativeSizeSpan(.42f),e,big.length(),0);big.setSpan(new ForegroundColorSpan(0xFF9AA0A6),e,big.length(),0);
        v.setTextViewText(R.id.goals_big,big);
        v.setTextViewText(R.id.goals_steps,line(d,0));v.setTextViewText(R.id.goals_brisk,line(d,1));v.setTextViewText(R.id.goals_km,line(d,2));
        v.setOnClickPendingIntent(R.id.goals_root,PendingIntent.getActivity(c,8,new Intent(c,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
        for(int id:ids)manager.updateAppWidget(id,v);
    }
}
