package com.stride.run;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.pdf.PdfDocument;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The health reports as a PDF (A4) to share: Kadamm's logo and name on every page, the latest report test by test
 * (value, the range you entered, status, the previous result and the change), each test's history, what Kadamm
 * measured in the 30 days up to each test, and what moved with the results. Light theme, so it prints well.
 */
final class ReportPdf {
    private ReportPdf() {}
    private static final int W=595,H=842,M=40;
    private static final int INK=Color.rgb(11,11,13),MUTED=Color.rgb(95,99,104),LINE=Color.rgb(227,227,230),BLUE=Color.rgb(26,115,232),GOOD=Color.rgb(24,128,56),WARN=Color.rgb(196,98,0),BAND=Color.rgb(244,246,250);

    private static final class Doc {
        final Context c;final PdfDocument pdf=new PdfDocument();PdfDocument.Page page;Canvas cv;float y;int n;final String stamp;
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Doc(Context c,String stamp){this.c=c;this.stamp=stamp;newPage();}
        void newPage(){
            if(page!=null){footer();pdf.finishPage(page);}
            n++;page=pdf.startPage(new PdfDocument.PageInfo.Builder(W,H,n).create());cv=page.getCanvas();
            Drawable logo=c.getDrawable(R.drawable.ic_kadamm_logo);if(logo!=null){logo.setBounds(M,M-6,M+26,M+20);logo.draw(cv);}
            p.setColor(INK);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));p.setTextSize(13);p.setLetterSpacing(.14f);cv.drawText("KADAMM",M+34,M+12,p);p.setLetterSpacing(0);
            p.setTypeface(Typeface.DEFAULT);p.setTextSize(9);p.setColor(MUTED);p.setTextAlign(Paint.Align.RIGHT);cv.drawText("Lab results · "+stamp,W-M,M+12,p);p.setTextAlign(Paint.Align.LEFT);
            p.setColor(LINE);p.setStrokeWidth(1);cv.drawLine(M,M+28,W-M,M+28,p);y=M+52;
        }
        void footer(){
            p.setTypeface(Typeface.DEFAULT);p.setTextSize(8);p.setColor(MUTED);
            cv.drawText("Kadamm compares your own results; it doesn't diagnose. Typical ranges only where the report gave none.",M,H-28,p);
            p.setTextAlign(Paint.Align.RIGHT);cv.drawText("Page "+n,W-M,H-28,p);p.setTextAlign(Paint.Align.LEFT);
            ShareCard.signature(c,cv,W-M,H-12,MUTED,.22f);
        }
        void need(float h){if(y+h>H-50)newPage();}
        void text(String s,float size,int color,boolean bold,float x){p.setTypeface(bold?Typeface.create("sans-serif-medium",Typeface.BOLD):Typeface.DEFAULT);p.setTextSize(size);p.setColor(color);cv.drawText(s,x,y,p);}
        /** Wrapped text from x to the right margin; moves y down. */
        void para(String s,float size,int color,boolean bold,float x){
            Runnable style=()->{p.setTypeface(bold?Typeface.create("sans-serif-medium",Typeface.BOLD):Typeface.DEFAULT);p.setTextSize(size);p.setColor(color);p.setLetterSpacing(0);};style.run();float max=W-M-x;
            // A new page draws its header with other colours, so the style is set again before each line.
            StringBuilder line=new StringBuilder();for(String w:s.split(" ")){String t=line.length()==0?w:line+" "+w;if(p.measureText(t)>max&&line.length()>0){need(size+4);style.run();cv.drawText(line.toString(),x,y,p);y+=size+4;line=new StringBuilder(w);}else{line=new StringBuilder(t);}}
            if(line.length()>0){need(size+4);style.run();cv.drawText(line.toString(),x,y,p);y+=size+4;}
        }
        void heading(String s){need(40);y+=10;text(s.toUpperCase(Locale.getDefault()),10,BLUE,true,M);p.setLetterSpacing(0);y+=8;p.setColor(LINE);cv.drawLine(M,y,W-M,y,p);y+=16;}
        String fit(String s,float width){if(p.measureText(s)<=width)return s;while(s.length()>2&&p.measureText(s+"…")>width)s=s.substring(0,s.length()-1);return s+"…";}
    }

    /** Builds the PDF in the cache and returns it, or null if it couldn't be written. */
    static File make(Context c,JSONArray hist,List<String[]> now){
        List<JSONObject> reps=HealthReports.all(c);if(reps.isEmpty())return null;
        String stamp=new SimpleDateFormat("d MMM yyyy",Locale.getDefault()).format(new Date());Doc d=new Doc(c,"made "+stamp);
        List<HealthReports.Marker> mk=HealthReports.markers(c,reps);JSONObject latest=reps.get(reps.size()-1);String ld=latest.optString("date");
        String name="";LocalAccount.Account acc=LocalAccount.current(c);if(acc!=null&&acc.name!=null)name=acc.name.trim();

        // Title block.
        d.text("Lab results",24,INK,true,M);d.y+=18;
        d.para((name.isEmpty()?"":name+" · ")+reps.size()+(reps.size()==1?" report":" reports")+", "+nice(reps.get(0).optString("date"))+(reps.size()>1?" to "+nice(ld):""),10,MUTED,false,M);
        int in=0,out=0,none=0;for(HealthReports.Marker m:mk){HealthReports.Point p=m.last();if(!p.date.equals(ld))continue;int s=HealthReports.status(p);if(s==0)in++;else if(s==2)none++;else out++;}
        d.y+=8;d.need(56);d.p.setColor(BAND);d.cv.drawRoundRect(new RectF(M,d.y-4,W-M,d.y+44),8,8,d.p);
        String[][] cells={{"Latest report",nice(ld)},{"In range",String.valueOf(in)},{"Outside range",String.valueOf(out)},{"No range entered",String.valueOf(none)}};float cw=(W-2*M)/4f;
        for(int i=0;i<4;i++){float x=M+12+i*cw;float yy=d.y;d.y=yy+10;d.text(cells[i][0],8,MUTED,false,x);d.y=yy+30;d.text(cells[i][1],15,i==2&&out>0?WARN:INK,true,x);d.y=yy;}d.y+=60;

        // Every measure today.
        if(now!=null&&!now.isEmpty()){d.heading("Your measures today · "+stamp);float c1=M,c2=M+170,c3=M+275;
            d.text("Measure",8,MUTED,true,c1);d.text("Today",8,MUTED,true,c2);d.text("Source or note",8,MUTED,true,c3);d.y+=14;
            for(String[] r:now){d.need(18);boolean blank="—".equals(r[1]);d.text(r[0],9,INK,false,c1);d.text(blank?"—":r[1]+(r[2].isEmpty()?"":" "+r[2]),9,blank?MUTED:INK,!blank,c2);d.p.setTextSize(9);d.text(d.fit(r[3],W-M-c3),9,MUTED,false,c3);
                d.y+=6;d.p.setColor(LINE);d.cv.drawLine(M,d.y,W-M,d.y,d.p);d.y+=12;}}

        // The latest report, test by test.
        d.heading("Latest report · "+nice(ld)+(latest.optString("lab").isEmpty()?"":" · "+latest.optString("lab")));
        float[] col={M,M+170,M+250,M+335,M+415};String[] head={"Test","Result","Range","Status","Previous · change"};
        for(int i=0;i<head.length;i++){d.text(head[i],8,MUTED,true,col[i]);}d.y+=14;
        String group="";
        for(HealthReports.Marker m:mk){HealthReports.Point p=m.last();if(!p.date.equals(ld))continue;String g=HealthReports.groupOf(m.name);
            if(!g.equals(group)){group=g;d.need(30);d.y+=4;d.text(g,9,BLUE,true,M);d.y+=14;}
            d.need(18);int s=HealthReports.status(p);HealthReports.Point pv=m.prev();
            d.p.setTextSize(10);d.text(d.fit(m.name,165),10,INK,false,col[0]);d.text(HealthReports.fmt(p.v)+" "+p.unit,10,s==1||s==-1?WARN:INK,true,col[1]);
            d.text(range(p)+(p.labRange?"":" (typical)"),9,MUTED,false,col[2]);d.text(HealthReports.statusWord(s),9,s==0?GOOD:s==2?MUTED:WARN,true,col[3]);
            if(pv!=null){double ch=p.v-pv.v;d.text(HealthReports.fmt(pv.v)+(pv.v!=0?String.format(Locale.US,"  (%+.0f %%)",ch/Math.abs(pv.v)*100):""),9,MUTED,false,col[4]);}
            d.y+=6;d.p.setColor(LINE);d.cv.drawLine(M,d.y,W-M,d.y,d.p);d.y+=12;}

        // Every test over time.
        boolean anyHist=false;for(HealthReports.Marker m:mk)if(m.points.size()>=2)anyHist=true;
        if(anyHist){d.heading("Each test over time");
            for(HealthReports.Marker m:mk){if(m.points.size()<2)continue;d.need(34);d.text(m.name+(m.unit.isEmpty()?"":" ("+m.unit+")"),10,INK,true,M);d.y+=14;
                StringBuilder b=new StringBuilder();for(HealthReports.Point p:m.points){if(b.length()>0)b.append("   →   ");b.append(shortD(p.date)).append(": ").append(HealthReports.fmt(p.v));int s=HealthReports.status(p);if(s==1)b.append(" ▲");else if(s==-1)b.append(" ▼");}
                d.para(b.toString(),9,MUTED,false,M+8);
                HealthReports.Point a=m.first(),z=m.last();if(a.v!=0)d.para(String.format(Locale.US,"Change from first to latest: %s → %s (%+.0f %%)",HealthReports.fmt(a.v),HealthReports.fmt(z.v),(z.v-a.v)/Math.abs(a.v)*100),9,MUTED,false,M+8);d.y+=4;}}

        // What Kadamm measured around each test, and what moved with the results.
        Map<String,Map<String,double[]>> around=new java.util.HashMap<>();for(JSONObject r:reps){String dt=r.optString("date");if(!around.containsKey(dt))around.put(dt,HealthReports.around(c,hist,dt));}
        List<String> dates=new ArrayList<>();for(JSONObject r:reps)if(!dates.contains(r.optString("date")))dates.add(r.optString("date"));while(dates.size()>5)dates.remove(0);
        d.heading("Measured by Kadamm · 30-day averages up to each test");
        float first=M+175,step=(W-M-first)/Math.max(1,dates.size());d.text("Measure",8,MUTED,true,M);for(int i=0;i<dates.size();i++)d.text(shortD(dates.get(i)),8,MUTED,true,first+i*step);d.y+=14;
        int rows=0;for(String[] me:HealthReports.METRICS){boolean any=false;for(String dt:dates)if(around.get(dt)!=null&&around.get(dt).containsKey(me[0]))any=true;rows++;d.need(28);
            d.text(me[1]+(me[2].isEmpty()?"":" ("+me[2]+")"),9,INK,false,M);if(!any){float yy=d.y;d.y+=10;d.text(HealthReports.missingReason(me[0]),7.5f,MUTED,false,M);d.y=yy;}for(int i=0;i<dates.size();i++){double[] x=around.get(dates.get(i))==null?null:around.get(dates.get(i)).get(me[0]);d.text(x==null?"—":HealthReports.fmtMetric(me[0],x[0]),9,x==null?MUTED:INK,false,first+i*step);}
            d.y+=any?6:14;d.p.setColor(LINE);d.cv.drawLine(M,d.y,W-M,d.y,d.p);d.y+=12;}
        if(rows==0)d.para("Kadamm had no measures from the 30 days before these tests.",9,MUTED,false,M);

        d.heading("What moved with the results");
        int shown=0;
        for(HealthReports.Marker m:mk){if(m.points.size()<2)continue;List<String> lines=new ArrayList<>();
            for(HealthReports.Link l:HealthReports.links(m,around)){if(lines.size()>=3)break;String nm=HealthReports.metricName(l.metric);
                if(!Double.isNaN(l.r)){if(Math.abs(l.r)<.5)continue;lines.add(String.format(Locale.US,"%s: r %+.2f across %d reports (%s).",nm,l.r,l.n,l.r>0?"rose and fell together":"one rose as the other fell"));}
                else{double ch=HealthReports.rel(l.metricFrom,l.metricTo);if(Math.abs(ch)<5)continue;lines.add(String.format(Locale.US,"%s %s → %s %s (%+.0f %%).",nm,HealthReports.fmtMetric(l.metric,l.metricFrom),HealthReports.fmtMetric(l.metric,l.metricTo),HealthReports.metricUnit(l.metric),ch));}}
            if(lines.isEmpty())continue;shown++;d.need(30);d.text(m.name,10,INK,true,M);d.y+=13;for(String s:lines)d.para("•  "+s,9,INK,false,M+8);d.y+=4;}
        if(shown==0)d.para("Appears when a test has 2 or more results and Kadamm has measures from the 30 days before them.",9,MUTED,false,M);
        d.y+=6;d.para("Correlation (r) shows only that two things changed together in these results, not that one caused the other. With few reports, one result can change the picture.",8,MUTED,false,M);

        d.footer();d.pdf.finishPage(d.page);
        try{File dir=new File(c.getCacheDir(),"share");dir.mkdirs();File f=new File(dir,"Kadamm-lab-results.pdf");try(FileOutputStream o=new FileOutputStream(f)){d.pdf.writeTo(o);}d.pdf.close();return f;}
        catch(Exception e){d.pdf.close();return null;}
    }
    private static String range(HealthReports.Point p){boolean lo=!Double.isNaN(p.lo),hi=!Double.isNaN(p.hi);return lo&&hi?HealthReports.fmt(p.lo)+"–"+HealthReports.fmt(p.hi):lo?"≥ "+HealthReports.fmt(p.lo):hi?"≤ "+HealthReports.fmt(p.hi):"—";}
    private static String nice(String k){try{return new SimpleDateFormat("d MMM yyyy",Locale.getDefault()).format(new SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(k));}catch(Exception e){return k;}}
    private static String shortD(String k){try{return new SimpleDateFormat("d MMM ''yy",Locale.getDefault()).format(new SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(k));}catch(Exception e){return k;}}
}
