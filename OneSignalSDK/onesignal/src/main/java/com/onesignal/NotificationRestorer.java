/**
 * Modified MIT License
 *
 * Copyright 2017 OneSignal
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
import android.os.Bundle;
import android.os.PersistableBundle;
import android.service.notification.StatusBarNotification;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.text.TextUtils;

import com.onesignal.OneSignalDbContract.NotificationTable;

import java.util.ArrayList;
import java.util.Random;

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
            restore(context);
         }
      }, "OS_RESTORE_NOTIFS").start();
   }

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

      StringBuilder dbQuerySelection = new StringBuilder(
              // 1 Week back.
              NotificationTable.COLUMN_NAME_CREATED_TIME + " > " + ((System.currentTimeMillis() / 1000L) - 604800L) + " AND " +
              NotificationTable.COLUMN_NAME_DISMISSED + " = 0 AND " +
              NotificationTable.COLUMN_NAME_OPENED + " = 0 AND " +
              NotificationTable.COLUMN_NAME_IS_SUMMARY + " = 0");

      //retrieve the list of notifications that are currently in the shade
      //this is used to prevent notifications from being restored twice in M and newer
      if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
         NotificationManager notifManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
         if(notifManager != null) {
            StatusBarNotification[] activeNotifs = notifManager.getActiveNotifications();
            if(activeNotifs.length > 0) {
               ArrayList<Integer> activeNotifIds = new ArrayList<>();
               for(StatusBarNotification activeNotif : activeNotifs)
                  activeNotifIds.add(activeNotif.getId());

               dbQuerySelection.append(" AND " + NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID + " NOT IN (")
                       .append(TextUtils.join(",",activeNotifIds))
                       .append(")");
            }
         }
      }

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
             null,                            // group by
             null,                            // filter by row groups
             NotificationTable._ID + " ASC"   // sort order, old to new
         );
   
         showNotifications(context, cursor);
         
      } catch (Throwable t) {
         OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error restoring notification records! ", t);
      } finally {
         if (cursor != null && !cursor.isClosed())
            cursor.close();
      }
   }
   
   // NOTE: This can be running from a Application, Service, or JobService context.
   static void showNotifications(Context context, Cursor cursor) {
      if (cursor.moveToFirst()) {
         boolean useExtender = (NotificationExtenderService.getIntent(context) != null);
      
         do {
            int existingId = cursor.getInt(cursor.getColumnIndex(NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID));
            String fullData = cursor.getString(cursor.getColumnIndex(NotificationTable.COLUMN_NAME_FULL_DATA));
            Long datetime = cursor.getLong(cursor.getColumnIndex(NotificationTable.COLUMN_NAME_CREATED_TIME));
         
            Intent serviceIntent;
            if (useExtender)
               serviceIntent = NotificationExtenderService.getIntent(context);
            else
               serviceIntent = new Intent().setComponent(new ComponentName(context.getPackageName(), GcmIntentService.class.getName()));

            serviceIntent.putExtra("json_payload", fullData);
            serviceIntent.putExtra("android_notif_id", existingId);
            serviceIntent.putExtra("restoring", true);
            serviceIntent.putExtra("timestamp", datetime);

            //Using API 22 as the cutoff due to PersistableBundle#putBoolean being 22+
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
               //use the job intent service...
               if (useExtender) {
                  NotificationExtenderService.enqueueWork(context,
                          serviceIntent.getComponent(), existingId,
                          serviceIntent);
               } else {
                  //use a job scheduler...
                  OneSignal.Log(OneSignal.LOG_LEVEL.INFO, "scheduleRestoreNotif:" + existingId);

                  //schedule the job with the job service here...
                  PersistableBundle restoreBundle = new PersistableBundle();
                  restoreBundle.putString("json_payload",fullData);
                  restoreBundle.putInt("android_notif_id", existingId);
                  restoreBundle.putBoolean("restoring", true);
                  restoreBundle.putLong("timestamp", datetime);

                  //set the job id to android notif id - that way we don't restore any notif twice
                  JobInfo.Builder jobBuilder = new JobInfo.Builder(existingId,
                          new ComponentName(context, RestoreJobService.class));
                  JobInfo job = jobBuilder.setOverrideDeadline(0)
                          .setExtras(restoreBundle)
                          .build();

                  JobScheduler jobScheduler = (JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
                  jobScheduler.schedule(job);
               }
            }
            else {
               startService(context, serviceIntent);
            }

         } while (cursor.moveToNext());
      }
   }

   private static void startService(Context context, Intent intent) {
      context.startService(intent);
   }
   
   static void startRestoreTaskFromReceiver(Context context) {
      if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
         // NotificationRestorer#restore is Code-sensitive to Android O
         NotificationRestorer.restore(context);
      }
      else {
         Intent intentForService = new Intent();
         intentForService.setComponent(new ComponentName(context.getPackageName(),
             NotificationRestoreService.class.getName()));
         WakefulBroadcastReceiver.startWakefulService(context, intentForService);
      }
   }

   static void startDelayedRestoreTaskFromReceiver(Context context) {
      if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
         // NotificationRestorer#restore is Code-sensitive to Android O
         OneSignal.Log(OneSignal.LOG_LEVEL.INFO, "scheduleRestoreKickoffJob");

         //set the job id to android notif id - that way we don't restore any notif twice
         JobInfo.Builder jobBuilder = new JobInfo.Builder(RESTORE_KICKOFF_REQUEST_CODE,
                 new ComponentName(context, RestoreKickoffJobService.class));
         JobInfo job = jobBuilder.setOverrideDeadline(15*1000)
                 .setMinimumLatency(15*1000)
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

         long scheduleTime = System.currentTimeMillis()+(15*1000);
         AlarmManager alarm = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
         alarm.set(AlarmManager.RTC_WAKEUP, scheduleTime, pendingIntent);
      }
   }
}
