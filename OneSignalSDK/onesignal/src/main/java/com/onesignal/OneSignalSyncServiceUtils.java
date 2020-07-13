/**
 * Modified MIT License
 *
 * Copyright 2019 OneSignal
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

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 This schedules a job to fire later in the background preform player REST calls to make a(n);
   - on_focus
     - Delayed by MIN_ON_SESSION_TIME so we know no more time can be attributed to current session
   - Location update
   - Player update
      - IF there are any pending field updates - pushToken, tags, etc
*/

class OneSignalSyncServiceUtils {

   private static final int SYNC_TASK_ID = 2071862118;

   // We want to perform a on_focus sync as soon as the session is done to report the time
   private static final long SYNC_AFTER_BG_DELAY_MS = OneSignal.MIN_ON_SESSION_TIME_MILLIS;

   private static Long nextScheduledSyncTimeMs = 0L;
   private static boolean needsJobReschedule = false;

   static void scheduleLocationUpdateTask(Context context, long delayMs) {
      OneSignal.Log(OneSignal.LOG_LEVEL.VERBOSE, "scheduleLocationUpdateTask:delayMs: " + delayMs);
      scheduleSyncTask(context, delayMs);
   }

   static void scheduleSyncTask(Context context) {
      OneSignal.Log(OneSignal.LOG_LEVEL.VERBOSE, "scheduleSyncTask:SYNC_AFTER_BG_DELAY_MS: " + SYNC_AFTER_BG_DELAY_MS);
      scheduleSyncTask(context, SYNC_AFTER_BG_DELAY_MS);
   }

   static synchronized void cancelSyncTask(Context context) {
      nextScheduledSyncTimeMs = 0L;
      boolean didSchedule = LocationController.scheduleUpdate(context);
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
    * @param context - Any context type
    * @param delayMs - How long to wait before doing work
    */
   private static synchronized void scheduleSyncTask(Context context, long delayMs) {
      if (nextScheduledSyncTimeMs != 0 &&
            System.currentTimeMillis() + delayMs > nextScheduledSyncTimeMs)
         return;

      if (delayMs < 5_000)
         delayMs = 5_000;

      if (useJob())
         scheduleSyncServiceAsJob(context, delayMs);
      else
         scheduleSyncServiceAsAlarm(context, delayMs);

      nextScheduledSyncTimeMs = System.currentTimeMillis() + delayMs;
   }

   private static boolean hasBootPermission(Context context) {
      return ContextCompat.checkSelfPermission(
               context,
               "android.permission.RECEIVE_BOOT_COMPLETED"
             ) == PackageManager.PERMISSION_GRANTED;
   }


   @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
   private static boolean isJobIdRunning(Context context) {
      final JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
      for (JobInfo jobInfo : jobScheduler.getAllPendingJobs()) {
         if (jobInfo.getId() == OneSignalSyncServiceUtils.SYNC_TASK_ID && syncBgThread != null && syncBgThread.isAlive()) {
            return true;
         }
      }
      return false;
   }

   @RequiresApi(21)
   private static void scheduleSyncServiceAsJob(Context context, long delayMs) {
      OneSignal.Log(OneSignal.LOG_LEVEL.VERBOSE, "scheduleSyncServiceAsJob:atTime: " + delayMs);

      if (isJobIdRunning(context)) {
         OneSignal.Log(OneSignal.LOG_LEVEL.VERBOSE, "scheduleSyncServiceAsJob Scheduler already running!");
         // If a JobScheduler is schedule again while running it will stop current job. We will schedule again when finished.
         // This will avoid InterruptionException due to thread.join() or queue.take() running.
         needsJobReschedule = true;
         return;
      }

      JobInfo.Builder jobBuilder = new JobInfo.Builder(
         SYNC_TASK_ID,
         new ComponentName(context, SyncJobService.class)
      );

      jobBuilder
         .setMinimumLatency(delayMs)
         .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);

      if (hasBootPermission(context))
         jobBuilder.setPersisted(true);

      JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
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

   private static Thread syncBgThread;
   // Entry point from SyncJobService and SyncService when the job is kicked off
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
         synchronized (OneSignalSyncServiceUtils.class) {
            nextScheduledSyncTimeMs = 0L;
         }
         if (OneSignal.getUserId() == null) {
            stopSync();
            return;
         }

         OneSignal.appId = OneSignal.getSavedAppId();
         OneSignalStateSynchronizer.initUserState();

         // BlockingQueue used to make sure that the complete() callback is completely finished before moving on
         // SyncUserState was moving on and causing a thread lock with the complete() LocationGMS work
         // Issue #650
         // https://github.com/OneSignal/OneSignal-Android-SDK/issues/650
         try {
            final BlockingQueue<Object> queue = new ArrayBlockingQueue<>(1);
            LocationController.LocationHandler locationHandler = new LocationController.LocationHandler() {
               @Override
               public LocationController.PermissionType getType() {
                  return LocationController.PermissionType.SYNC_SERVICE;
               }

               @Override
               public void onComplete(LocationController.LocationPoint point) {
                  Object object = point != null ?  point : new Object();
                  queue.offer(object);
               }
            };
            LocationController.getLocation(OneSignal.appContext, false, false, locationHandler);

            // The take() will return the offered point once the callback for the locationHandler is completed
            Object point = queue.take();
            if (point instanceof LocationController.LocationPoint)
               OneSignalStateSynchronizer.updateLocation((LocationController.LocationPoint) point);

         } catch (InterruptedException e) {
            e.printStackTrace();
         }

         // Both these calls are synchronous
         // Once the queue calls take the code will continue and move on to the syncUserState
         OneSignalStateSynchronizer.syncUserState(true);
         FocusTimeController.getInstance().doBlockingBackgroundSyncOfUnsentTime();
         stopSync();
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
         OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "LollipopSyncRunnable:JobFinished needsJobReschedule: " + needsJobReschedule);
         // Reschedule if needed
         boolean reschedule = needsJobReschedule;
         needsJobReschedule = false;
         jobService.jobFinished(jobParameters, reschedule);
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
