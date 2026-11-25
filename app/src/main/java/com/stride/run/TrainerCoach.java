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
    static final class Day { final List<Challenge> list=new ArrayList<>();int done;String greeting,message,signoff;boolean allDone,recovery,restNow;int recScore=-1;double strainNow=-1,strainLo=-1,strainHi=-1;String strainName="";double level,yEffort,usualEffort; }

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
        // Recovery: after a day with more than twice your usual effort, today is eased and kept gentle.
        double[] eff=efforts(c,hist);day.yEffort=eff[0];day.usualEffort=eff[1];day.recovery=eff[0]>=60&&eff[1]>0&&eff[0]>2*eff[1];
        // A red Recovery score (watch HRV, resting heart rate and sleep) also makes today an easy day.
        try{Pro.Recovery rec=Pro.recovery(c);day.recScore=rec.score;if(rec.score>=0&&rec.score<34)day.recovery=true;}catch(Exception ignored){}
        // Green Recovery: ready to push, so targets rise 5 %.
        if(!day.recovery&&day.recScore>=67){day.level=Math.min(1.25,day.level*1.05);stepTarget=Math.min(goal*1.8,Math.max(goal,Math.round(base*day.level/500.0)*500.0));briskTarget+=5;}
        // How much load suits today's recovery (WHOOP's guidance): green 14–18, yellow 10–14, red 5–9, on the 0–21 scale
        // of Strain (heart rate) or, without a watch, Movement load (steps).
        try{Pro.Strain stn=Pro.strain(c,today);if(stn.strain>=0){day.strainNow=stn.strain;day.strainName="Strain";}else{Pro.Load ld=Pro.load(c,hist,today);if(ld.load>=0){day.strainNow=ld.load;day.strainName="Movement load";}}
            if(day.recScore>=0){day.strainLo=day.recScore>=67?14:day.recScore>=34?10:5;day.strainHi=day.recScore>=67?18:day.recScore>=34?14:9;day.restNow=day.strainNow>=day.strainHi;}}catch(Exception ignored){}
        if(day.recovery){day.level=Math.min(day.level,.9);stepTarget=Math.max(goal,Math.round(base*day.level/500.0)*500.0);briskTarget=Math.max(20,Math.round((brMed)*.8/5.0)*5.0);}
        String picked=cp.getString("set_"+today,"");
        if(picked.isEmpty()){
            List<String> yest=Arrays.asList(cp.getString("set_"+yKey,"").split(","));java.util.Random rnd=new java.util.Random(today.hashCode()*31L+7);
            List<String> vol=new ArrayList<>(Arrays.asList("steps","km","beat"));Long yv0=totals.get(yKey);if(yv0==null||yv0<1000)vol.remove("beat");
            List<String> rest=new ArrayList<>(Arrays.asList("brisk","sit","long","early","evening","twice","hours","cadence"));if(day.recovery){rest.remove("cadence");rest.remove("long");}if(minute>=8*60+30)rest.remove("early");if(minute>=19*60)rest.remove("evening");
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

    /**
     * The coach's sleep advice for today, when sleep is the thing to work on: a short night against your sleep need,
     * or bedtimes and wake times that vary a lot (consistency under 60 %). Empty otherwise.
     */
    static String sleepFocus(Context c){
        try{String today=DaySteps.todayKey();SleepTracker.Night n=SleepTracker.night(c,today);SleepTracker.Consistency cs=SleepTracker.consistency(c);
            Pro.SleepNeed tonight=Pro.sleepNeed(c,DaySteps.sessions(c),DaySteps.key(System.currentTimeMillis()+86400000L));
            String bed=cs.score>=0?SleepTracker.clock(((cs.avgWake-tonight.total-15)%1440+1440)%1440):null;
            if(n!=null&&n.asleep>=180){int need=Pro.sleepNeed(c,DaySteps.sessions(c),today).total;
                if(n.asleep<need-60)return String.format(Locale.US,"Focus on sleep: you slept %s, short of the %s your body needs. %s ",Pro.hm(n.asleep),Pro.hm(need),bed!=null?"Be in bed by "+bed+" tonight.":"Get to bed early tonight.");}
            if(cs.score>=0&&cs.score<60)return String.format(Locale.US,"Focus on sleep timing: your bedtime varies by about %.0f minutes and your wake time by %.0f (consistency %d %%). Going to bed within 30 minutes of the same time every night helps recovery more than an extra walk%s. ",cs.sdBed,cs.sdWake,cs.score,bed!=null?"; tonight, aim for "+bed:"");
        }catch(Exception ignored){}
        return "";
    }
    /** One line for the coach card: today's Recovery and how much load suits it, or null without a Recovery score. */
    static String guidance(Context c,Day d){
        if(d.recScore<0)return null;String zone=d.recScore>=67?"green":d.recScore>=34?"yellow":"red";
        String load=d.strainNow>=0?String.format(Locale.US," · %s %.1f, best today %.0f–%.0f",d.strainName,d.strainNow,d.strainLo,d.strainHi):String.format(Locale.US," · best load today %.0f–%.0f",d.strainLo,d.strainHi);
        Pro.Now nw=null;try{nw=Pro.recoveryNow(c);}catch(Exception ignored){}
        String now=nw!=null&&nw.now>=0&&nw.now!=d.recScore?String.format(Locale.US,", now %d %%",nw.now):"";
        return String.format(Locale.US,"Recovery %d %% (%s)%s%s%s",d.recScore,zone,now,load,d.restNow?" · rest now":"");
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
            if(day.recScore>=0&&(day.restNow||day.recScore<34))day.signoff=String.format(Locale.US,"Now rest. Your Recovery is %d %% and your %s is %.1f, %s. Skip extra walks today, drink water and get to bed early so tomorrow starts green.",day.recScore,day.strainName.toLowerCase(Locale.US),day.strainNow,day.restNow?String.format(Locale.US,"already past the %.0f–%.0f that suits it",day.strainLo,day.strainHi):"so your body needs the time to recover");
            else if(day.recScore>=67&&day.strainNow>=0&&day.strainNow<day.strainLo)day.signoff=String.format(Locale.US,"You're well recovered (%d %%). If you feel good, an easy 20–30 minute walk would bring your %s from %.1f into today's %.0f–%.0f range. Otherwise, rest well.",day.recScore,day.strainName.toLowerCase(Locale.US),day.strainNow,day.strainLo,day.strainHi);
        }else{
            if(day.restNow&&!(day.recScore<34))m.append(String.format(Locale.US,"Your %s is already %.1f, above the %.0f–%.0f that suits today's Recovery of %d %%. Finish the challenges gently, with no extra pace. ",day.strainName.toLowerCase(Locale.US),day.strainNow,day.strainLo,day.strainHi,day.recScore));
            else if(day.recScore>=67&&!day.recovery&&minute<14*60)m.append(String.format(Locale.US,"Your Recovery is %d %% (green): you're ready to push today, so I've added 5 %% to your targets. ",day.recScore));
            else if(day.recScore>=34&&day.recScore<67&&minute<14*60)m.append(String.format(Locale.US,"Your Recovery is %d %% (yellow): a normal day. Steady pace, nothing all-out. ",day.recScore));
            if(day.recovery&&day.recScore>=0&&day.recScore<34)m.append(String.format(Locale.US,"Your Recovery is %d %% (red): your body hasn't bounced back yet. Today is an easy day, so I've eased your targets. Keep the pace comfortable. ",day.recScore));
            else if(day.recovery)m.append(String.format(Locale.US,"Yesterday was a big day: effort %d, about %.1f× your usual. Today is an easy day, so I've eased your targets. Keep the pace comfortable. ",Math.round(day.yEffort),day.yEffort/day.usualEffort));
            else if(yv!=null&&yv>0&&minute<12*60)m.append(String.format(Locale.US,"Yesterday you walked %,d steps%s. ",yv,yDone>=3?" and finished every challenge, brilliant":yDone==2?" and finished two of three challenges":""));
            m.append(sleepFocus(c));
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
            int[] stk=DaySteps.streaks(totals,c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE).getInt("stepGoal",10000));int g0=c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE).getInt("stepGoal",10000);
            if(minute>=19*60&&stk[0]>=2&&steps<g0)m.append(String.format(Locale.US," Your %d-day streak needs %,d more steps tonight, about %d minutes of walking.",stk[0],Math.round(g0-steps),Math.max(5,Math.round((g0-steps)/100.0))));
            day.signoff=day.recovery&&day.recScore>=0&&day.recScore<34?"Gentle walking only today, and an early night: that's what turns Recovery green again.":day.recovery?"Easy movement today helps your body absorb yesterday's work.":day.recScore>=67&&day.level<1?"Your targets were eased after a quieter run of days; your green Recovery puts 5 % back, so today is a good day to catch up.":day.level>1.02?String.format(Locale.US,"Your targets are %d%% above your usual, because you've been on a roll.",Math.round((day.level-1)*100)):day.level<.98?"I've eased today's targets a little; a steady day gets you back on track.":"You've got this.";
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
    /** [yesterday's effort, your usual daily effort over the 28 days before]. */
    static double[] efforts(Context c,JSONArray hist){
        if(!SessionMetrics.ready())return new double[]{0,0};
        Calendar y=Calendar.getInstance();y.add(Calendar.DAY_OF_YEAR,-1);String yk=DaySteps.key(y.getTimeInMillis());y.add(Calendar.DAY_OF_YEAR,-28);String from=DaySteps.key(y.getTimeInMillis());
        double ye=0,sum=0;for(int i=0;i<hist.length();i++){JSONObject o=hist.optJSONObject(i);if(o==null||!DaySummary.counts(o))continue;String k=o.optString("day","");if(k.compareTo(from)<0||k.compareTo(yk)>0)continue;
            double e=SessionMetrics.of(c,o).effort;if(k.equals(yk))ye+=e;else sum+=e;}
        return new double[]{ye,sum/28};
    }

    // ---- Weekly challenges (Monday to Sunday) ----
    static final class Week { final List<Challenge> list=new ArrayList<>();int done,daysLeft,wins;boolean allDone;String key; }
    static Calendar monday(int weeksBack){Calendar m=Calendar.getInstance();int dow=(m.get(Calendar.DAY_OF_WEEK)+5)%7;m.add(Calendar.DAY_OF_YEAR,-dow-7*weeksBack);m.set(Calendar.HOUR_OF_DAY,12);return m;}
    /** Brisk minutes on a day: Health Connect's minute steps or that day's sessions, the larger. */
    static double briskOn(Context c,JSONArray hist,JSONObject md,String day){
        if(day.equals(DaySteps.todayKey()))return GoalCapsule.briskToday(c);
        JSONObject x=md.optJSONObject(day);double hc=x!=null?x.optDouble("brisk",0):0,ses=0;
        if(SessionMetrics.ready())for(int i=0;i<hist.length();i++){JSONObject o=hist.optJSONObject(i);if(o!=null&&day.equals(o.optString("day",""))&&DaySummary.counts(o)){SessionMetrics m=SessionMetrics.of(c,o);ses+=(m.zoneS[2]+m.zoneS[3]+m.zoneS[4])/60d;}}
        return Math.max(hc,ses);
    }
    static Week week(Context c,JSONArray hist){
        Week w=new Week();Calendar mon=monday(0);w.key=DaySteps.key(mon.getTimeInMillis());Map<String,Long> totals=DaySteps.allDayTotals(c);JSONObject md=DaySteps.minuteDays(c);SharedPreferences p=prefs(c);
        int goal=c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE).getInt("stepGoal",10000);String today=DaySteps.todayKey();
        long steps=0;int goalDays=0,perfect=0,walks=0;double brisk=0;Calendar d=(Calendar)mon.clone();
        for(int i=0;i<7;i++){String k=DaySteps.key(d.getTimeInMillis());if(k.compareTo(today)>0){w.daysLeft++;d.add(Calendar.DAY_OF_YEAR,1);continue;}
            Long v=totals.get(k);long n=v==null?0:v;steps+=n;if(n>=goal)goalDays++;if(p.getInt("done_"+k,0)>=3)perfect++;brisk+=briskOn(c,hist,md,k);d.add(Calendar.DAY_OF_YEAR,1);}
        w.daysLeft++; // today still counts
        for(int i=0;i<hist.length();i++){JSONObject o=hist.optJSONObject(i);if(o!=null&&DaySummary.counts(o)&&o.optString("day","").compareTo(w.key)>=0)walks++;}
        // Your usual week: the 4 weeks before this one.
        long prior=0;Calendar q=(Calendar)mon.clone();for(int i=0;i<28;i++){q.add(Calendar.DAY_OF_YEAR,-1);Long v=totals.get(DaySteps.key(q.getTimeInMillis()));if(v!=null)prior+=v;}
        double lv=level(c);double stepT=Math.max(goal*5L,Math.round(prior/4.0*1.05*lv/5000.0)*5000.0);
        String picked=p.getString("wk_"+w.key,"");
        if(picked.isEmpty()){List<String> pool=new ArrayList<>(Arrays.asList("wsteps","wgoal","wbrisk","wwalks","wperfect"));java.util.Collections.shuffle(pool,new java.util.Random(w.key.hashCode()*17L+3));
            List<String> last=Arrays.asList(p.getString("wk_"+DaySteps.key(monday(1).getTimeInMillis()),"").split(","));pool.sort((a,b)->Boolean.compare(last.contains(a),last.contains(b)));
            picked=pool.get(0)+","+pool.get(1)+","+pool.get(2);p.edit().putString("wk_"+w.key,picked).apply();}
        for(String id:picked.split(",")){
            switch(id){
                case "wsteps":w.list.add(new Challenge(id,String.format(Locale.US,"%,d steps this week",Math.round(stepT)),stepT,steps,"steps",steps>=stepT));break;
                case "wgoal":w.list.add(new Challenge(id,"Reach your goal on 5 days",5,goalDays,goalDays+" of 5 days",goalDays>=5));break;
                case "wbrisk":w.list.add(new Challenge(id,"150 brisk minutes (WHO)",150,brisk,"min",brisk>=150));break;
                case "wwalks":w.list.add(new Challenge(id,"5 walks this week",5,walks,walks+" of 5 walks",walks>=5));break;
                default:w.list.add(new Challenge(id,"2 perfect challenge days",2,perfect,perfect+" of 2 days",perfect>=2));
            }
        }
        for(Challenge ch:w.list)if(ch.done)w.done++;w.allDone=w.done==w.list.size();
        if(w.allDone&&!p.getBoolean("wkWon_"+w.key,false))p.edit().putBoolean("wkWon_"+w.key,true).apply();
        for(String k:p.getAll().keySet())if(k.startsWith("wkWon_"))w.wins++;
        return w;
    }

    // ---- The weekly review ----
    static final class Review { String title;final List<String> lines=new ArrayList<>();String focus; }
    /** weeksBack 0: this week so far (shown Sunday evening); 1: last week (shown Monday and Tuesday). */
    static Review review(Context c,JSONArray hist,int weeksBack){
        Review r=new Review();Map<String,Long> totals=DaySteps.allDayTotals(c);JSONObject md=DaySteps.minuteDays(c);SharedPreferences p=prefs(c);int goal=c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE).getInt("stepGoal",10000);
        Calendar mon=monday(weeksBack),prev=monday(weeksBack+1);String today=DaySteps.todayKey();
        long steps=0,prevSteps=0,best=0;String bestDay="";int goalDays=0,chDone=0,chDays=0,perfect=0;double brisk=0;String[] names={"Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday"};
        Calendar d=(Calendar)mon.clone(),e=(Calendar)prev.clone();
        for(int i=0;i<7;i++){String k=DaySteps.key(d.getTimeInMillis()),pk=DaySteps.key(e.getTimeInMillis());d.add(Calendar.DAY_OF_YEAR,1);e.add(Calendar.DAY_OF_YEAR,1);Long pv=totals.get(pk);if(pv!=null)prevSteps+=pv;
            if(k.compareTo(today)>0)continue;Long v=totals.get(k);long n=v==null?0:v;steps+=n;if(n>=goal)goalDays++;if(n>best){best=n;bestDay=names[i];}brisk+=briskOn(c,hist,md,k);
            int dn=p.getInt("done_"+k,-1);if(dn>=0){chDays++;chDone+=dn;if(dn>=3)perfect++;}}
        r.title=weeksBack==0?"Your week so far":"Last week in review";
        r.lines.add(String.format(Locale.US,"%,d steps%s",steps,prevSteps>0?String.format(Locale.US," (%+.0f%% on the week before)",(steps-prevSteps)*100.0/prevSteps):""));
        r.lines.add(goalDays+" of 7 days at your step goal");
        r.lines.add(Math.round(brisk)+" brisk minutes"+(brisk>=150?" ✓ WHO's weekly 150 met":" of WHO's weekly 150"));
        if(best>0)r.lines.add(String.format(Locale.US,"Best day: %s, %,d steps",bestDay,best));
        if(chDays>0)r.lines.add(chDone+" of "+chDays*3+" daily challenges done, "+perfect+" perfect "+(perfect==1?"day":"days"));
        if(p.getBoolean("wkWon_"+DaySteps.key(mon.getTimeInMillis()),false))r.lines.add("🏆 Weekly challenge won");
        r.focus=goalDays<4?"Next week's focus: reach your goal on at least 5 days. Steady days beat one big day.":brisk<150?"Next week's focus: add two brisk 20-minute walks to reach 150 brisk minutes.":prevSteps>0&&steps<prevSteps*.9?"Next week's focus: get back to your usual volume with one longer weekend walk.":"Next week's focus: keep this rhythm, and try one walk at a quicker cadence (110+ spm).";
        return r;
    }

    // ---- Friend challenges: a short code, shared any way you like; nothing goes to a server ----
    static final class Friend { String code,kind,name,from,to;long target;double value;boolean done,over; }
    /** A code like KDM-S70000-7-Vraj: S steps, B brisk minutes or D km, the target, the days, and who sent it. */
    static String makeCode(String kind,long target,int days,String name){String n=(name==null?"":name.trim().split(" ")[0]).replaceAll("[^A-Za-z0-9]","");return "KDM-"+kind+target+"-"+days+(n.isEmpty()?"":"-"+n);}
    static String join(Context c,String code){
        String t=code==null?"":code.trim().toUpperCase(Locale.US).replace(" ","");String[] parts=code==null?new String[0]:code.trim().split("-");
        if(!t.startsWith("KDM-")||parts.length<3)return "That doesn't look like a Kadamm code (KDM-…).";
        try{String kind=parts[1].substring(0,1).toUpperCase(Locale.US);long target=Long.parseLong(parts[1].substring(1));int days=Integer.parseInt(parts[2]);if(!"SBD".contains(kind)||target<=0||days<1||days>31)return "That code isn't valid.";
            JSONArray a=new JSONArray(prefs(c).getString("friends","[]"));Calendar e=Calendar.getInstance();e.add(Calendar.DAY_OF_YEAR,days-1);
            a.put(new JSONObject().put("code",code.trim()).put("kind",kind).put("target",target).put("name",parts.length>3?parts[3]:"A friend").put("from",DaySteps.todayKey()).put("to",DaySteps.key(e.getTimeInMillis())));
            prefs(c).edit().putString("friends",a.toString()).apply();return null;}catch(Exception ex){return "That code isn't valid.";}
    }
    static List<Friend> friends(Context c,JSONArray hist){
        List<Friend> out=new ArrayList<>();Map<String,Long> totals=DaySteps.allDayTotals(c);JSONObject md=DaySteps.minuteDays(c);String today=DaySteps.todayKey();
        try{JSONArray a=new JSONArray(prefs(c).getString("friends","[]"));for(int i=a.length()-1;i>=0;i--){JSONObject o=a.getJSONObject(i);Friend f=new Friend();f.code=o.optString("code");f.kind=o.optString("kind");f.name=o.optString("name");f.from=o.optString("from");f.to=o.optString("to");f.target=o.optLong("target");
            Calendar d=Calendar.getInstance();d.setTime(new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(f.from));
            for(int k=0;k<40;k++){String key=DaySteps.key(d.getTimeInMillis());if(key.compareTo(f.to)>0||key.compareTo(today)>0)break;Long v=totals.get(key);long n=v==null?0:v;
                f.value+="S".equals(f.kind)?n:"D".equals(f.kind)?DaySteps.stepKm(c,n):briskOn(c,hist,md,key);d.add(Calendar.DAY_OF_YEAR,1);}
            f.done=f.value>=f.target;f.over=today.compareTo(f.to)>0;out.add(f);}}catch(Exception ignored){}
        return out;
    }
    static void removeFriend(Context c,String code){try{JSONArray a=new JSONArray(prefs(c).getString("friends","[]")),b=new JSONArray();for(int i=0;i<a.length();i++)if(!code.equals(a.getJSONObject(i).optString("code")))b.put(a.get(i));prefs(c).edit().putString("friends",b.toString()).apply();}catch(Exception ignored){}}
    static String kindUnit(String k){return "S".equals(k)?"steps":"D".equals(k)?"km":"brisk min";}

    // ---- Notifications: today's challenges in the morning, and a thank-you when all three are done ----
    static void notifyIfDue(Context c){
        try{
            SharedPreferences p=prefs(c);String today=DaySteps.todayKey();int h=Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            if(!c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE).getBoolean("notifyCoach",true))return;
            Day d=today(c,DaySteps.sessions(c));
            if(d.allDone&&!today.equals(p.getString("doneNotified",""))){p.edit().putString("doneNotified",today).putString("morningNotified",today).apply();
                post(c,"Coach Kadamm: all 3 challenges done 🎉","I'm really happy with all your efforts today. "+d.signoff,7420);return;}
            Calendar now=Calendar.getInstance();int dow=now.get(Calendar.DAY_OF_WEEK);
            // Streak protection, 8 to 10:30 pm.
            int goal=c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE).getInt("stepGoal",10000);long steps=DaySteps.todayTotal(c);int[] stk=DaySteps.streaks(DaySteps.allDayTotals(c),goal);
            if(h>=20&&(h<22||h==22&&now.get(Calendar.MINUTE)<30)&&stk[0]>=2&&steps<goal&&!today.equals(p.getString("streakNotified",""))){p.edit().putString("streakNotified",today).apply();
                post(c,"Your "+stk[0]+"-day streak is at risk",String.format(Locale.US,"%,d steps to go: about %d minutes of walking keeps it alive.",goal-steps,Math.max(5,Math.round((goal-steps)/100.0))),7421);return;}
            // The weekly review, Sunday 7 to 10 pm.
            if(dow==Calendar.SUNDAY&&h>=19&&h<22&&!today.equals(p.getString("reviewNotified",""))){p.edit().putString("reviewNotified",today).apply();Review r=review(c,DaySteps.sessions(c),0);
                post(c,"Coach Kadamm: your week in review",r.lines.get(0)+" · "+r.lines.get(1)+". "+r.focus,7422);return;}
            // Almost there: a challenge 85 %+ done, once a day.
            if(h>=10&&h<22&&!d.allDone&&!today.equals(p.getString("almostNotified",""))){for(Challenge ch:d.list)if(!ch.done&&ch.target>0&&ch.fraction()>=.85f){p.edit().putString("almostNotified",today).apply();
                post(c,"So close: "+ch.title,ch.progress()+". One short walk finishes it.",7423);return;}}
            // Your usual walking hour went by quietly.
            int usual=usualHour(c);if(usual>=0&&h==usual+1&&!d.allDone&&!today.equals(p.getString("usualNotified",""))){double[] hh=DaySteps.hoursOn(c,today);double inHour=hh==null?0:hh[usual];
                if(inHour<usualAvg*.3){p.edit().putString("usualNotified",today).apply();post(c,"Time for your walk?","You usually walk around "+MetricViews.DotDay.hourName(usual)+". A short one now keeps today's challenges on track.",7424);return;}}
            if(h>=7&&h<11&&d.done<d.list.size()&&!today.equals(p.getString("morningNotified",""))){p.edit().putString("morningNotified",today).apply();
                StringBuilder b=new StringBuilder();for(Challenge ch:d.list)b.append(b.length()>0?" · ":"").append(ch.title);
                post(c,d.greeting+" Today's challenges",b.toString(),7420);}
        }catch(Exception ignored){}
    }
    private static double usualAvg;
    /** The hour (6 am to 9 pm) you walk most, from the last 14 days; -1 when no hour averages 800+ steps. */
    static int usualHour(Context c){double[] sum=new double[24];Calendar d=Calendar.getInstance();int n=0;for(int i=0;i<14;i++){d.add(Calendar.DAY_OF_YEAR,-1);double[] h=DaySteps.hoursOn(c,DaySteps.key(d.getTimeInMillis()));if(h==null)continue;n++;for(int j=0;j<24;j++)sum[j]+=h[j];}
        if(n<5)return -1;int best=-1;for(int j=6;j<=21;j++)if(best<0||sum[j]>sum[best])best=j;usualAvg=sum[best]/n;return usualAvg>=800?best:-1;}
    private static void post(Context c,String title,String text,int id){
        android.app.NotificationManager nm=c.getSystemService(android.app.NotificationManager.class);if(nm==null)return;
        if(android.os.Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new android.app.NotificationChannel("kadamm_coach","Coach",android.app.NotificationManager.IMPORTANCE_DEFAULT));
        nm.notify(id,Reminders.rich(c,"kadamm_coach",id,title,text,"Trainer",true).build());
    }
}
