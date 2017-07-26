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

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.PersistableBundle;
import android.support.v4.content.WakefulBroadcastReceiver;

import com.onesignal.OneSignalDbContract.NotificationTable;

import java.util.Random;

class NotificationRestorer {

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
      
      Cursor cursor = null;
      try {
         SQLiteDatabase readableDb = dbHelper.getReadableDbWithRetries();
         cursor = readableDb.query(
             NotificationTable.TABLE_NAME,
             COLUMNS_FOR_RESTORE,
             // 1 Week back.
             NotificationTable.COLUMN_NAME_CREATED_TIME + " > " + ((System.currentTimeMillis() / 1000L) - 604800L) + " AND " +
                 NotificationTable.COLUMN_NAME_DISMISSED + " = 0 AND " +
                 NotificationTable.COLUMN_NAME_OPENED + " = 0 AND " +
                 NotificationTable.COLUMN_NAME_IS_SUMMARY + " = 0",
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
   // TODO: Android O - NotificationExtenderService compatibility
   //        1.  Query services that match action and instance a class.
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
   
            startService(context, serviceIntent);
         } while (cursor.moveToNext());
      }
   }
   
   // TODO: Update method to support both Services and JobServices
   private static void startService(Context context, Intent intent) {
      context.startService(intent);
   }
   
   static void startRestoreTaskFromReceiver(WakefulBroadcastReceiver receiver, Context context) {
      if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
         ComponentName componentName = new ComponentName(context.getPackageName(),
             NotificationRestoreJobService.class.getName());
         
         Random random = new Random();
         JobInfo jobInfo = new JobInfo.Builder(random.nextInt(), componentName)
             .setOverrideDeadline(0)
             .build();
         JobScheduler jobScheduler = (JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
         jobScheduler.schedule(jobInfo);
      }
      else {
         Intent intentForService = new Intent();
         intentForService.setComponent(new ComponentName(context.getPackageName(),
             NotificationRestoreService.class.getName()));
         receiver.startWakefulService(context, intentForService);
      }
   }
}
