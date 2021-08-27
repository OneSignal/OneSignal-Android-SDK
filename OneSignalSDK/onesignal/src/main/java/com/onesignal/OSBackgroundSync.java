/**
 * Modified MIT License
 * <p>
 * Copyright 2020 OneSignal
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * 1. The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * 2. All copies of substantial portions of the Software may only be used in connection
 * with services provided by OneSignal.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.onesignal;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.onesignal.AndroidSupportV4Compat.ContextCompat;

abstract class OSBackgroundSync {

    protected static final Object LOCK = new Object();
    protected boolean needsJobReschedule = false;

    private Thread syncBgThread;

    protected abstract String getSyncTaskThreadId();

    protected abstract int getSyncTaskId();

    protected abstract Class getSyncServiceJobClass();

    protected abstract Class getSyncServicePendingIntentClass();

    protected abstract void scheduleSyncTask(Context context);

    // Entry point from SyncJobService and SyncService when the job is kicked off
    void doBackgroundSync(Context context, Runnable runnable) {
        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "OSBackground sync, calling initWithContext");
        OneSignal.initWithContext(context);
        syncBgThread = new Thread(runnable, getSyncTaskThreadId());
        syncBgThread.start();
    }

    boolean stopSyncBgThread() {
        if (syncBgThread == null)
            return false;

        if (!syncBgThread.isAlive())
            return false;

        syncBgThread.interrupt();
        return true;
    }

    /**
     * The main schedule method for all SyncTasks - this method differentiates between
     * Legacy Android versions (pre-LOLLIPOP 21) and 21+ to execute an Alarm (<21) or a Job (>=21)
     *
     * @param context - Any context type
     * @param delayMs - How long to wait before doing work
     */
    protected void scheduleBackgroundSyncTask(Context context, long delayMs) {
        synchronized (LOCK) {
            if (useJob())
                scheduleSyncServiceAsJob(context, delayMs);
            else
                scheduleSyncServiceAsAlarm(context, delayMs);
        }
    }

    private boolean hasBootPermission(Context context) {
        return ContextCompat.checkSelfPermission(
                context,
                "android.permission.RECEIVE_BOOT_COMPLETED"
        ) == PackageManager.PERMISSION_GRANTED;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private boolean isJobIdRunning(Context context) {
        final JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        for (JobInfo jobInfo : jobScheduler.getAllPendingJobs()) {
            if (jobInfo.getId() == getSyncTaskId() && syncBgThread != null && syncBgThread.isAlive()) {
                return true;
            }
        }
        return false;
    }

    @RequiresApi(21)
    private void scheduleSyncServiceAsJob(Context context, long delayMs) {
        OneSignal.Log(OneSignal.LOG_LEVEL.VERBOSE, "OSBackgroundSync scheduleSyncServiceAsJob:atTime: " + delayMs);

        if (isJobIdRunning(context)) {
            OneSignal.Log(OneSignal.LOG_LEVEL.VERBOSE, "OSBackgroundSync scheduleSyncServiceAsJob Scheduler already running!");
            // If a JobScheduler is schedule again while running it will stop current job. We will schedule again when finished.
            // This will avoid InterruptionException due to thread.join() or queue.take() running.
            needsJobReschedule = true;
            return;
        }

        JobInfo.Builder jobBuilder = new JobInfo.Builder(
                getSyncTaskId(),
                new ComponentName(context, getSyncServiceJobClass())
        );

        jobBuilder
                .setMinimumLatency(delayMs)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);

        if (hasBootPermission(context))
            jobBuilder.setPersisted(true);

        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        try {
            int result = jobScheduler.schedule(jobBuilder.build());
            OneSignal.Log(OneSignal.LOG_LEVEL.INFO, "OSBackgroundSync scheduleSyncServiceAsJob:result: " + result);
        } catch (NullPointerException e) {
            // Catch for buggy Oppo devices
            // https://github.com/OneSignal/OneSignal-Android-SDK/issues/487
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR,
                    "scheduleSyncServiceAsJob called JobScheduler.jobScheduler which " +
                            "triggered an internal null Android error. Skipping job.", e);
        }
    }

    private void scheduleSyncServiceAsAlarm(Context context, long delayMs) {
        OneSignal.Log(OneSignal.LOG_LEVEL.VERBOSE, this.getClass().getSimpleName() + " scheduleServiceSyncTask:atTime: " + delayMs);

        PendingIntent pendingIntent = syncServicePendingIntent(context);
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        long triggerAtMs = OneSignal.getTime().getCurrentTimeMillis() + delayMs;
        alarm.set(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent);
    }

    protected void cancelBackgroundSyncTask(Context context) {
        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, this.getClass().getSimpleName() + " cancel background sync");
        synchronized (LOCK) {
            if (useJob()) {
                JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
                jobScheduler.cancel(getSyncTaskId());
            } else {
                AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                alarmManager.cancel(syncServicePendingIntent(context));
            }
        }
    }

    private PendingIntent syncServicePendingIntent(Context context) {
        // KEEP - PendingIntent.FLAG_UPDATE_CURRENT
        //          Some Samsung devices will throw the below exception otherwise.
        //          "java.lang.SecurityException: !@Too many alarms (500) registered"
        return PendingIntent.getService(
                context,
                getSyncTaskId(),
                new Intent(context, getSyncServicePendingIntentClass()),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static boolean useJob() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }
}
