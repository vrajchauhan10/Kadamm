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
    /**
     * A full 9:16 story frame (1080 × 1920): the route map fills the top two-thirds edge to edge, then the title,
     * a large distance with time and pace beside it, three more numbers, and the footer with the maker's mark.
     * Dark: black card, blue route on a greyscale map. Light: white card on the standard map.
     */
    static Bitmap render(Context c,JSONObject o,String title,boolean dark){
        final int BG=dark?BLACK:Color.WHITE,TEXT=dark?Color.WHITE:Color.rgb(11,11,13),MUTED=dark?Color.rgb(154,160,166):Color.rgb(95,99,104),LINE=dark?Color.rgb(42,42,46):Color.rgb(227,227,230),RT=dark?ROUTE:BLUE;
        int w=1080,h=1920,mapH=1180;
        Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas cv=new Canvas(b);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        cv.drawColor(BG);
        String route=o.optString("route","[]");
        if(route.contains("lat")){
            RouteView map=new RouteView(c);map.setThemeExact(BG,LINE,RT,dark);map.setRunColor(RUN);map.setLineScale(2.1f);map.setFollowLocation(false);map.setRoute(route);
            map.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(mapH,View.MeasureSpec.EXACTLY));
            map.layout(0,0,w,mapH);cv.save();cv.clipRect(0,0,w,mapH);map.draw(cv);cv.restore();
        }else{
            p.setColor(dark?Color.rgb(22,22,26):Color.rgb(241,243,244));cv.drawRect(0,0,w,mapH,p);
            p.setColor(MUTED);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextSize(70);
            cv.drawText("indoor".equals(o.optString("activityMode"))?"Indoor session":"No GPS route",w/2f,mapH/2f,p);p.setTextAlign(Paint.Align.LEFT);
        }
        p.setShader(new LinearGradient(0,mapH-420,0,mapH,Color.argb(0,Color.red(BG),Color.green(BG),Color.blue(BG)),BG,Shader.TileMode.CLAMP));cv.drawRect(0,mapH-420,w,mapH,p);
        p.setShader(new LinearGradient(0,0,0,260,dark?Color.argb(190,0,0,0):Color.argb(215,255,255,255),Color.argb(0,Color.red(BG),Color.green(BG),Color.blue(BG)),Shader.TileMode.CLAMP));cv.drawRect(0,0,w,260,p);p.setShader(null);
        Drawable logo=c.getDrawable(dark?R.drawable.ic_kadamm_logo_dark:R.drawable.ic_kadamm_logo);
        if(logo!=null){logo.setBounds(64,72,64+96,72+96);logo.draw(cv);}
        p.setColor(TEXT);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextSize(50);p.setLetterSpacing(.16f);cv.drawText("KADAMM",184,138,p);p.setLetterSpacing(0f);
        // Activity type chip, top right.
        String type=o.optString("type","Walk");boolean run="Run".equals(type);String chip=(o.optBoolean("auto",false)?"AUTO · ":"")+type.toUpperCase(Locale.US);
        p.setTextSize(32);float cw=p.measureText(chip)+56;p.setColor(run?RUN:RT);cv.drawRoundRect(new android.graphics.RectF(w-64-cw,90,w-64,150),30,30,p);p.setColor(Color.WHITE);cv.drawText(chip,w-64-cw+28,132,p);

        double walkKm=o.optDouble("walkDistanceKm",0),runKm=o.optDouble("runDistanceKm",0);
        if(runKm>0||route.contains("\"t\":\"r\"")){
            p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextSize(34);float lx=64,ly=mapH-40;
            p.setColor(RT);cv.drawCircle(lx+11,ly-12,11,p);p.setColor(TEXT);String wl=String.format(Locale.US,"Walk %.2f km",walkKm);cv.drawText(wl,lx+32,ly,p);
            lx+=32+p.measureText(wl)+44;p.setColor(RUN);cv.drawCircle(lx+11,ly-12,11,p);p.setColor(TEXT);cv.drawText(String.format(Locale.US,"Run %.2f km",runKm),lx+32,ly,p);
        }
        float y=mapH+20;p.setColor(TEXT);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextSize(78);String t=title;
        while(p.measureText(t)>w-128&&t.length()>4)t=t.substring(0,t.length()-2);
        cv.drawText(t.equals(title)?t:t+"…",64,y+60,p);
        p.setTypeface(Typeface.DEFAULT);p.setColor(MUTED);p.setTextSize(36);
        cv.drawText(o.optString("date","")+"  ·  "+o.optString("time",""),64,y+120,p);

        double km=o.optDouble("distanceKm",0);long ms=o.optLong("durationMs",0),pace=km>0?Math.round(ms/1000d/km):0;
        // Hero row: distance large on the left, time and pace stacked on the right.
        float hy=y+300;p.setColor(RT);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextSize(176);String dk=String.format(Locale.US,"%.2f",km);cv.drawText(dk,56,hy,p);
        float dw=p.measureText(dk);p.setColor(MUTED);p.setTextSize(44);p.setTypeface(Typeface.DEFAULT);cv.drawText("km",56+dw+14,hy,p);
        float rx=w-64;p.setTextAlign(Paint.Align.RIGHT);
        p.setColor(MUTED);p.setTextSize(28);p.setLetterSpacing(.1f);cv.drawText("TIME",rx,hy-140,p);cv.drawText("AVG PACE",rx,hy-40,p);p.setLetterSpacing(0f);
        p.setColor(TEXT);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextSize(58);cv.drawText(duration(ms),rx,hy-84,p);cv.drawText(km>0?String.format(Locale.US,"%d:%02d /km",pace/60,pace%60):"—",rx,hy+16,p);p.setTextAlign(Paint.Align.LEFT);
        String[][] stats={
            {"STEPS",o.optBoolean("stepsAvailable",false)?String.format(Locale.US,"%,d",o.optLong("steps")):"—",""},
            {"CALORIES",String.format(Locale.US,"%d",o.optInt("calories",(int)Math.round(km*65))),"kcal"},
            {"ELEV. GAIN",o.has("elevGainM")?String.format(Locale.US,"%.0f",o.optDouble("elevGainM")):"—",o.has("elevGainM")?"m":""}};
        float colW=(w-128)/3f,top=hy+90;
        p.setColor(LINE);p.setStrokeWidth(2);cv.drawLine(64,top,w-64,top,p);
        for(int i=0;i<stats.length;i++){
            float x=64+i*colW,ry=top+40;
            p.setColor(MUTED);p.setTextSize(28);p.setLetterSpacing(.1f);p.setTypeface(Typeface.DEFAULT);cv.drawText(stats[i][0],x,ry+24,p);p.setLetterSpacing(0f);
            p.setColor(TEXT);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextSize(64);cv.drawText(stats[i][1],x,ry+104,p);
            float vw=p.measureText(stats[i][1]);p.setColor(MUTED);p.setTextSize(30);p.setTypeface(Typeface.DEFAULT);cv.drawText(stats[i][2],x+vw+10,ry+104,p);
        }
        p.setColor(RT);cv.drawRect(64,h-104,124,h-98,p);
        p.setColor(MUTED);p.setTextSize(32);cv.drawText("Tracked with Kadamm  ·  Every step counts",64,h-52,p);
        signature(c,cv,w-64,h-50,MUTED);
        return b;
    }
    /** "crafted by V" in the app's signature fonts, right-aligned at (right, baseline). */
    static void signature(Context c,Canvas cv,float right,float baseline,int color){
        try{
            Paint a=new Paint(Paint.ANTI_ALIAS_FLAG);a.setColor(Color.argb(170,Color.red(color),Color.green(color),Color.blue(color)));
            Paint v=new Paint(a);a.setTypeface(c.getResources().getFont(R.font.great_vibes));a.setTextSize(52);v.setTypeface(c.getResources().getFont(R.font.parisienne));v.setTextSize(56);
            float vw=v.measureText("V"),aw=a.measureText("crafted by ");cv.drawText("crafted by ",right-vw-aw,baseline,a);cv.drawText("V",right-vw,baseline,v);
        }catch(Exception ignored){}
    }
    private static String duration(long ms){long s=ms/1000;return s>=3600?String.format(Locale.US,"%d:%02d:%02d",s/3600,(s/60)%60,s%60):String.format(Locale.US,"%d:%02d",s/60,s%60);}
}
