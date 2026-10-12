package com.stride.run;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Small native distance chart aggregated from saved Kadamm sessions. */
final class ActivityChartView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final JSONArray sessions;private final String range;private final KadammTheme theme;
    ActivityChartView(Context context,JSONArray sessions,String range,KadammTheme theme){super(context);this.sessions=sessions;this.range=range;this.theme=theme;setContentDescription("Distance chart for the selected activity time range");}
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();int n="7D".equals(range)?7:"30D".equals(range)?6:12;double[] values=new double[n];String[] labels=new String[n];Calendar now=Calendar.getInstance();if("12M".equals(range)){Calendar start=(Calendar)now.clone();start.set(Calendar.DAY_OF_MONTH,1);start.add(Calendar.MONTH,-(n-1));for(int i=0;i<n;i++){Calendar d=(Calendar)start.clone();d.add(Calendar.MONTH,i);labels[i]=new SimpleDateFormat("MMM",Locale.getDefault()).format(d.getTime());}}else if("30D".equals(range)){for(int i=0;i<n;i++){int daysAgo=(n-1-i)*5;Calendar d=(Calendar)now.clone();d.add(Calendar.DAY_OF_YEAR,-daysAgo);labels[i]=new SimpleDateFormat("d",Locale.getDefault()).format(d.getTime());}}else{for(int i=0;i<n;i++){Calendar d=(Calendar)now.clone();d.add(Calendar.DAY_OF_YEAR,-(n-1-i));labels[i]=new SimpleDateFormat("EE",Locale.getDefault()).format(d.getTime());}}
        for(int k=0;k<sessions.length();k++){JSONObject o=sessions.optJSONObject(k);if(o==null)continue;Calendar d=parse(o.optString("day",""));if(d==null)continue;int ix=-1;if("12M".equals(range)){Calendar base=(Calendar)now.clone();base.set(Calendar.DAY_OF_MONTH,1);int delta=(d.get(Calendar.YEAR)-base.get(Calendar.YEAR))*12+d.get(Calendar.MONTH)-base.get(Calendar.MONTH);ix=n-1+delta;}else if("30D".equals(range)){long age=(dayStart(now)-dayStart(d))/86400000L;if(age>=0&&age<30)ix=n-1-(int)(age/5);}else{long age=(dayStart(now)-dayStart(d))/86400000L;if(age>=0&&age<7)ix=n-1-(int)age;}if(ix>=0&&ix<n)values[ix]+=o.optDouble("distanceKm",0);}
        float left=dp(7),right=dp(6),top=dp(12),bottom=dp(24),chartH=h-top-bottom;float chartW=w-left-right;double max=0;for(double v:values)max=Math.max(max,v);p.setColor(theme.border);p.setStrokeWidth(dp(1));c.drawLine(left,top+chartH,left+chartW,top+chartH,p);if(max<=0){p.setColor(theme.textMuted);p.setTextSize(dp(12));c.drawText("Complete a session to see your distance trend",left,top+chartH*.55f,p);drawLabels(c,labels,left,chartW,bottom,h);return;}float slot=chartW/n;for(int i=0;i<n;i++){float barW=Math.max(dp(5),slot*.52f);float barH=(float)(chartH*.88*values[i]/max);float x=left+i*slot+(slot-barW)/2;float y=top+chartH-barH;p.setColor(theme.accent);c.drawRoundRect(new RectF(x,y,x+barW,top+chartH),dp(4),dp(4),p);}drawLabels(c,labels,left,chartW,bottom,h);}
    private void drawLabels(Canvas c,String[] labels,float left,float chartW,float bottom,float h){int step=labels.length>8?2:1;float slot=chartW/labels.length;p.setColor(theme.textMuted);p.setTextSize(dp(9));p.setTextAlign(Paint.Align.CENTER);for(int i=0;i<labels.length;i+=step)c.drawText(labels[i],left+i*slot+slot/2,h-dp(5),p);p.setTextAlign(Paint.Align.LEFT);}
    private Calendar parse(String value){try{Date d=new SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(value);Calendar c=Calendar.getInstance();c.setTime(d);c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return c;}catch(Exception e){return null;}}
    private long dayStart(Calendar d){Calendar c=(Calendar)d.clone();c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return c.getTimeInMillis();}
}
