/**
 * Modified MIT License
 *
 * Copyright 2016 OneSignal
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
import org.json.JSONException;
import org.json.JSONObject;

import com.onesignal.OneSignalDbContract.NotificationTable;

// Used to process opens and dismisses of notifications.

public class NotificationOpenedProcessor {

   private static Context context;
   private static Intent intent;

   public static void processFromActivity(Context inContext, Intent inIntent) {
      // Pressed an action button, need to clear the notification and close the notification area manually.
      if (inIntent.getBooleanExtra("action_button", false)) {
         NotificationManagerCompat.from(inContext).cancel(inIntent.getIntExtra("notificationId", 0));
         inContext.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
      }

      processIntent(inContext, inIntent);
   }

   static void processIntent(Context incContext, Intent inIntent) {
      context = incContext;
      intent = inIntent;

      String summaryGroup = intent.getStringExtra("summary");

      boolean dismissed = intent.getBooleanExtra("dismissed", false);

      OneSignalDbHelper dbHelper = new OneSignalDbHelper(context);
      SQLiteDatabase writableDb = dbHelper.getWritableDatabase();

      JSONArray dataArray = null;
      if (!dismissed) {
         try {
            dataArray = newJsonArray(new JSONObject(intent.getStringExtra("onesignal_data")));
         } catch (Throwable t) {
            t.printStackTrace();
         }
      }

      // We just opened a summary notification.
      if (!dismissed && summaryGroup != null)
         addChildNotifications(dataArray, summaryGroup, writableDb);

      markNotificationsConsumed(writableDb);

      // Notification is not a summary type but a single notification part of a group.
      if (summaryGroup == null && intent.getStringExtra("grp") != null)
         updateSummaryNotification(writableDb);

      writableDb.close();

      if (!dismissed)
         OneSignal.handleNotificationOpened(context, dataArray, inIntent.getBooleanExtra("from_alert", false));
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

   private static void markNotificationsConsumed(SQLiteDatabase writableDb) {
      String group = intent.getStringExtra("summary");
      String whereStr;
      String[] whereArgs = null;

      if (group != null) {
         whereStr = NotificationTable.COLUMN_NAME_GROUP_ID + " = ?";
         whereArgs = new String[]{ group };
      }
      else
         whereStr = NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID + " = " + intent.getIntExtra("notificationId", 0);

      writableDb.update(NotificationTable.TABLE_NAME, newContentValuesWithConsumed(), whereStr, whereArgs);
      BadgeCountUpdater.update(writableDb, context);
   }

   private static void updateSummaryNotification(SQLiteDatabase writableDb) {
      String grpId = intent.getStringExtra("grp");

      Cursor cursor = writableDb.query(
            NotificationTable.TABLE_NAME,
            new String[] { NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID }, // retColumn
            NotificationTable.COLUMN_NAME_GROUP_ID + " = ? AND " +   // Where String
                  NotificationTable.COLUMN_NAME_DISMISSED + " = 0 AND " +
                  NotificationTable.COLUMN_NAME_OPENED + " = 0 AND " +
                  NotificationTable.COLUMN_NAME_IS_SUMMARY + " = 0" ,
            new String[] { grpId }, // whereArgs
            null, null, null);

      // All individual notifications consumed, make summary notification as consumed as well.
      if (cursor.getCount() == 0)
         writableDb.update(NotificationTable.TABLE_NAME, newContentValuesWithConsumed(), NotificationTable.COLUMN_NAME_GROUP_ID + " = ?", new String[] {grpId });
      else {
         try {
            GenerateNotification.createSummaryNotification(context, true, new JSONObject("{\"grp\": \"" + grpId + "\"}"));
         } catch (JSONException e) {}
      }

      cursor.close();
   }

   private static ContentValues newContentValuesWithConsumed() {
      ContentValues values = new ContentValues();

      boolean dismissed = intent.getBooleanExtra("dismissed", false);

      if (dismissed)
         values.put(NotificationTable.COLUMN_NAME_DISMISSED, 1);
      else
         values.put(NotificationTable.COLUMN_NAME_OPENED, 1);

      return values;
   }

   private static JSONArray newJsonArray(JSONObject jsonObject) {
      JSONArray jsonArray = new JSONArray();
      jsonArray.put(jsonObject);
      return jsonArray;
   }
}
