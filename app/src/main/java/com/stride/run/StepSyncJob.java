package com.stride.run;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/** Reads the phone step counter every 15 minutes so daily totals stay current while Kadamm is closed. */
public class StepSyncJob extends JobService {
    private static final int JOB_ID=4101;

    static void schedule(Context c){
        JobScheduler scheduler=c.getSystemService(JobScheduler.class);
        if(scheduler==null||scheduler.getPendingJob(JOB_ID)!=null)return;
        try{
            scheduler.schedule(new JobInfo.Builder(JOB_ID,new ComponentName(c,StepSyncJob.class))
                .setPeriodic(15*60*1000L).setPersisted(true).build());
        }catch(Exception ignored){}
    }

    @Override public boolean onStartJob(JobParameters params){
        if(!DaySteps.ready(this)&&!HealthSync.backgroundGranted(this))return false;
        DaySteps.sample(this,()->{
            Runnable after=()->{Reminders.check(this);StepsWidget.refresh(this);jobFinished(params,false);};
            // With background access, Health Connect's count (Samsung Health etc.) stays current while Kadamm is closed.
            if(HealthSync.backgroundGranted(this))HealthSync.refresh(this,after);else after.run();
        });
        return true;
    }
    @Override public boolean onStopJob(JobParameters params){return false;}
}
