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

    /** Fastest 1 km and 5 km, longest distance, longest session and most steps in a day. */
    static List<Record> records(JSONArray hist,Map<String,Long> days){
        List<Record> out=new ArrayList<>();
        JSONObject f1=null,f5=null,far=null,lng=null;double b1=-1,b5=-1;
        for(int i=0;i<hist.length();i++){
            JSONObject o=hist.optJSONObject(i);if(o==null)continue;
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
}
