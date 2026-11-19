package com.stride.run;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Kadamm coach: three challenges a day, fitted to you from your own last four weeks, and words that react to
 * what you actually did. Nothing here is guessed: every target and message comes from your saved steps, sessions,
 * brisk minutes and sitting.
 *
 * How it learns: the step target starts at about your 65th-percentile day of the last 28 (a good day for you,
 * not your best), the brisk target at your recent median plus 5 minutes. Each day all three are done, the next
 * day's targets rise 5 % (up to +25 %); each day under half are done, they ease 5 % (down to −15 %). Results are
 * remembered per day in prefs "kadamm_coach".
 */
final class TrainerCoach {
    private TrainerCoach() {}

    static final class Challenge {
        final String id,title;final double target,value;final String unit;final boolean done;
        Challenge(String id,String title,double target,double value,String unit,boolean done){this.id=id;this.title=title;this.target=target;this.value=value;this.unit=unit;this.done=done;}
        float fraction(){return target<=0?(done?1:0):(float)Math.max(0,Math.min(1,value/target));}
        String progress(){
            if(done)return "Done ✓";
            if("steps".equals(unit))return String.format(Locale.US,"%,d of %,d steps",Math.round(value),Math.round(target));
            if("min".equals(unit))return Math.round(value)+" of "+Math.round(target)+" min";
            return unit;
        }
    }
    static final class Day { final List<Challenge> list=new ArrayList<>();int done;String greeting,message,signoff;boolean allDone;double level; }

    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("kadamm_coach",Context.MODE_PRIVATE);}
    private static String name(Context c){LocalAccount.Account a=LocalAccount.current(c);if(a==null||a.name==null)return "";String n=a.name.trim();int sp=n.indexOf(' ');return sp>0?n.substring(0,sp):n;}

    /** The difficulty level for today: 1.0 normally, up after fully completed days, down after weak ones. */
    static double level(Context c){
        SharedPreferences p=prefs(c);double lv=1;Calendar d=Calendar.getInstance();
        // Walk back over the last 10 days in order, oldest first.
        List<String> keys=new ArrayList<>();for(int i=10;i>=1;i--){Calendar x=(Calendar)d.clone();x.add(Calendar.DAY_OF_YEAR,-i);keys.add(DaySteps.key(x.getTimeInMillis()));}
        for(String k:keys){int r=p.getInt("done_"+k,-1);if(r<0)continue;if(r>=3)lv+=.05;else if(r<=1)lv-=.05;lv=Math.max(.85,Math.min(1.25,lv));}
        return lv;
    }

    static Day today(Context c,JSONArray hist){
        Day day=new Day();Map<String,Long> totals=DaySteps.allDayTotals(c);String today=DaySteps.todayKey();SharedPreferences set=c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE);
        int goal=set.getInt("stepGoal",10000);day.level=level(c);
        // Step target: about your 65th-percentile day of the last 28, never under your goal, rounded to 500.
        List<Long> last=new ArrayList<>();Calendar d=Calendar.getInstance();for(int i=0;i<28;i++){d.add(Calendar.DAY_OF_YEAR,-1);Long v=totals.get(DaySteps.key(d.getTimeInMillis()));if(v!=null&&v>0)last.add(v);}
        long[] arr=new long[last.size()];for(int i=0;i<arr.length;i++)arr[i]=last.get(i);Arrays.sort(arr);
        double base=arr.length>=5?arr[(int)(arr.length*.65)]:goal;
        double stepTarget=Math.max(goal,Math.round(base*day.level/500.0)*500.0);stepTarget=Math.min(stepTarget,goal*1.8);
        Long tv=totals.get(today);double steps=tv==null?0:tv;
        // Brisk target: your recent median brisk minutes plus 5, at least 20.
        JSONObject md=DaySteps.minuteDays(c);List<Double> br=new ArrayList<>();Calendar b=Calendar.getInstance();for(int i=0;i<14;i++){b.add(Calendar.DAY_OF_YEAR,-1);JSONObject x=md.optJSONObject(DaySteps.key(b.getTimeInMillis()));if(x!=null&&x.has("brisk"))br.add(x.optDouble("brisk"));}
        double brMed=30;if(br.size()>=3){double[] a=new double[br.size()];for(int i=0;i<a.length;i++)a[i]=br.get(i);Arrays.sort(a);brMed=a[a.length/2];}
        double briskTarget=Math.max(20,Math.round((brMed+5)*day.level/5.0)*5.0);double brisk=GoalCapsule.briskToday(c);
        // A fresh set every day: one volume challenge and two others, drawn from a pool so no challenge repeats from
        // yesterday. The day's pick is saved, so it stays the same all day.
        Calendar now=Calendar.getInstance();int minute=now.get(Calendar.HOUR_OF_DAY)*60+now.get(Calendar.MINUTE);
        long longestToday=0;boolean early=false,evening=false;int walks=0;double bestCad=0;
        for(int i=0;i<hist.length();i++){JSONObject o=hist.optJSONObject(i);if(o==null||!today.equals(o.optString("day",""))||!DaySummary.counts(o))continue;walks++;longestToday=Math.max(longestToday,o.optLong("durationMs")/60000);
            Calendar st=Calendar.getInstance();st.setTimeInMillis(o.optLong("startTimeMs",o.optLong("id")));int hr=st.get(Calendar.HOUR_OF_DAY);if(hr<9)early=true;if(hr>=18)evening=true;
            if(SessionMetrics.ready()&&o.optLong("durationMs")>=10*60000){SessionMetrics m=SessionMetrics.of(c,o);bestCad=Math.max(bestCad,m.cadenceAvg);}}
        SharedPreferences cp=prefs(c);Calendar y=Calendar.getInstance();y.add(Calendar.DAY_OF_YEAR,-1);String yKey=DaySteps.key(y.getTimeInMillis());
        String picked=cp.getString("set_"+today,"");
        if(picked.isEmpty()){
            List<String> yest=Arrays.asList(cp.getString("set_"+yKey,"").split(","));java.util.Random rnd=new java.util.Random(today.hashCode()*31L+7);
            List<String> vol=new ArrayList<>(Arrays.asList("steps","km","beat"));Long yv0=totals.get(yKey);if(yv0==null||yv0<1000)vol.remove("beat");
            List<String> rest=new ArrayList<>(Arrays.asList("brisk","sit","long","early","evening","twice","hours","cadence"));if(minute>=8*60+30)rest.remove("early");if(minute>=19*60)rest.remove("evening");
            vol.removeAll(yest);if(vol.isEmpty())vol.add("steps");List<String> fresh=new ArrayList<>(rest);fresh.removeAll(yest);if(fresh.size()<2)fresh=rest;
            java.util.Collections.shuffle(vol,rnd);java.util.Collections.shuffle(fresh,rnd);picked=vol.get(0)+","+fresh.get(0)+","+fresh.get(1);
            cp.edit().putString("set_"+today,picked).apply();
        }
        for(String id:picked.split(",")){
            switch(id){
                case "steps":day.list.add(new Challenge("steps",String.format(Locale.US,"Walk %,d steps",Math.round(stepTarget)),stepTarget,steps,"steps",steps>=stepTarget));break;
                case "km":{double kmT=Math.max(3,Math.round(stepTarget*DaySteps.stepKm(c,1)*2)/2.0),km=DaySteps.distanceToday(c)[0];
                    day.list.add(new Challenge("km",String.format(Locale.US,"Cover %.1f km",kmT),kmT,km,String.format(Locale.US,"%.1f of %.1f km",km,kmT),km>=kmT));break;}
                case "beat":{Long yv=totals.get(yKey);double t=Math.round((yv==null?goal:yv)*1.05/100.0)*100.0;
                    day.list.add(new Challenge("beat",String.format(Locale.US,"Beat yesterday: %,d steps",Math.round(t)),t,steps,"steps",steps>=t));break;}
                case "brisk":day.list.add(new Challenge("brisk",Math.round(briskTarget)+" brisk minutes",briskTarget,brisk,"min",brisk>=briskTarget));break;
                case "sit":{int over=0;for(int[] x:DaySteps.sitsToday(c))if(x[1]>=60)over++;long still=DaySteps.stillMinutes(c);boolean bad=over>0||still>=60&&minute>=420&&minute<1380;
                    day.list.add(new Challenge("sit","No sit longer than an hour",0,bad?1:0,bad?"A sit went past an hour today":minute>=21*60?"Done ✓":"On track so far",!bad&&minute>=21*60));break;}
                case "long":{int t=(int)Math.max(30,Math.round(35*day.level/5.0)*5);day.list.add(new Challenge("long","One walk of "+t+"+ minutes",t,longestToday,"min",longestToday>=t));break;}
                case "early":day.list.add(new Challenge("early","A walk before 9 am",0,early?1:0,early?"Done ✓":minute>=9*60?"Missed today; try tomorrow morning":"Before 9 am",early));break;
                case "evening":day.list.add(new Challenge("evening","An evening walk after 6 pm",0,evening?1:0,evening?"Done ✓":"After 6 pm",evening));break;
                case "twice":day.list.add(new Challenge("twice","Two separate walks",2,walks,walks+" of 2 walks",walks>=2));break;
                case "hours":{double[] hh=DaySteps.hoursOn(c,today);int act=0;if(hh!=null)for(double v:hh)if(v>=250)act++;int t=(int)Math.round(8*day.level);
                    day.list.add(new Challenge("hours","Move in "+t+" different hours",t,act,act+" of "+t+" hours (250+ steps)",act>=t));break;}
                case "cadence":{int t=110;day.list.add(new Challenge("cadence","A 10-min walk at "+t+"+ spm",t,bestCad,bestCad>0?Math.round(bestCad)+" spm best so far":"Quick, light steps",bestCad>=t));break;}
            }
        }
        for(Challenge ch:day.list)if(ch.done)day.done++;day.allDone=day.done==day.list.size();
        prefs(c).edit().putInt("done_"+today,day.done).apply();
        words(c,day,totals,steps,brisk,minute);
        return day;
    }

    /** The coach's words, built from the numbers. */
    private static void words(Context c,Day day,Map<String,Long> totals,double steps,double brisk,int minute){
        String n=name(c);String hi=minute<12*60?"Good morning":minute<17*60?"Good afternoon":"Good evening";
        Calendar y=Calendar.getInstance();y.add(Calendar.DAY_OF_YEAR,-1);Long yv=totals.get(DaySteps.key(y.getTimeInMillis()));
        int yDone=prefs(c).getInt("done_"+DaySteps.key(y.getTimeInMillis()),-1);
        day.greeting=hi+(n.isEmpty()?"":", "+n)+".";
        StringBuilder m=new StringBuilder();
        if(day.allDone){
            m.append("I'm really happy with today's effort. All three challenges are done");
            m.append(String.format(Locale.US,", with %,d steps on the day. That's exactly how fitness is built, one good day at a time.",Math.round(steps)));
            day.signoff="Rest well. Tomorrow I'll raise the bar a little, because you've earned it.";
        }else{
            if(yv!=null&&yv>0&&minute<12*60)m.append(String.format(Locale.US,"Yesterday you walked %,d steps%s. ",yv,yDone>=3?" and finished every challenge, brilliant":yDone==2?" and finished two of three challenges":""));
            Challenge next=null;for(Challenge ch:day.list)if(!ch.done&&(next==null||ch.fraction()>next.fraction()))next=ch;
            if(day.done==0)m.append("Here are today's three challenges, set from your last four weeks. ");
            else m.append(day.done==1?"One challenge down, two to go. ":"Two done, one to go. ");
            if(next!=null){
                if("steps".equals(next.id)){long left=Math.round(next.target-next.value);m.append(String.format(Locale.US,"%,d steps left for the step challenge: about a %d-minute walk.",left,Math.max(5,Math.round(left/100.0))));}
                else if("brisk".equals(next.id))m.append(Math.round(next.target-next.value)+" more brisk minutes: walk at a pace where talking takes a little effort.");
                else if("long".equals(next.id))m.append("One unbroken walk of "+Math.round(next.target)+" minutes finishes it.");
                else if("early".equals(next.id))m.append("A short walk before 9 am finishes it.");
                else if("km".equals(next.id))m.append(String.format(Locale.US,"%.1f km to go for the distance challenge.",Math.max(.1,next.target-next.value)));
                else if("beat".equals(next.id))m.append(String.format(Locale.US,"%,d more steps and you've beaten yesterday.",Math.round(Math.max(0,next.target-next.value))));
                else if("evening".equals(next.id))m.append("A walk after 6 pm finishes it: a good way to wind down.");
                else if("twice".equals(next.id))m.append("Split your walking: one more separate walk finishes it.");
                else if("hours".equals(next.id))m.append("Move a little every hour: 250 steps in an hour counts it.");
                else if("cadence".equals(next.id))m.append("Walk 10 minutes with quick, light steps, about 110 a minute.");
                else m.append("Stand up and walk for two minutes every time you've been still for close to an hour.");
            }
            day.signoff=day.level>1.02?String.format(Locale.US,"Your targets are %d%% above your usual, because you've been on a roll.",Math.round((day.level-1)*100)):day.level<.98?"I've eased today's targets a little; a steady day gets you back on track.":"You've got this.";
        }
        day.message=m.toString().trim();
    }

    // ---- Report: patterns in the last four weeks ----
    static final class Finding { final String title,value,verdict;final int tone; Finding(String t,String v,String verdict,int tone){title=t;value=v;this.verdict=verdict;this.tone=tone;} }
    /** tone: 1 good, 0 neutral, -1 needs attention. */
    static List<Finding> report(Context c,JSONArray hist){
        List<Finding> out=new ArrayList<>();Map<String,Long> totals=DaySteps.allDayTotals(c);int goal=c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE).getInt("stepGoal",10000);
        double[] days=new double[28];int n=0;Calendar d=Calendar.getInstance();for(int i=0;i<28;i++){d.add(Calendar.DAY_OF_YEAR,-1);Long v=totals.get(DaySteps.key(d.getTimeInMillis()));days[i]=v==null?0:v;if(v!=null&&v>0)n++;}
        if(n<5)return out;
        double a7=0,a21=0;for(int i=0;i<7;i++)a7+=days[i];for(int i=7;i<28;i++)a21+=days[i];a7/=7;a21/=21;
        if(a21>0){double ch=(a7-a21)/a21*100;out.add(new Finding("Step trend",String.format(Locale.US,"%,d a day (%+.0f%%)",Math.round(a7),ch),ch>=5?"This week is above your previous three weeks. Keep the momentum.":ch<=-10?"This week is below your usual; one longer walk would close most of the gap.":"Holding steady against your previous three weeks.",ch>=5?1:ch<=-10?-1:0));}
        int goalDays=0;double sum=0,sq=0;for(double v:days){if(v>=goal)goalDays++;sum+=v;}double mean=sum/28;for(double v:days)sq+=(v-mean)*(v-mean);double cv=mean>0?Math.sqrt(sq/28)/mean*100:0;
        out.add(new Finding("Consistency",goalDays+" of 28 days at goal",goalDays>=20?"Very consistent: most days reach your goal.":goalDays>=12?"Good, with room to add a few more goal days.":"Fewer than half of days reach the goal; aim for steady days over big ones.",goalDays>=20?1:goalDays>=12?0:-1));
        out.add(new Finding("Day-to-day swing",String.format(Locale.US,"%.0f%% variation",cv),cv<30?"Your days are even, which is easier on the body than big swings.":"Big days and quiet days alternate; evening them out helps recovery.",cv<30?1:0));
        // Weekday pattern
        double[] wd=new double[7];int[] wn=new int[7];Calendar w=Calendar.getInstance();for(int i=0;i<28;i++){w.add(Calendar.DAY_OF_YEAR,-1);int k=(w.get(Calendar.DAY_OF_WEEK)+5)%7;if(days[i]>0){wd[k]+=days[i];wn[k]++;}}
        int best=0,worst=0;for(int k=0;k<7;k++){if(wn[k]==0)continue;double v=wd[k]/wn[k];if(wn[best]==0||v>wd[best]/wn[best])best=k;if(wn[worst]==0||v<wd[worst]/wn[worst])worst=k;}
        String[] names={"Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday"};
        if(wn[best]>0&&wn[worst]>0&&best!=worst)out.add(new Finding("Weekly rhythm",names[best]+" strongest",String.format(Locale.US,"%ss average %,d steps; %ss %,d. Plan a walk on %ss.",names[best],Math.round(wd[best]/wn[best]),names[worst],Math.round(wd[worst]/wn[worst]),names[worst]),0));
        // Best time of day (hourly records)
        double[] hrs=new double[24];JSONObject md=DaySteps.minuteDays(c);Calendar h=Calendar.getInstance();
        for(int i=0;i<28;i++){h.add(Calendar.DAY_OF_YEAR,-1);String k=DaySteps.key(h.getTimeInMillis());double[] own=DaySteps.hoursOn(c,k);JSONObject x=md.optJSONObject(k);JSONArray hh=x==null?null:x.optJSONArray("h");
            for(int j=0;j<24;j++)hrs[j]+=own!=null?own[j]:hh!=null?hh.optDouble(j):0;}
        int ph=0;double ht=0;for(int j=0;j<24;j++){ht+=hrs[j];if(hrs[j]>hrs[ph])ph=j;}
        if(ht>0){double eve=0;for(int j=18;j<24;j++)eve+=hrs[j];out.add(new Finding("When you move",MetricViews.DotDay.hourName(ph),eve/ht>.45?"Most walking is in the evening; a morning walk spreads the benefit and helps sleep.":"Your walking is spread through the day. Good.",eve/ht>.45?0:1));}
        // Speed and cadence trend: last 14 days against the 14 before
        double[] sp=new double[4];double[] cd=new double[4];String c14=DaySteps.key(System.currentTimeMillis()-14L*86400000L),c28=DaySteps.key(System.currentTimeMillis()-28L*86400000L);
        int easy=0,hard=0;
        for(int i=0;i<hist.length();i++){JSONObject o=hist.optJSONObject(i);if(o==null||!DaySummary.counts(o))continue;String k=o.optString("day","");if(k.compareTo(c28)<0)continue;SessionMetrics m=SessionMetrics.of(c,o);int idx=k.compareTo(c14)>=0?0:2;
            if(m.walkingSpeed()>0&&!m.estimatedDistance){sp[idx]+=m.walkingSpeed()*m.movingS;sp[idx+1]+=m.movingS;}if(m.cadenceAvg>0){cd[idx]+=m.cadenceAvg*m.movingS;cd[idx+1]+=m.movingS;}
            int dz=m.dominantZone();if(dz>=0){if(dz>=3)hard++;else easy++;}}
        if(sp[1]>0&&sp[3]>0){double now=sp[0]/sp[1],before=sp[2]/sp[3];out.add(new Finding("Walking speed",String.format(Locale.US,"%.2f m/s (%+.2f)",now,now-before),now>before+.02?"You're walking faster than two weeks ago: a sign of growing fitness.":now<before-.02?"A little slower than two weeks ago; a brisk session or two will lift it.":"Steady speed.",now>before+.02?1:now<before-.02?-1:0));}
        if(cd[1]>0&&cd[3]>0){double now=cd[0]/cd[1],before=cd[2]/cd[3];out.add(new Finding("Cadence",String.format(Locale.US,"%.0f spm (%+.0f)",now,now-before),now>=110?"Brisk cadence: quick, light steps.":"Aim for 110+ steps a minute on brisk walks.",now>=110?1:0));}
        if(easy+hard>=4)out.add(new Finding("Effort mix",hard+" hard · "+easy+" easy sessions",hard==0?"All your sessions are easy; one brisk or interval walk a week builds fitness faster.":hard>easy?"More hard than easy sessions; balance with easy days to recover.":"A healthy mix of easy and harder sessions.",hard==0?-1:hard>easy?0:1));
        // Challenges kept
        SharedPreferences p=prefs(c);int done=0,full=0,daysTracked=0;Calendar q=Calendar.getInstance();for(int i=0;i<14;i++){q.add(Calendar.DAY_OF_YEAR,-1);int r=p.getInt("done_"+DaySteps.key(q.getTimeInMillis()),-1);if(r<0)continue;daysTracked++;done+=r;if(r>=3)full++;}
        if(daysTracked>0)out.add(new Finding("Challenges",full+" perfect days of "+daysTracked,done*100/Math.max(1,daysTracked*3)+"% of challenges completed over the last two weeks.",full*2>=daysTracked?1:0));
        return out;
    }
    // ---- Notifications: today's challenges in the morning, and a thank-you when all three are done ----
    static void notifyIfDue(Context c){
        try{
            SharedPreferences p=prefs(c);String today=DaySteps.todayKey();int h=Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            if(!c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE).getBoolean("notifyCoach",true))return;
            Day d=today(c,DaySteps.sessions(c));
            if(d.allDone&&!today.equals(p.getString("doneNotified",""))){p.edit().putString("doneNotified",today).putString("morningNotified",today).apply();
                post(c,"Coach Kadamm: all 3 challenges done 🎉","I'm really happy with all your efforts today. "+d.signoff);return;}
            if(h>=7&&h<11&&d.done<d.list.size()&&!today.equals(p.getString("morningNotified",""))){p.edit().putString("morningNotified",today).apply();
                StringBuilder b=new StringBuilder();for(Challenge ch:d.list)b.append(b.length()>0?" · ":"").append(ch.title);
                post(c,d.greeting+" Today's challenges",b.toString());}
        }catch(Exception ignored){}
    }
    private static void post(Context c,String title,String text){
        android.app.NotificationManager nm=c.getSystemService(android.app.NotificationManager.class);if(nm==null)return;
        if(android.os.Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new android.app.NotificationChannel("kadamm_coach","Coach",android.app.NotificationManager.IMPORTANCE_DEFAULT));
        nm.notify(7420,Reminders.rich(c,"kadamm_coach",7420,title,text,"Trainer",true).build());
    }
}
