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
            // Recovery (tonight's bedtime from your sleep need and usual wake time).
            SleepTracker.Consistency cs0=SleepTracker.consistency(c);Pro.SleepNeed tn0=Pro.sleepNeed(c,DaySteps.sessions(c),DaySteps.key(System.currentTimeMillis()+86400000L));
            String bed0=SleepTracker.clock((((cs0.score>=0?cs0.avgWake:7*60)-tn0.total-15)%1440+1440)%1440);
            Pro.Recovery rec=Pro.recovery(c);
            if(rec.score>=0&&rec.score<34&&h>=7)out.add(new Alert("recovery","Recovery "+rec.score+" % (red): take it easy today","Your body hasn't bounced back yet. Keep walks gentle, drink water, skip hard efforts, and be in bed by "+bed0+" for the "+Pro.hm(tn0.total)+" of sleep you need. I've eased today's challenges."));
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
            // Sleep: a short night against your own need, and bedtimes that drift.
            SleepTracker.Night n=SleepTracker.night(c,today);SleepTracker.Consistency cs=SleepTracker.consistency(c);
            Pro.SleepNeed tonight=Pro.sleepNeed(c,DaySteps.sessions(c),DaySteps.key(System.currentTimeMillis()+86400000L));
            int wake=cs.score>=0?cs.avgWake:n!=null?n.wakeMin():7*60;int bedMin=((wake-tonight.total-15)%1440+1440)%1440;String bed=SleepTracker.clock(bedMin);
            if(n!=null&&n.asleep>=180&&h>=7){int need=Pro.sleepNeed(c,DaySteps.sessions(c),today).total;
                if(n.asleep<need-60)out.add(new Alert("sleep","Short sleep: "+Pro.hm(n.asleep)+" of "+Pro.hm(need),"Tonight you need "+Pro.hm(tonight.total)+": be in bed by "+bed+", keep the last hour screen-light, and skip caffeine after 2 pm. A 20-minute nap before 3 pm also helps."));}
            if(cs.score>=0&&cs.score<50&&h>=9)out.add(new Alert("consistency_"+DaySteps.key(monday()),"Your sleep times vary a lot ("+cs.score+" % consistency)",String.format(Locale.US,"Bedtime moves by about %.0f minutes and wake time by %.0f. A steady schedule is one of the best things for recovery: aim for bed by %s and the same wake time every day, weekends too.",cs.sdBed,cs.sdWake,bed)));
            // Stress through the day.
            JSONObject st=Pro.stress(c,today);
            if(st!=null&&st.optInt("score")>=60&&h>=12)out.add(new Alert("stress","Stress has been high today ("+st.optInt("score")+")","Take a break: 5 minutes of slow breathing (in for 4 seconds, out for 6), or a 10-minute easy walk outside. Both bring heart rate and stress down."));
            // Load past what today's recovery suits.
            TrainerCoach.Day d=TrainerCoach.today(c,DaySteps.sessions(c));
            if(d.restNow&&d.recScore>=0)out.add(new Alert("load",String.format(Locale.US,"You've reached today's load (%s %.1f)",d.strainName.toLowerCase(Locale.US),d.strainNow),String.format(Locale.US,"With Recovery at %d %%, %.0f–%.0f suits today. More now adds fatigue more than fitness: rest, stretch, and sleep early.",d.recScore,d.strainLo,d.strainHi)));
            // Wind-down reminder, 45 minutes before tonight's bedtime (only when your usual times are known).
            int now=Calendar.getInstance().get(Calendar.HOUR_OF_DAY)*60+Calendar.getInstance().get(Calendar.MINUTE);int lead=((bedMin-now)%1440+1440)%1440;
            if(cs.score>=0&&lead<=45&&now>=19*60)out.add(new Alert("bedtime","Time to wind down","To get the "+Pro.hm(tonight.total)+" your body needs tonight, be in bed by "+bed+". Dim the lights and put the phone away."));
        }catch(Exception ignored){}
        return out;
    }
    /** Each kind of alert has its own notification, so one never replaces another. */
    static int id(String key){String[] k={"bp","spo2","recovery","rhr","hrv","resp","sleep","consistency","stress","load","bedtime"};for(int i=0;i<k.length;i++)if(key.startsWith(k[i]))return BASE_ID+i;return BASE_ID+19;}
    static long monday(){Calendar d=Calendar.getInstance();d.add(Calendar.DAY_OF_YEAR,-((d.get(Calendar.DAY_OF_WEEK)+5)%7));return d.getTimeInMillis();}

    /** From the background sync: posts new alerts, within the limits. */
    static void notifyIfDue(Context c){
        {int hh=Calendar.getInstance().get(Calendar.HOUR_OF_DAY);if(hh>=9&&hh<21)sinceReport(c);}
        try{
            if(!c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE).getBoolean("bodyAlerts",true))return;
            int h=Calendar.getInstance().get(Calendar.HOUR_OF_DAY),mins=h*60+Calendar.getInstance().get(Calendar.MINUTE);if(h<7||mins>=23*60+30)return;
            SharedPreferences p=c.getSharedPreferences("kadamm_alerts",Context.MODE_PRIVATE);String today=DaySteps.todayKey();
            int sent=today.equals(p.getString("day",""))?p.getInt("sent",0):0;if(sent>=3)return;
            NotificationManager nm=c.getSystemService(NotificationManager.class);if(nm==null)return;
            if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Health alerts",NotificationManager.IMPORTANCE_DEFAULT));
            for(Alert a:current(c)){
                if(sent>=3)break;if(today.equals(p.getString("a_"+a.key,"")))continue;
                // After 10 pm only the wind-down reminder; a weekly alert is keyed by its week, so once a week.
                if(h>=22&&!a.key.equals("bedtime"))continue;if(a.key.startsWith("consistency_")&&p.contains("a_"+a.key))continue;
                int id=id(a.key);nm.notify(id,Reminders.rich(c,CHANNEL,id,a.title,a.text,"Body",false).build());
                sent++;p.edit().putString("a_"+a.key,today).putString("day",today).putInt("sent",sent).apply();
            }
        }catch(Exception ignored){}
    }

    // ---- Health reports ----
    static final String LAB_CHANNEL="kadamm_reports";static final int LAB_SAVED=7470,LAB_SINCE=7471;
    private static boolean labOn(Context c){return c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE).getBoolean("labNotes",true);}
    private static NotificationManager labChannel(Context c){NotificationManager nm=c.getSystemService(NotificationManager.class);if(nm!=null&&Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(LAB_CHANNEL,"Lab result updates",NotificationManager.IMPORTANCE_DEFAULT));return nm;}
    /** After saving a report: how its tests compare with the report before (toward or away from the range). */
    static void reportSaved(Context c,String date){
        try{if(!labOn(c))return;java.util.List<JSONObject> reps=HealthReports.all(c);java.util.List<HealthReports.Marker> mk=HealthReports.markers(c,reps);
            java.util.List<String> good=new ArrayList<>(),bad=new ArrayList<>();int in=0,out=0;
            for(HealthReports.Marker m:mk){HealthReports.Point p=m.last();if(!p.date.equals(date))continue;int st=HealthReports.status(p);if(st==0)in++;else if(st!=2)out++;
                int d=HealthReports.direction(m.prev(),p);if(d!=0){String t=m.name+" "+HealthReports.fmt(m.prev().v)+" → "+HealthReports.fmt(p.v);(d>0?good:bad).add(t);}}
            String title=out==0?"Lab results saved: every test in range":String.format(Locale.US,"Lab results saved: %d of %d tests in range",in,in+out);
            StringBuilder b=new StringBuilder();if(!good.isEmpty())b.append("Better: ").append(String.join(", ",good)).append(". ");if(!bad.isEmpty())b.append("Moved away from the range: ").append(String.join(", ",bad)).append(". ");
            if(b.length()==0)b.append("Open Lab results to see each test and what moved with it.");
            NotificationManager nm=labChannel(c);if(nm!=null)nm.notify(LAB_SAVED,Reminders.rich(c,LAB_CHANNEL,LAB_SAVED,title,b.toString().trim(),"Labs",false).build());
        }catch(Exception ignored){}
    }
    /**
     * Once a week from the background sync, when measures have clearly changed since your latest report: the good
     * changes and the ones to watch, so you know what your next test may reflect.
     */
    static void sinceReport(Context c){
        try{if(!labOn(c))return;SharedPreferences p=c.getSharedPreferences("kadamm_alerts",Context.MODE_PRIVATE);if(System.currentTimeMillis()-p.getLong("labSinceAt",0)<7L*86400000L)return;
            java.util.List<JSONObject> reps=HealthReports.all(c);if(reps.isEmpty())return;String ld=reps.get(reps.size()-1).optString("date");
            java.util.List<HealthReports.Change> ch=HealthReports.since(c,DaySteps.sessions(c));if(ch.isEmpty())return;
            StringBuilder g=new StringBuilder(),b=new StringBuilder();for(HealthReports.Change x:ch){if(x.tone==0)continue;StringBuilder t=x.tone>0?g:b;if(t.length()>0)t.append(", ");t.append(HealthReports.changeText(x));}
            if(g.length()==0&&b.length()==0)return;
            String nice;try{nice=new java.text.SimpleDateFormat("d MMM",Locale.getDefault()).format(new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(ld));}catch(Exception e){nice=ld;}
            String text=(g.length()>0?"Going well: "+g+". ":"")+(b.length()>0?"Worth watching: "+b+".":"");
            NotificationManager nm=labChannel(c);if(nm==null)return;
            nm.notify(LAB_SINCE,Reminders.rich(c,LAB_CHANNEL,LAB_SINCE,"Since your "+nice+" lab results",text.trim(),"Labs",false).build());
            p.edit().putLong("labSinceAt",System.currentTimeMillis()).apply();
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
