package com.stride.run;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Analysis over saved sessions: pace and elevation along a route, personal records, and saved routes. */
final class Insights {
    private Insights() {}

    static double metres(double lat1,double lon1,double lat2,double lon2){
        double r=6371000,dLat=Math.toRadians(lat2-lat1),dLon=Math.toRadians(lon2-lon1);
        double a=Math.sin(dLat/2)*Math.sin(dLat/2)+Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*Math.sin(dLon/2)*Math.sin(dLon/2);
        return 2*r*Math.asin(Math.min(1,Math.sqrt(a)));
    }

    /** Route points as {lat, lon}. */
    static List<double[]> points(String route){
        List<double[]> out=new ArrayList<>();
        try{JSONArray a=new JSONArray(route);for(int i=0;i<a.length();i++){JSONObject p=a.optJSONObject(i);if(p!=null)out.add(new double[]{p.optDouble("lat"),p.optDouble("lon")});}}catch(Exception ignored){}
        return out;
    }

    /**
     * Timed track: {cumulative km, seconds since start, altitude or NaN, isRun}. Distance across a GPS gap is not
     * counted. Empty for sessions recorded before points carried timestamps.
     */
    static List<double[]> track(String route){
        List<double[]> out=new ArrayList<>();
        try{
            JSONArray a=new JSONArray(route);double km=0;JSONObject prev=null;
            for(int i=0;i<a.length();i++){
                JSONObject p=a.optJSONObject(i);if(p==null||!p.has("s"))continue;
                if(prev!=null&&!p.optBoolean("break",false))km+=metres(prev.optDouble("lat"),prev.optDouble("lon"),p.optDouble("lat"),p.optDouble("lon"))/1000d;
                out.add(new double[]{km,p.optDouble("s"),p.has("a")?p.optDouble("a"):Double.NaN,"r".equals(p.optString("t"))?1:0});
                prev=p;
            }
        }catch(Exception ignored){}
        return out;
    }

    /** Pace along the route, {km, seconds per km, isRun}, each point averaged over the last 150 m. */
    static List<double[]> pace(List<double[]> track){
        List<double[]> out=new ArrayList<>();int j=0;
        for(int i=1;i<track.size();i++){
            while(j<i-1&&track.get(i)[0]-track.get(j+1)[0]>=.15)j++;
            double dk=track.get(i)[0]-track.get(j)[0],dt=track.get(i)[1]-track.get(j)[1];
            if(dk<.05||dt<=0)continue;
            out.add(new double[]{track.get(i)[0],Math.max(150,Math.min(1800,dt/dk)),track.get(i)[3]});
        }
        return out;
    }

    /** Elevation along the route, {km, metres, isRun}, lightly smoothed. Empty when altitude wasn't recorded. */
    static List<double[]> elevation(List<double[]> track){
        List<double[]> out=new ArrayList<>();double s=Double.NaN;
        for(double[] p:track){if(Double.isNaN(p[2]))continue;s=Double.isNaN(s)?p[2]:s*.8+p[2]*.2;out.add(new double[]{p[0],s,p[3]});}
        return out.size()>=3?out:new ArrayList<>();
    }

    /**
     * Fastest time in seconds to cover {@code km} inside one session, or -1. Uses the timed track when there is one;
     * older sessions fall back to their average pace.
     */
    static double fastest(JSONObject o,double km){
        List<double[]> t=track(o.optString("route","[]"));
        if(t.size()>=2&&t.get(t.size()-1)[0]>=km){
            double best=-1;int j=0;
            for(int i=1;i<t.size();i++){
                while(j<i&&t.get(i)[0]-t.get(j+1)[0]>=km)j++;
                double dk=t.get(i)[0]-t.get(j)[0];if(dk<km)continue;
                double sec=(t.get(i)[1]-t.get(j)[1])*km/dk;if(sec>0&&(best<0||sec<best))best=sec;
            }
            return best;
        }
        double total=o.optDouble("distanceKm",0);
        return total>=km&&o.optLong("durationMs",0)>0?o.optLong("durationMs")/1000d/total*km:-1;
    }

    static final class Record{
        final String label,value,date;final long sessionId;
        Record(String label,String value,String date,long sessionId){this.label=label;this.value=value;this.date=date;this.sessionId=sessionId;}
    }
    static String clock(double sec){long s=Math.round(sec);return s>=3600?String.format(Locale.US,"%d:%02d:%02d",s/3600,(s/60)%60,s%60):String.format(Locale.US,"%d:%02d",s/60,s%60);}

    /** A ride, not a walk or run: 15+ km/h on average with far fewer steps than the distance needs. Same rule as the session note. */
    static boolean vehicle(JSONObject o){double h=o.optLong("durationMs",0)/3600000d,km=o.optDouble("distanceKm",0);return h>0&&km/h>=15&&o.optLong("steps",0)<km*300;}
    /** Fastest 1 km and 5 km, longest distance, longest session and most steps in a day. Vehicle rides are left out. */
    static List<Record> records(JSONArray hist,Map<String,Long> days){
        List<Record> out=new ArrayList<>();
        JSONObject f1=null,f5=null,far=null,lng=null;double b1=-1,b5=-1;
        for(int i=0;i<hist.length();i++){
            JSONObject o=hist.optJSONObject(i);if(o==null||vehicle(o))continue;
            double t1=fastest(o,1),t5=fastest(o,5);
            if(t1>0&&(b1<0||t1<b1)){b1=t1;f1=o;}
            if(t5>0&&(b5<0||t5<b5)){b5=t5;f5=o;}
            if(o.optDouble("distanceKm",0)>0&&(far==null||o.optDouble("distanceKm")>far.optDouble("distanceKm")))far=o;
            if(o.optLong("durationMs",0)>0&&(lng==null||o.optLong("durationMs")>lng.optLong("durationMs")))lng=o;
        }
        if(f1!=null)out.add(new Record("Fastest 1 km",clock(b1),f1.optString("date"),f1.optLong("id")));
        if(f5!=null)out.add(new Record("Fastest 5 km",clock(b5),f5.optString("date"),f5.optLong("id")));
        if(far!=null)out.add(new Record("Longest distance",String.format(Locale.US,"%.2f km",far.optDouble("distanceKm")),far.optString("date"),far.optLong("id")));
        if(lng!=null)out.add(new Record("Longest session",clock(lng.optLong("durationMs")/1000d),lng.optString("date"),lng.optLong("id")));
        String bestDay=null;long most=0;
        for(Map.Entry<String,Long> e:days.entrySet())if(e.getValue()>most){most=e.getValue();bestDay=e.getKey();}
        if(bestDay!=null){
            String label=bestDay;try{label=new SimpleDateFormat("EEE, d MMM yyyy",Locale.getDefault()).format(new SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(bestDay));}catch(Exception ignored){}
            out.add(new Record("Most steps in a day",String.format(Locale.US,"%,d",most),label,0));
        }
        return out;
    }

    // ---- Saved routes ----
    private static SharedPreferences store(Context c){return c.getSharedPreferences("kadamm_routes",Context.MODE_PRIVATE);}
    static JSONArray routes(Context c){try{return new JSONArray(store(c).getString("routes","[]"));}catch(Exception e){return new JSONArray();}}
    private static void put(Context c,JSONArray a){store(c).edit().putString("routes",a.toString()).apply();}
    static void saveRoute(Context c,String name,JSONObject session){
        try{
            JSONObject r=new JSONObject();r.put("id",System.currentTimeMillis());r.put("name",name);r.put("route",session.optString("route","[]"));
            r.put("distanceKm",session.optDouble("distanceKm",0));r.put("fromSession",session.optLong("id"));r.put("createdAt",System.currentTimeMillis());
            JSONArray a=routes(c);a.put(r);put(c,a);
        }catch(Exception ignored){}
    }
    static void deleteRoute(Context c,long id){JSONArray a=routes(c),out=new JSONArray();for(int i=0;i<a.length();i++){JSONObject r=a.optJSONObject(i);if(r!=null&&r.optLong("id")!=id)out.put(r);}put(c,out);}
    static void renameRoute(Context c,long id,String name){JSONArray a=routes(c);try{for(int i=0;i<a.length();i++){JSONObject r=a.optJSONObject(i);if(r!=null&&r.optLong("id")==id)r.put("name",name);}}catch(Exception ignored){}put(c,a);}

    /**
     * A session counts as an attempt of a saved route when it starts and ends within 60 m of the route's start and
     * end, its distance is within 15%, and the route's shape is followed (most of 20 sample points within 40 m).
     */
    static boolean matches(JSONObject route,JSONObject session){
        if(route.optLong("fromSession")==session.optLong("id"))return true;
        double rk=route.optDouble("distanceKm",0),sk=session.optDouble("distanceKm",0);
        if(rk<=0||Math.abs(sk-rk)>rk*.15)return false;
        List<double[]> r=points(route.optString("route","[]")),s=points(session.optString("route","[]"));
        if(r.size()<2||s.size()<2)return false;
        double[] r0=r.get(0),r1=r.get(r.size()-1),s0=s.get(0),s1=s.get(s.size()-1);
        if(metres(r0[0],r0[1],s0[0],s0[1])>60||metres(r1[0],r1[1],s1[0],s1[1])>60)return false;
        int hit=0,tests=0;
        for(int k=0;k<20;k++){
            double[] q=r.get(k*(r.size()-1)/19);tests++;
            for(double[] p:s)if(metres(q[0],q[1],p[0],p[1])<=40){hit++;break;}
        }
        return hit>=tests*.8;
    }
    /** Sessions that ran a route, oldest first. */
    static List<JSONObject> attempts(JSONObject route,JSONArray hist){
        List<JSONObject> out=new ArrayList<>();
        for(int i=0;i<hist.length();i++){JSONObject o=hist.optJSONObject(i);if(o!=null&&matches(route,o))out.add(o);}
        out.sort((a,b)->Long.compare(a.optLong("id"),b.optLong("id")));return out;
    }
    static String ago(long ms){return new SimpleDateFormat("d MMM",Locale.getDefault()).format(new Date(ms));}

    // ---- Places you walk ----
    static final class Coverage {
        /** Every saved route joined into one, each session starting a new stretch. */
        String route="[]";
        int sessions;double uniqueKm,weekKm,monthKm,totalKm;
        /** {minLat,maxLat,minLon,maxLon} of the 3 km area with the most covered streets; null with no routes. */
        double[] home;int areas;
    }
    private static final double CELL=25;
    /**
     * The streets you have covered: every route (vehicle rides left out) is walked in 10 m steps and each 25 m
     * square it touches is counted once, so walking the same street again adds nothing. A square's date is the
     * first session that reached it, which gives "new this week" and "new this month".
     */
    static Coverage coverage(JSONArray hist){
        Coverage cv=new Coverage();java.util.HashMap<Long,Long> first=new java.util.HashMap<>();JSONArray all=new JSONArray();
        long now=System.currentTimeMillis();
        for(int i=0;i<hist.length();i++){
            JSONObject o=hist.optJSONObject(i);if(o==null||vehicle(o))continue;
            JSONArray r;try{r=new JSONArray(o.optString("route","[]"));}catch(Exception e){continue;}
            if(r.length()<2)continue;cv.sessions++;cv.totalKm+=o.optDouble("distanceKm",0);
            long when=o.optLong("startTimeMs",o.optLong("id",0));
            JSONObject prev=null;
            for(int k=0;k<r.length();k++){
                JSONObject q=r.optJSONObject(k);if(q==null)continue;
                try{JSONObject c=new JSONObject(q.toString());if(k==0)c.put("break",true);all.put(c);}catch(Exception ignored){}
                if(prev!=null&&!q.optBoolean("break",false)){
                    double d=metres(prev.optDouble("lat"),prev.optDouble("lon"),q.optDouble("lat"),q.optDouble("lon"));
                    if(d<200){int n=Math.max(1,(int)Math.ceil(d/10));for(int t=0;t<=n;t++){double f=t/(double)n;mark(first,prev.optDouble("lat")+(q.optDouble("lat")-prev.optDouble("lat"))*f,prev.optDouble("lon")+(q.optDouble("lon")-prev.optDouble("lon"))*f,when);}}
                }
                prev=q;
            }
        }
        for(long t:first.values()){cv.uniqueKm+=CELL/1000;if(now-t<7L*86400000)cv.weekKm+=CELL/1000;if(now-t<30L*86400000)cv.monthKm+=CELL/1000;}
        // Home area: bucket covered squares into 3 km tiles and fit the busiest one (with a little margin).
        java.util.HashMap<Long,double[]> tiles=new java.util.HashMap<>();
        for(int i=0;i<all.length();i++){JSONObject q=all.optJSONObject(i);if(q==null)continue;double la=q.optDouble("lat"),lo=q.optDouble("lon");long k=Math.round(la*111320/3000)*1000003L+Math.round(lo*111320*Math.cos(Math.toRadians(la))/3000);
            double[] b=tiles.get(k);if(b==null)tiles.put(k,new double[]{la,la,lo,lo,1});else{b[0]=Math.min(b[0],la);b[1]=Math.max(b[1],la);b[2]=Math.min(b[2],lo);b[3]=Math.max(b[3],lo);b[4]++;}}
        double[] best=null;for(double[] b:tiles.values())if(best==null||b[4]>best[4])best=b;cv.areas=tiles.size();
        if(best!=null){double m=.0015;cv.home=new double[]{best[0]-m,best[1]+m,best[2]-m,best[3]+m};}
        cv.route=all.toString();return cv;
    }
    private static void mark(java.util.HashMap<Long,Long> first,double lat,double lon,long when){
        long y=Math.round(lat*111320/CELL),x=Math.round(lon*111320*Math.cos(Math.toRadians(lat))/CELL),key=y*4000003L+x;
        Long old=first.get(key);if(old==null||when<old)first.put(key,when);
    }
}
