package com.stride.run;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.View;

import org.json.JSONObject;
import java.util.Locale;

/** The shareable activity image: route map on top, Kadamm branding, and the session's numbers. */
final class ShareCard {
    /** Kadamm map theme: black, white and one blue. */
    static final int RUN=Color.rgb(234,67,53);
    static final int BLACK=Color.rgb(10,10,12),BLUE=Color.rgb(26,115,232),ROUTE=Color.rgb(77,144,254);
    private ShareCard() {}

    static Bitmap render(Context c,JSONObject o,String title){return render(c,o,title,true);}
    /** Dark: black card, blue route on a greyscale map. Light: white card on the standard map. */
    static Bitmap render(Context c,JSONObject o,String title,boolean dark){
        final int BG=dark?BLACK:Color.WHITE,TEXT=dark?Color.WHITE:Color.rgb(11,11,13),MUTED=dark?Color.rgb(154,160,166):Color.rgb(95,99,104),LINE=dark?Color.rgb(42,42,46):Color.rgb(227,227,230),RT=dark?ROUTE:BLUE;
        int w=1080,h=1400,mapH=800;
        Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas cv=new Canvas(b);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        cv.drawColor(BG);
        String route=o.optString("route","[]");
        if(route.contains("lat")){
            RouteView map=new RouteView(c);map.setTheme(BG,LINE,RT,dark);map.setRunColor(RUN);map.setLineScale(1.7f);map.setFollowLocation(false);map.setRoute(route);
            map.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(mapH,View.MeasureSpec.EXACTLY));
            map.layout(0,0,w,mapH);cv.save();cv.clipRect(0,0,w,mapH);map.draw(cv);cv.restore();
        }else{
            p.setColor(dark?Color.rgb(22,22,26):Color.rgb(241,243,244));cv.drawRect(0,0,w,mapH,p);
            p.setColor(TEXT);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextSize(64);
            cv.drawText("indoor".equals(o.optString("activityMode"))?"Indoor session":"No GPS route",w/2f,mapH/2f,p);p.setTextAlign(Paint.Align.LEFT);
        }
        p.setShader(new LinearGradient(0,mapH-320,0,mapH,Color.argb(0,Color.red(BG),Color.green(BG),Color.blue(BG)),BG,Shader.TileMode.CLAMP));cv.drawRect(0,mapH-320,w,mapH,p);
        p.setShader(new LinearGradient(0,0,0,220,dark?Color.argb(170,0,0,0):Color.argb(200,255,255,255),Color.argb(0,Color.red(BG),Color.green(BG),Color.blue(BG)),Shader.TileMode.CLAMP));cv.drawRect(0,0,w,220,p);p.setShader(null);
        Drawable logo=c.getDrawable(dark?R.drawable.ic_kadamm_logo_dark:R.drawable.ic_kadamm_logo);
        if(logo!=null){logo.setBounds(56,50,56+88,50+88);logo.draw(cv);}
        p.setColor(TEXT);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextSize(46);p.setLetterSpacing(.16f);cv.drawText("KADAMM",166,110,p);p.setLetterSpacing(0f);

        double walkKm=o.optDouble("walkDistanceKm",0),runKm=o.optDouble("runDistanceKm",0);
        if(runKm>0||route.contains("\"t\":\"r\"")){
            p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextSize(32);float lx=64,ly=mapH-36;
            p.setColor(RT);cv.drawCircle(lx+10,ly-11,10,p);p.setColor(TEXT);String wl=String.format(Locale.US,"Walk %.2f km",walkKm);cv.drawText(wl,lx+30,ly,p);
            lx+=30+p.measureText(wl)+40;p.setColor(RUN);cv.drawCircle(lx+10,ly-11,10,p);p.setColor(TEXT);cv.drawText(String.format(Locale.US,"Run %.2f km",runKm),lx+30,ly,p);
        }
        float y=mapH+10;p.setTextSize(66);String t=title;
        while(p.measureText(t)>w-128&&t.length()>4)t=t.substring(0,t.length()-2);
        cv.drawText(t.equals(title)?t:t+"…",64,y+50,p);
        p.setTypeface(Typeface.DEFAULT);p.setColor(MUTED);p.setTextSize(34);
        cv.drawText(o.optString("date","")+"  ·  "+o.optString("time","")+"  ·  "+o.optString("activityLabel",o.optString("type","Walk")).replace(" · recovered after an interruption",""),64,y+108,p);

        double km=o.optDouble("distanceKm",0);long ms=o.optLong("durationMs",0),pace=km>0?Math.round(ms/1000d/km):0;
        String[][] stats={
            {"DISTANCE",String.format(Locale.US,"%.2f",km),"km"},
            {"TIME",duration(ms),""},
            {"AVG PACE",km>0?String.format(Locale.US,"%d:%02d",pace/60,pace%60):"—",km>0?"/km":""},
            {"STEPS",o.optBoolean("stepsAvailable",false)?String.format(Locale.US,"%,d",o.optLong("steps")):"—",""},
            {"CALORIES",String.format(Locale.US,"%d",o.optInt("calories",(int)Math.round(km*65))),"kcal"},
            {"ELEV. GAIN",o.has("elevGainM")?String.format(Locale.US,"%.0f",o.optDouble("elevGainM")):"—",o.has("elevGainM")?"m":""}};
        float colW=(w-128)/3f,top=y+180;
        p.setColor(LINE);p.setStrokeWidth(2);cv.drawLine(64,top-36,w-64,top-36,p);
        for(int i=0;i<stats.length;i++){
            float x=64+(i%3)*colW,ry=top+(i/3)*165;
            p.setColor(MUTED);p.setTextSize(28);p.setLetterSpacing(.1f);p.setTypeface(Typeface.DEFAULT);cv.drawText(stats[i][0],x,ry+20,p);p.setLetterSpacing(0f);
            p.setColor(i==0?RT:TEXT);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextSize(70);cv.drawText(stats[i][1],x,ry+100,p);
            float vw=p.measureText(stats[i][1]);p.setColor(MUTED);p.setTextSize(30);p.setTypeface(Typeface.DEFAULT);cv.drawText(stats[i][2],x+vw+10,ry+100,p);
        }
        p.setColor(RT);cv.drawRect(64,h-92,124,h-86,p);
        p.setColor(MUTED);p.setTextSize(30);cv.drawText("Tracked with Kadamm  ·  Every step counts",64,h-44,p);
        return b;
    }
    private static String duration(long ms){long s=ms/1000;return s>=3600?String.format(Locale.US,"%d:%02d:%02d",s/3600,(s/60)%60,s%60):String.format(Locale.US,"%d:%02d",s/60,s%60);}
}
