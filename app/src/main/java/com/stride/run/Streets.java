package com.stride.run;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Names the streets you have walked. Each covered 100 m square is looked up once with Android's geocoder and the
 * name is kept on the phone (prefs kadamm_streets), so later visits cost nothing. Squares with no street name are
 * remembered as unnamed. Lookups run on a background thread, a batch at a time.
 */
final class Streets {
    private Streets() {}
    static final class Street { String name,area;double km;int blocks; }
    private static volatile boolean running;
    static boolean running(){return running;}
    private static SharedPreferences p(Context c){return c.getSharedPreferences("kadamm_streets",Context.MODE_PRIVATE);}
    static boolean available(){return Geocoder.isPresent();}

    /** Squares still without a name lookup. */
    static int pending(Context c,Insights.Coverage cv){int n=0;SharedPreferences s=p(c);for(Long k:cv.blocks.keySet())if(!s.contains(String.valueOf(k)))n++;return n;}

    /** Streets from the names known so far, longest first. Distance is the unique ground covered on each street. */
    static List<Street> list(Context c,Insights.Coverage cv){
        SharedPreferences s=p(c);Map<String,Street> by=new HashMap<>();
        for(Map.Entry<Long,double[]> e:cv.blocks.entrySet()){
            String v=s.getString(String.valueOf(e.getKey()),null);if(v==null||v.isEmpty())continue;String[] nv=v.split("\\|",-1);
            Street st=by.get(nv[0]);if(st==null){st=new Street();st.name=nv[0];st.area=nv.length>1?nv[1]:"";by.put(nv[0],st);}
            st.km+=e.getValue()[2]*.025;st.blocks++;
        }
        List<Street> out=new ArrayList<>(by.values());out.sort((a,b)->Double.compare(b.km,a.km));return out;
    }

    /** Looks up to {@code batch} unnamed squares in the background, then calls {@code done} on the main thread. */
    static void lookUp(Context c,Insights.Coverage cv,int batch,Runnable done){
        if(running||!available())return;running=true;Context app=c.getApplicationContext();
        List<Map.Entry<Long,double[]>> todo=new ArrayList<>();SharedPreferences s=p(app);
        for(Map.Entry<Long,double[]> e:cv.blocks.entrySet())if(!s.contains(String.valueOf(e.getKey()))){todo.add(e);if(todo.size()>=batch)break;}
        new Thread(()->{
            try{
                Geocoder g=new Geocoder(app,Locale.getDefault());SharedPreferences.Editor ed=p(app).edit();int n=0;
                for(Map.Entry<Long,double[]> e:todo){
                    String name="";
                    try{
                        @SuppressWarnings("deprecation") List<Address> r=g.getFromLocation(e.getValue()[0],e.getValue()[1],1);
                        if(r!=null&&!r.isEmpty()){Address a=r.get(0);String street=a.getThoroughfare();String area=a.getSubLocality()!=null?a.getSubLocality():a.getLocality();
                            if(street!=null&&!street.trim().isEmpty()&&!street.matches("^[0-9A-Z+]{4,}$"))name=street.trim()+"|"+(area==null?"":area.trim());}
                    }catch(Exception ex){if(++n>3)break;continue;}
                    ed.putString(String.valueOf(e.getKey()),name);
                }
                ed.apply();
            }finally{running=false;new Handler(Looper.getMainLooper()).post(done);}
        },"KadammStreets").start();
    }
}
