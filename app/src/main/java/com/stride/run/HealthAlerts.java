package com.stride.run;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Health alerts: when a measure moves well outside your own normal, a notification says what changed and what to do
 * about it. Checked after each background sync (every 15 minutes). Each alert fires at most once a day, at most 3
 * alerts a day, never between 10 pm and 7 am, and only on fresh data (today's, or last night's for sleep measures).
 * Thresholds are against your own 30-day baseline where one exists, so they adapt to each person and watch.
 */
final class HealthAlerts {
    private HealthAlerts() {}
    static final String CHANNEL="kadamm_body";
    static final int BASE_ID=7440;

    static final class Alert { String key,title,text; Alert(String k,String t,String x){key=k;title=t;text=x;} }

    /** Every alert that applies right now (no rate limits), most important first. Also shown on the Health data page. */
    static List<Alert> current(Context c){
        List<Alert> out=new ArrayList<>();JSONObject v=Vitals.all(c);String today=DaySteps.todayKey();int h=Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        try{
            // Blood pressure: an urgent reading first.
            double[] bp=bloodPressureToday(c,v,today);
            if(bp!=null&&(bp[0]>=180||bp[1]>=120))out.add(new Alert("bp","Blood pressure "+Math.round(bp[0])+"/"+Math.round(bp[1])+" is very high","Sit quietly for 5 minutes and measure again. If it stays this high, or you have chest pain, shortness of breath or a headache, get medical help now."));
            else if(bp!=null&&(bp[0]>=140||bp[1]>=90))out.add(new Alert("bp","Blood pressure "+Math.round(bp[0])+"/"+Math.round(bp[1])+" is high","One reading isn't a diagnosis: measure again at rest tomorrow morning. If it stays at 140/90 or above, talk to a doctor. Less salt, regular walking and good sleep all help."));
            // Blood oxygen overnight.
            Pro.Reading lo=latestOf(v,"spo2Low",today);
            if(lo.ok()&&lo.value<90)out.add(new Alert("spo2",String.format(Locale.US,"Blood oxygen dipped to %.0f %% last night",lo.value),"A loose strap can cause this, so wear the watch snugly tonight. If it happens often, especially with snoring or daytime tiredness, talk to a doctor."));
            // Recovery.
            Pro.Recovery rec=Pro.recovery(c);
            if(rec.score>=0&&rec.score<34&&h>=7)out.add(new Alert("recovery","Recovery "+rec.score+" % (red): take it easy today","Your body hasn't bounced back yet. Keep walks gentle, drink water, skip hard efforts, and aim to be in bed by 10:30 pm. I've eased today's challenges."));
            // Resting heart rate up against your normal.
            Pro.Reading rh=Pro.restingHr(c);double[] rb=rh.ok()&&rh.day.equals(today)?Pro.baseline(Pro.restSeries(c,v),today,7,false):null;
            if(rb!=null&&rh.value>=rb[0]+7)out.add(new Alert("rhr",String.format(Locale.US,"Resting heart rate is up %.0f bpm",rh.value-rb[0]),String.format(Locale.US,"It's %.0f bpm against your usual %.0f. That often comes with illness, stress, alcohol or short sleep. Rest today and drink water; if it stays up for 3 days or you feel unwell, check with a doctor.",rh.value,rb[0])));
            // HRV well below your normal.
            Pro.Reading hv=Pro.hrv(c);double[] hb=hv.ok()&&hv.day.equals(today)?Pro.baseline(Pro.hrvSeries(c,v),today,7,true):null;
            if(hb!=null&&Math.log(hv.value)<=hb[0]-1.5*Math.max(.05,hb[1])&&hv.value<=.8*Math.exp(hb[0]))out.add(new Alert("hrv",String.format(Locale.US,"HRV is low today: %.0f ms",hv.value),String.format(Locale.US,"Your usual is %.0f ms. Low HRV means your body is under strain. Try 5 minutes of slow breathing (in for 4 seconds, out for 6), keep today light, and sleep early.",Math.exp(hb[0]))));
            // Breathing rate up.
            Pro.Reading rs=latestOf(v,"resp",today);double[] rsb=rs.ok()?Pro.baseline(Vitals.series(v,"resp"),today,7,false):null;
            if(rsb!=null&&rs.value>=rsb[0]+2)out.add(new Alert("resp",String.format(Locale.US,"Breathing rate is up: %.1f a minute",rs.value),String.format(Locale.US,"Your usual is %.1f. A rise like this can come before a cold. Rest, drink water and keep today easy.",rsb[0])));
            // Short sleep.
            SleepTracker.Night n=SleepTracker.night(c,today);
            if(n!=null&&!n.estimate&&n.asleep<360&&h>=8){int bed=((n.wakeMin()-8*60-30)%1440+1440)%1440;out.add(new Alert("sleep","Short sleep: "+Pro.hm(n.asleep),"Adults need 7–9 hours. To get 8 tonight, be in bed by "+SleepTracker.clock(bed)+", keep the evening screen-light, and skip caffeine after 2 pm."));}
            // Stress through the day.
            JSONObject st=Pro.stress(c,today);
            if(st!=null&&st.optInt("score")>=60&&h>=12)out.add(new Alert("stress","Stress has been high today ("+st.optInt("score")+")","Take a break: 5 minutes of slow breathing (in for 4 seconds, out for 6), or a 10-minute easy walk outside. Both bring heart rate and stress down."));
            // Load past what today's recovery suits.
            TrainerCoach.Day d=TrainerCoach.today(c,DaySteps.sessions(c));
            if(d.restNow&&d.recScore>=0)out.add(new Alert("load",String.format(Locale.US,"You've reached today's load (%s %.1f)",d.strainName.toLowerCase(Locale.US),d.strainNow),String.format(Locale.US,"With Recovery at %d %%, %.0f–%.0f suits today. More now adds fatigue more than fitness: rest, stretch, and sleep early.",d.recScore,d.strainLo,d.strainHi)));
        }catch(Exception ignored){}
        return out;
    }

    /** From the background sync: posts new alerts, within the limits. */
    static void notifyIfDue(Context c){
        try{
            if(!c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE).getBoolean("bodyAlerts",true))return;
            int h=Calendar.getInstance().get(Calendar.HOUR_OF_DAY);if(h<7||h>=22)return;
            SharedPreferences p=c.getSharedPreferences("kadamm_alerts",Context.MODE_PRIVATE);String today=DaySteps.todayKey();
            int sent=today.equals(p.getString("day",""))?p.getInt("sent",0):0;if(sent>=3)return;
            NotificationManager nm=c.getSystemService(NotificationManager.class);if(nm==null)return;
            if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Health alerts",NotificationManager.IMPORTANCE_DEFAULT));
            for(Alert a:current(c)){
                if(sent>=3)break;if(today.equals(p.getString("a_"+a.key,"")))continue;
                nm.notify(BASE_ID+Math.abs(a.key.hashCode()%20),Reminders.rich(c,CHANNEL,BASE_ID,a.title,a.text,"Body",false).build());
                sent++;p.edit().putString("a_"+a.key,today).putString("day",today).putInt("sent",sent).apply();
            }
        }catch(Exception ignored){}
    }

    private static Pro.Reading latestOf(JSONObject v,String key,String today){
        Pro.Reading r=new Pro.Reading();TreeMap<String,Double> s=Vitals.series(v,key);Double x=s.get(today);if(x!=null){r.value=x;r.day=today;}return r;
    }
    private static double[] bloodPressureToday(Context c,JSONObject v,String today){
        try{JSONObject m=new JSONObject(c.getSharedPreferences("kadamm_health",Context.MODE_PRIVATE).getString("bpManual","{}"));JSONArray a=m.optJSONArray(today);if(a!=null)return new double[]{a.getDouble(0),a.getDouble(1)};}catch(Exception ignored){}
        Map<String,Double> s=Vitals.series(v,"bpSys"),d=Vitals.series(v,"bpDia");Double sy=s.get(today),di=d.get(today);return sy!=null&&di!=null?new double[]{sy,di}:null;
    }
}
