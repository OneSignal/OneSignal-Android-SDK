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

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.v4.app.NotificationManagerCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import com.onesignal.OneSignalDbContract.NotificationTable;

// Process both notifications opens and dismisses.
class NotificationOpenedProcessor {

   static void processFromContext(Context context, Intent intent) {
      if (!isOneSignalIntent(intent))
         return;
      
      handleDismissFromActionButtonPress(context, intent);

      processIntent(context, intent);
   }
   
   private static boolean isOneSignalIntent(Intent intent) {
      return intent.hasExtra("onesignal_data") || intent.hasExtra("summary") || intent.hasExtra("notificationId");
   }
   
   private static void handleDismissFromActionButtonPress(Context context, Intent intent) {
      // Pressed an action button, need to clear the notification and close the notification area manually.
      if (intent.getBooleanExtra("action_button", false)) {
         NotificationManagerCompat.from(context).cancel(intent.getIntExtra("notificationId", 0));
         context.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
      }
   }
   
   static void processIntent(Context context, Intent intent) {
      String summaryGroup = intent.getStringExtra("summary");

      boolean dismissed = intent.getBooleanExtra("dismissed", false);

      JSONArray dataArray = null;
      if (!dismissed) {
         try {
            JSONObject jsonData = new JSONObject(intent.getStringExtra("onesignal_data"));
            jsonData.put("notificationId", intent.getIntExtra("notificationId", 0));
            intent.putExtra("onesignal_data", jsonData.toString());
            dataArray = NotificationBundleProcessor.newJsonArray(new JSONObject(intent.getStringExtra("onesignal_data")));
         } catch (Throwable t) {
            t.printStackTrace();
         }
      }
   
      OneSignalDbHelper dbHelper = OneSignalDbHelper.getInstance(context);
      SQLiteDatabase writableDb = null;
      
      try {
         writableDb = dbHelper.getWritableDbWithRetries();
         writableDb.beginTransaction();
         
         // We just opened a summary notification.
         if (!dismissed && summaryGroup != null)
            addChildNotifications(dataArray, summaryGroup, writableDb);

         markNotificationsConsumed(context, intent, writableDb);

         // Notification is not a summary type but a single notification part of a group.
         if (summaryGroup == null) {
            String group = intent.getStringExtra("grp");
            if (group != null)
               NotificationSummaryManager.updateSummaryNotificationAfterChildRemoved(context, writableDb, group, dismissed);
         }
         writableDb.setTransactionSuccessful();
      } catch (Exception e) {
         OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error processing notification open or dismiss record! ", e);
      } finally {
         if (writableDb != null) {
            try {
               writableDb.endTransaction(); // May throw if transaction was never opened or DB is full.
            } catch (Throwable t) {
               OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error closing transaction! ", t);
            }
         }
      }

      if (!dismissed)
         OneSignal.handleNotificationOpen(context, dataArray, intent.getBooleanExtra("from_alert", false));
   }

   private static void addChildNotifications(JSONArray dataArray, String summaryGroup, SQLiteDatabase writableDb) {
      String[] retColumn = { NotificationTable.COLUMN_NAME_FULL_DATA };
      String[] whereArgs = { summaryGroup };

      Cursor cursor = writableDb.query(
            NotificationTable.TABLE_NAME,
            retColumn,
            NotificationTable.COLUMN_NAME_GROUP_ID + " = ? AND " +   // Where String
                  NotificationTable.COLUMN_NAME_DISMISSED + " = 0 AND " +
                  NotificationTable.COLUMN_NAME_OPENED + " = 0 AND " +
                  NotificationTable.COLUMN_NAME_IS_SUMMARY + " = 0",
            whereArgs,
            null, null, null);

      if (cursor.getCount() > 1) {
         cursor.moveToFirst();
         do {
            try {
               String jsonStr = cursor.getString(cursor.getColumnIndex(NotificationTable.COLUMN_NAME_FULL_DATA));
               dataArray.put(new JSONObject(jsonStr));
            } catch (Throwable t) {
               OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Could not parse JSON of sub notification in group: " + summaryGroup);
            }
         } while (cursor.moveToNext());
      }

      cursor.close();
   }

   private static void markNotificationsConsumed(Context context, Intent intent, SQLiteDatabase writableDb) {
      String group = intent.getStringExtra("summary");
      String whereStr;
      String[] whereArgs = null;

      if (group != null) {
         whereStr = NotificationTable.COLUMN_NAME_GROUP_ID + " = ?";
         whereArgs = new String[]{ group };
      }
      else
         whereStr = NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID + " = " + intent.getIntExtra("notificationId", 0);

      writableDb.update(NotificationTable.TABLE_NAME, newContentValuesWithConsumed(intent), whereStr, whereArgs);
      BadgeCountUpdater.update(writableDb, context);
   }

   private static ContentValues newContentValuesWithConsumed(Intent intent) {
      ContentValues values = new ContentValues();

      boolean dismissed = intent.getBooleanExtra("dismissed", false);

      if (dismissed)
         values.put(NotificationTable.COLUMN_NAME_DISMISSED, 1);
      else
         values.put(NotificationTable.COLUMN_NAME_OPENED, 1);

      return values;
   }

}
