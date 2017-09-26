package com.onesignal;

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.annotation.RequiresApi;

/**
 * Copyright 2017 OneSignal
 * Created by alamgir on 9/22/17.
 */

class OneSignalSyncUtils {

    private static final int SYNC_TASK_JOB_ID;
    private static final int SYNC_SERVICE_REQUEST_CODE = SYNC_TASK_JOB_ID = 2071862119;

    /**
     * The main schedule method for all SyncTasks - this method differentiates between
     * Legacy Android versions (pre-Oreo) and 26+ to execute an Alarm (<26) or a Job (>=26)
     * @param context
     * @param atTime
     */
    @TargetApi(26)
    static void scheduleSyncTask(Context context,long atTime) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // use the JobService and JobScheduler
            OneSignal.Log(OneSignal.LOG_LEVEL.VERBOSE, "scheduleJobSyncTask:atTime: " + atTime);

            //build the job
            JobInfo.Builder jobBuilder = new JobInfo.Builder(SYNC_TASK_JOB_ID, new ComponentName(context, SyncJobService.class));
            JobInfo job = jobBuilder.setMinimumLatency(atTime-System.currentTimeMillis())
                    .setOverrideDeadline(atTime-System.currentTimeMillis())
                    .build();

            JobScheduler jobScheduler = (JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            jobScheduler.schedule(job);
        }
        else {
            OneSignal.Log(OneSignal.LOG_LEVEL.VERBOSE, "scheduleServiceSyncTask:atTime: " + atTime);

            Intent intent = new Intent(context, SyncService.class);
            intent.putExtra("task", SyncService.TASK_SYNC);

            // KEEP - PendingIntent.FLAG_UPDATE_CURRENT
            //    Some Samsung devices will throw the below exception otherwise.
            //    "java.lang.SecurityException: !@Too many alarms (500) registered"

            PendingIntent pendingIntent = PendingIntent.getService(context, SYNC_SERVICE_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            AlarmManager alarm = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
            alarm.set(AlarmManager.RTC_WAKEUP, atTime, pendingIntent);
        }
    }

    static void scheduleSyncTask(Context context, int executeInSecs) {
        //execute the schedule sync task a number of seconds from now
        scheduleSyncTask(context, System.currentTimeMillis() + (executeInSecs * 1000));
    }

    static void checkOnFocusSync() {
        long unsentTime = OneSignal.GetUnsentActiveTime();
        if (unsentTime < OneSignal.MIN_ON_FOCUS_TIME)
            return;

        OneSignal.sendOnFocus(unsentTime, true);
    }

    static void doBackgroundSync(Context context, SyncRunnable runnable) {
        OneSignal.appContext = context;
        new Thread(runnable, "OS_SYNCSRV_BG_SYNC").start();
    }

    /**
     * An abstract class to keep the actual syncing logic in one place
     * while allowing various different stopping mechanisms depending on
     * the calling service type (IntentService vs JobService vs Service)
     *
     * Subclasses should override only the stopSync() method
     */
    private static abstract class SyncRunnable implements Runnable {

        Service callerService;

        SyncRunnable(Service caller) {
            callerService = caller;
        }

        @Override
        public void run() {
            if (OneSignal.getUserId() == null) {
                stopSync();
                return;
            }

            OneSignal.appId = OneSignal.getSavedAppId();
            OneSignalStateSynchronizer.initUserState(OneSignal.appContext);

            LocationGMS.getLocation(OneSignal.appContext, false, new LocationGMS.LocationHandler() {
                @Override
                public void complete(LocationGMS.LocationPoint point) {
                    if (point != null)
                        OneSignalStateSynchronizer.updateLocation(point);

                    OneSignalStateSynchronizer.syncUserState(true);
                    OneSignalSyncUtils.checkOnFocusSync();

                    stopSync();
                }
            });
        }

        abstract void stopSync();
    }

    /**
     * A SyncRunnable that accommodates a JobService and
     * calls JobService#jobFinished during stopSync()
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    static class OreoSyncRunnable extends SyncRunnable {

        private JobService jobService;
        private JobParameters jobParameters;

        OreoSyncRunnable(Service caller,
                                JobParameters jobParameters) {
            super(caller);
            this.jobService = (JobService)caller;
            this.jobParameters = jobParameters;
        }

        @Override
        void stopSync() {
            OneSignal.Log(OneSignal.LOG_LEVEL.INFO,"OreoSyncRunnable:JobFinished");
            jobService.jobFinished(jobParameters,false);
        }
    }

    /**
     * A SyncRunnable that accommodates a normal Service and
     * calls Service#stopSelf during stopSync()
     */
    static class LegacySyncRunnable extends SyncRunnable {

        LegacySyncRunnable(Service caller) {
            super(caller);
        }

        @Override
        void stopSync() {
            OneSignal.Log(OneSignal.LOG_LEVEL.INFO,"LegacySyncRunnable:Stopped");
            callerService.stopSelf();
        }
    }

}
