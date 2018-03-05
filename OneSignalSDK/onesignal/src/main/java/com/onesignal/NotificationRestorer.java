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
import android.app.NotificationManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Process;
import android.service.notification.StatusBarNotification;
import android.support.annotation.WorkerThread;
import android.text.TextUtils;

import com.onesignal.OneSignalDbContract.NotificationTable;

import java.util.ArrayList;

// Purpose:
// Restore any notifications not interacted by the user back into the notification shade.
// We consider "not interacted" with if it wasn't swiped away or opened by the user.
// Android removes all the app's notifications in the following three cases.
//   1. App was force stopped (AKA forced killed). Different than the app being swiped away.
//   2. App is updated.
//   3. Device is rebooted.
// Restoring is done to ensure notifications are not missed by the user.
//
// Restoring cutoff:
// Notifications received older than 7 days are not restored.
//
// Notes:
// Android 8+ Oreo - Restored notifications will be generated under a "Restored" channel.
//                   The channel has a low priority so the user is not interrupted again.
// Android 6+ Marshmallow - We check the notification shade if the notification is already there
//                            we skip generating it again.

class NotificationRestorer {

   private static final int RESTORE_KICKOFF_REQUEST_CODE = 2071862120;

   static final String[] COLUMNS_FOR_RESTORE = {
       NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID,
       NotificationTable.COLUMN_NAME_FULL_DATA,
       NotificationTable.COLUMN_NAME_CREATED_TIME
   };
   
   // Notifications will never be force removed when the app's process is running.
   public static boolean restored;

   static void asyncRestore(final Context context) {
      new Thread(new Runnable() {
         @Override
         public void run() {
            Thread.currentThread().setPriority(Process.THREAD_PRIORITY_BACKGROUND);
            restore(context);
         }
      }, "OS_RESTORE_NOTIFS").start();
   }

   @WorkerThread
   public static void restore(Context context) {
      if (restored)
         return;
      restored = true;

      OneSignal.Log(OneSignal.LOG_LEVEL.INFO, "restoring notifications");

      OneSignalDbHelper dbHelper = OneSignalDbHelper.getInstance(context);
      SQLiteDatabase writableDb = null;
      
      try {
         writableDb = dbHelper.getWritableDbWithRetries();
         
         writableDb.beginTransaction();
         
         NotificationBundleProcessor.deleteOldNotifications(writableDb);
         writableDb.setTransactionSuccessful();
      } catch (Throwable t) {
         OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error deleting old notification records! ", t);
      } finally {
         if (writableDb != null) {
            try {
               writableDb.endTransaction(); // May throw if transaction was never opened or DB is full.
            } catch (Throwable t) {
               OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error closing transaction! ", t);
            }
         }
      }

      long created_at_cutoff = (System.currentTimeMillis() / 1_000L) - 604_800L; // 1 Week back
      StringBuilder dbQuerySelection = new StringBuilder(
        NotificationTable.COLUMN_NAME_CREATED_TIME + " > " + created_at_cutoff + " AND " +
        NotificationTable.COLUMN_NAME_DISMISSED + " = 0 AND " +
        NotificationTable.COLUMN_NAME_OPENED + " = 0 AND " +
        NotificationTable.COLUMN_NAME_IS_SUMMARY + " = 0"
      );

      skipVisibleNotifications(context, dbQuerySelection);

      OneSignal.Log(OneSignal.LOG_LEVEL.INFO,
              "Querying DB for notfs to restore: " + dbQuerySelection.toString());

      Cursor cursor = null;
      try {
         SQLiteDatabase readableDb = dbHelper.getReadableDbWithRetries();
         cursor = readableDb.query(
             NotificationTable.TABLE_NAME,
             COLUMNS_FOR_RESTORE,
             dbQuerySelection.toString(),
            null,
            null,                           // group by
            null,                            // filter by row groups
            NotificationTable._ID + " ASC"  // sort order, old to new
         );
   
         showNotifications(context, cursor, 100);
         
      } catch (Throwable t) {
         OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error restoring notification records! ", t);
      } finally {
         if (cursor != null && !cursor.isClosed())
            cursor.close();
      }
   }

   // Retrieve the list of notifications that are currently in the shade
   //    this is used to prevent notifications from being restored twice in M and newer.
   // This is important mostly for Android O as they can't be redisplayed in a silent way unless
   //    they are displayed under a different channel which isn't ideal.
   // For pre-O devices this still have the benefit of being more efficient
   private static void skipVisibleNotifications(Context context, StringBuilder dbQuerySelection) {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
         return;

      NotificationManager notifManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
      if (notifManager == null)
         return;

      try {
         StatusBarNotification[] activeNotifs = notifManager.getActiveNotifications();
         if (activeNotifs.length == 0)
            return;

         ArrayList<Integer> activeNotifIds = new ArrayList<>();
         for (StatusBarNotification activeNotif : activeNotifs)
            activeNotifIds.add(activeNotif.getId());

         dbQuerySelection
                 .append(" AND " + NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID + " NOT IN (")
                 .append(TextUtils.join(",", activeNotifIds))
                 .append(")");
      } catch(Throwable t) {
         // try-catch for Android 6.0.X bug work around,
         //    getActiveNotifications sometimes throws an exception.
         // Seem to be related to what Android's internal method getAppActiveNotifications returns.
         // Issue #422
      }
   }

   /**
    * Restores a set of notifications back to the notification shade based on an SQL cursor.
    * @param context - Context required to start JobIntentService
    * @param cursor - Source cursor to generate notifications from
    * @param delay - Delay to slow down process to ensure we don't spike CPU and I/O on the device.
    */
   static void showNotifications(Context context, Cursor cursor, int delay) {
      if (!cursor.moveToFirst())
         return;

      boolean useExtender = (NotificationExtenderService.getIntent(context) != null);

      do {
         if (useExtender) {
            Intent intent = NotificationExtenderService.getIntent(context);
            addRestoreExtras(intent, cursor);
            NotificationExtenderService.enqueueWork(context,
                  intent.getComponent(),
                  NotificationExtenderService.EXTENDER_SERVICE_JOB_ID,
                  intent);
         }
         else {
            Intent intent = addRestoreExtras(new Intent(), cursor);
            ComponentName componentName = new ComponentName(context, RestoreJobService.class);
            RestoreJobService.enqueueWork(context, componentName, RestoreJobService.RESTORE_SERVICE_JOB_ID, intent);
         }

         if (delay > 0)
            OSUtils.sleep(delay);
      } while (cursor.moveToNext());
   }

   private static Intent addRestoreExtras(Intent intent, Cursor cursor) {
      int existingId = cursor.getInt(cursor.getColumnIndex(NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID));
      String fullData = cursor.getString(cursor.getColumnIndex(NotificationTable.COLUMN_NAME_FULL_DATA));
      Long datetime = cursor.getLong(cursor.getColumnIndex(NotificationTable.COLUMN_NAME_CREATED_TIME));

      intent.putExtra("json_payload", fullData)
            .putExtra("android_notif_id", existingId)
            .putExtra("restoring", true)
            .putExtra("timestamp", datetime);

      return intent;
   }

   private static final int RESTORE_NOTIFICATIONS_DELAY_MS = 15_000;
   static void startDelayedRestoreTaskFromReceiver(Context context) {
      if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
         // NotificationRestorer#restore is Code-sensitive to Android O
         OneSignal.Log(OneSignal.LOG_LEVEL.INFO, "scheduleRestoreKickoffJob");

         // set the job id to android notif id - that way we don't restore any notif twice
         JobInfo.Builder jobBuilder = new JobInfo.Builder(RESTORE_KICKOFF_REQUEST_CODE,
                 new ComponentName(context, RestoreKickoffJobService.class));
         JobInfo job = jobBuilder
               .setOverrideDeadline(RESTORE_NOTIFICATIONS_DELAY_MS)
               .setMinimumLatency(RESTORE_NOTIFICATIONS_DELAY_MS)
               .build();
         JobScheduler jobScheduler = (JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
         jobScheduler.schedule(job);
      }
      else {
         OneSignal.Log(OneSignal.LOG_LEVEL.INFO, "scheduleRestoreKickoffAlarmTask");

         Intent intentForService = new Intent();
         intentForService.setComponent(new ComponentName(context.getPackageName(),
                 NotificationRestoreService.class.getName()));

         PendingIntent pendingIntent = PendingIntent.getService(context,
                 RESTORE_KICKOFF_REQUEST_CODE, intentForService, PendingIntent.FLAG_CANCEL_CURRENT);

         long scheduleTime = System.currentTimeMillis() + RESTORE_NOTIFICATIONS_DELAY_MS;
         AlarmManager alarm = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
         alarm.set(AlarmManager.RTC, scheduleTime, pendingIntent);
      }
   }
}