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

import android.app.Service;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Build;

import androidx.annotation.RequiresApi;

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
class OSSyncService extends OSBackgroundSync {

   private static final Object INSTANCE_LOCK = new Object();
   private static final String SYNC_TASK_THREAD_ID = "OS_SYNCSRV_BG_SYNC";
   private static final int SYNC_TASK_ID = 2071862118;
   // We want to perform a on_focus sync as soon as the session is done to report the time
   private static final long SYNC_AFTER_BG_DELAY_MS = OneSignal.MIN_ON_SESSION_TIME_MILLIS;

   private static OSSyncService sInstance;

   static OSSyncService getInstance() {
      if (sInstance == null) {
         synchronized (INSTANCE_LOCK) {
            if (sInstance == null)
               sInstance = new OSSyncService();
         }
      }
      return sInstance;
   }

   @Override
   String getSyncTaskThreadId() {
      return SYNC_TASK_THREAD_ID;
   }

   @Override
   int getSyncTaskId() {
      return SYNC_TASK_ID;
   }

   @Override
   long getSyncTaskDelay() {
      return SYNC_AFTER_BG_DELAY_MS;
   }

   @Override
   Class getSyncServiceJobClass() {
      return SyncJobService.class;
   }

   @Override
   Class getSyncServicePendingIntentClass() {
      return SyncService.class;
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
         synchronized (OSBackgroundSync.LOCK) {
            OSSyncService.getInstance().nextScheduledSyncTimeMs = 0L;
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
         OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "LollipopSyncRunnable:JobFinished needsJobReschedule: " + OSSyncService.getInstance().needsJobReschedule);
         // Reschedule if needed
         boolean reschedule = OSSyncService.getInstance().needsJobReschedule;
         OSSyncService.getInstance().needsJobReschedule = false;
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
