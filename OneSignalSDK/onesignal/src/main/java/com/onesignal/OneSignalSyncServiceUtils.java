/**
 * Modified MIT License
 *
 * Copyright 2018 OneSignal
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * 1. The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * 2. All copies of substantial portions of the Software may only be used in connection
 * with services provided by OneSignal.
 *
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
import android.app.Service;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.RequiresApi;

import com.onesignal.AndroidSupportV4Compat.ContextCompat;

import java.util.concurrent.atomic.AtomicBoolean;

class OneSignalSyncServiceUtils {

   private static final int SYNC_TASK_ID = 2071862118;

   private static final int SYNC_AFTER_BG_DELAY_MS = 120_000;

   private static Long nextScheduledSyncTime = 0L;

   static void scheduleLocationUpdateTask(Context context, long delayMs) {
      OneSignal.Log(OneSignal.LOG_LEVEL.VERBOSE, "scheduleLocationUpdateTask:delayMs: " + delayMs);
      scheduleSyncTask(context, delayMs);
   }

   static void scheduleSyncTask(Context context) {
      OneSignal.Log(OneSignal.LOG_LEVEL.VERBOSE, "scheduleSyncTask:SYNC_AFTER_BG_DELAY_MS: " + SYNC_AFTER_BG_DELAY_MS);
      scheduleSyncTask(context, SYNC_AFTER_BG_DELAY_MS);
   }

   static void cancelSyncTask(Context context) {
      synchronized (nextScheduledSyncTime) {
         nextScheduledSyncTime = 0L;
         boolean didSchedule = LocationGMS.scheduleUpdate(context);
         if (didSchedule)
            return;

         if (useJob()) {
            JobScheduler jobScheduler = (JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            jobScheduler.cancel(SYNC_TASK_ID);
         } else {
            AlarmManager alarmManager = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
            alarmManager.cancel(syncServicePendingIntent(context));
         }
      }
   }

   private static PendingIntent syncServicePendingIntent(Context context) {
      // KEEP - PendingIntent.FLAG_UPDATE_CURRENT
      //          Some Samsung devices will throw the below exception otherwise.
      //          "java.lang.SecurityException: !@Too many alarms (500) registered"
      return PendingIntent.getService(
         context,
         SYNC_TASK_ID,
         new Intent(context, SyncService.class),
         PendingIntent.FLAG_UPDATE_CURRENT
      );
   }

   private static boolean useJob() {
      return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
   }

   /**
    * The main schedule method for all SyncTasks - this method differentiates between
    * Legacy Android versions (pre-LOLLIPOP 21) and 21+ to execute an Alarm (<21) or a Job (>=21)
    *
    * @param context
    * @param delayMs
    */
   private static void scheduleSyncTask(Context context, long delayMs) {
      synchronized (nextScheduledSyncTime) {
         if (nextScheduledSyncTime != 0 &&
               System.currentTimeMillis() + delayMs > nextScheduledSyncTime)
            return;

         if (delayMs < 5_000)
            delayMs = 5_000;

         if (useJob())
            scheduleSyncServiceAsJob(context, delayMs);
         else
            scheduleSyncServiceAsAlarm(context, delayMs);
         nextScheduledSyncTime = System.currentTimeMillis() + delayMs;
      }
   }

   private static boolean hasBootPermission(Context context) {
      return ContextCompat.checkSelfPermission(
               context,
               "android.permission.RECEIVE_BOOT_COMPLETED"
             ) == PackageManager.PERMISSION_GRANTED;
   }

   @RequiresApi(21)
   private static void scheduleSyncServiceAsJob(Context context, long delayMs) {
      OneSignal.Log(OneSignal.LOG_LEVEL.VERBOSE, "scheduleSyncServiceAsJob:atTime: " + delayMs);

      JobInfo.Builder jobBuilder = new JobInfo.Builder(
         SYNC_TASK_ID,
         new ComponentName(context, SyncJobService.class)
      );

      jobBuilder
         .setMinimumLatency(delayMs)
         .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);

      if (hasBootPermission(context))
         jobBuilder.setPersisted(true);

      JobScheduler jobScheduler = (JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
      try {
         int result = jobScheduler.schedule(jobBuilder.build());
         OneSignal.Log(OneSignal.LOG_LEVEL.INFO, "scheduleSyncServiceAsJob:result: " + result);
      } catch (NullPointerException e) {
         // Catch for buggy Oppo devices
         // https://github.com/OneSignal/OneSignal-Android-SDK/issues/487
         OneSignal.Log(OneSignal.LOG_LEVEL.ERROR,
            "scheduleSyncServiceAsJob called JobScheduler.jobScheduler which " +
               "triggered an internal null Android error. Skipping job.", e);
      }
   }

   private static void scheduleSyncServiceAsAlarm(Context context, long delayMs) {
      OneSignal.Log(OneSignal.LOG_LEVEL.VERBOSE, "scheduleServiceSyncTask:atTime: " + delayMs);

      PendingIntent pendingIntent = syncServicePendingIntent(context);
      AlarmManager alarm = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
      long triggerAtMs = System.currentTimeMillis() + delayMs;
      alarm.set(AlarmManager.RTC_WAKEUP,  triggerAtMs + delayMs, pendingIntent);
   }

   private static AtomicBoolean runningOnFocusTime = new AtomicBoolean();
   static void syncOnFocusTime() {
      if (runningOnFocusTime.get())
         return;
      synchronized (runningOnFocusTime) {
         runningOnFocusTime.set(true);
         internalSyncOnFocusTime();
         runningOnFocusTime.set(false);
      }
   }

   private static void internalSyncOnFocusTime() {
      long unsentTime = OneSignal.GetUnsentActiveTime();
      if (unsentTime < OneSignal.MIN_ON_FOCUS_TIME)
         return;

      OneSignal.sendOnFocus(unsentTime, true);
   }

   private static Thread syncBgThread;
   static void doBackgroundSync(Context context, SyncRunnable runnable) {
      OneSignal.setAppContext(context);
      syncBgThread = new Thread(runnable, "OS_SYNCSRV_BG_SYNC");
      syncBgThread.start();
   }

   static boolean stopSyncBgThread() {
      if (syncBgThread == null)
         return false;

      if (!syncBgThread.isAlive())
         return false;

      syncBgThread.interrupt();
      return true;
   }

   /**
    * An abstract class to keep the actual syncing logic in one place
    * while allowing various different stopping mechanisms depending on
    * the calling service type (IntentService vs JobService vs Service)
    * <p>
    * Subclasses should override only the stopSync() method
    */
   static abstract class SyncRunnable implements Runnable {
      @Override
      public final void run() {
         synchronized (nextScheduledSyncTime) {
            nextScheduledSyncTime = 0L;
         }
         if (OneSignal.getUserId() == null) {
            stopSync();
            return;
         }

         OneSignal.appId = OneSignal.getSavedAppId();
         OneSignalStateSynchronizer.initUserState();

         LocationGMS.LocationHandler locationHandler = new LocationGMS.LocationHandler() {
            @Override
            public LocationGMS.CALLBACK_TYPE getType() {
               return LocationGMS.CALLBACK_TYPE.SYNC_SERVICE;
            }

            @Override
            public void complete(LocationGMS.LocationPoint point) {
               if (point != null)
                  OneSignalStateSynchronizer.updateLocation(point);

               // Both these calls are synchronous.
               //   Thread is blocked until network calls are made or their retry limits are reached
               OneSignalStateSynchronizer.syncUserState(true);
               OneSignalSyncServiceUtils.syncOnFocusTime();
               stopSync();
            }
         };
         LocationGMS.getLocation(OneSignal.appContext, false, locationHandler);
      }

      protected abstract void stopSync();
   }

   /**
    * A SyncRunnable that accommodates a JobService and
    * calls JobService#jobFinished during stopSync()
    */
   @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
   static class LollipopSyncRunnable extends SyncRunnable {

      private JobService jobService;
      private JobParameters jobParameters;

      LollipopSyncRunnable(JobService caller, JobParameters jobParameters) {
         this.jobService = caller;
         this.jobParameters = jobParameters;
      }

      @Override
      protected void stopSync() {
         OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "LollipopSyncRunnable:JobFinished");
         jobService.jobFinished(jobParameters, false);
      }
   }

   /**
    * A SyncRunnable that accommodates a normal Service and
    * calls Service#stopSelf during stopSync()
    */
   static class LegacySyncRunnable extends SyncRunnable {
      Service callerService;

      LegacySyncRunnable(Service caller) {
         callerService = caller;
      }

      @Override
      protected void stopSync() {
         OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "LegacySyncRunnable:Stopped");
         callerService.stopSelf();
      }
   }
}
