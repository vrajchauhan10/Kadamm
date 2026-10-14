package com.stride.run;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Notification wording. Each kind has several phrasings; a phrasing already sent today is never sent again
 * the same day, so no two notifications in a day read alike.
 */
final class Messages {
    private Messages() {}

    static final String[][] GOAL={
        {"Daily goal achieved!","Great work! You've reached your daily activity goal today."},
        {"Goal reached","%,d steps today. That's your goal done. Anything more is a bonus."},
        {"You did it","Daily step goal complete. Nice consistency."},
        {"Goal: done","You've hit your target for today. Enjoy the rest of the day."},
        {"Target reached","Today's goal is in the bag with %,d steps."},
    };
    static final String[][] PROGRESS={
        {"You're making progress!","You've completed %,d steps today. Just %,d more to reach your daily goal. Keep moving!"},
        {"Over halfway","%,d steps so far. %,d to go."},
        {"Good momentum","You're %,d steps in. Another %,d and the goal is yours."},
        {"Keep it going","%,d steps done today, %,d left to your goal."},
        {"Nice pace today","%,d steps already. Only %,d more."},
    };
    static final String[][] MOVE={
        {"Time to move","You've been still for %s. A 5-minute walk adds about 500 steps."},
        {"Stretch your legs","%s without steps. A short walk will wake you up."},
        {"Quick walk?","It's been %s since you last moved. Even 3 minutes helps."},
        {"Up for a few steps?","%s sitting still. A lap of the room counts too."},
        {"Movement break","No steps for %s. Stand up and take a short walk."},
        {"Your legs called","%s since your last steps. A walk to refill water works."},
    };
    static final String[][] CLOSE={
        {"Close to your goal","Only %,d steps to your goal. A %d-minute walk will do it."},
        {"Almost at your goal","%,d steps left today. About %d minutes of walking."},
        {"Finish strong","You're %,d steps away. A %d-minute evening walk closes it."},
        {"So close","Just %,d steps to go. That's roughly %d minutes."},
        {"One short walk","%,d more steps and today's goal is done. Around %d minutes."},
    };

    private static SharedPreferences p(Context c){return c.getSharedPreferences("kadamm_messages",Context.MODE_PRIVATE);}
    private static Set<String> sentToday(Context c){
        SharedPreferences s=p(c);
        if(!DaySteps.todayKey().equals(s.getString("day","")))return new HashSet<>();
        return new HashSet<>(s.getStringSet("sent",new HashSet<>()));
    }
    /** Times this kind was sent today. */
    static int countToday(Context c,String kind){int n=0;for(String k:sentToday(c))if(k.startsWith(kind+":"))n++;return n;}

    /**
     * Picks a phrasing of {@code kind} not yet used today, starting from a different place each day, and records it.
     * Returns {title, text}, or null when every phrasing has already been used today.
     */
    static String[] pick(Context c,String kind,String[][] pool,Object... args){
        Set<String> sent=sentToday(c);int start=Math.abs(DaySteps.todayKey().hashCode()+kind.hashCode())%pool.length;
        for(int i=0;i<pool.length;i++){
            int k=(start+i)%pool.length;String id=kind+":"+k;if(sent.contains(id))continue;
            sent.add(id);p(c).edit().putString("day",DaySteps.todayKey()).putStringSet("sent",sent).apply();
            return new String[]{pool[k][0],String.format(Locale.US,pool[k][1],args)};
        }
        return null;
    }
    static void record(Context c,String id){Set<String> sent=sentToday(c);sent.add(id);p(c).edit().putString("day",DaySteps.todayKey()).putStringSet("sent",sent).apply();}
}
