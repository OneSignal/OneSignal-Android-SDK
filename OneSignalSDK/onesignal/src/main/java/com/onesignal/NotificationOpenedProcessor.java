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

import android.app.Activity;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationManagerCompat;

import com.onesignal.OneSignalDbContract.NotificationTable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import static com.onesignal.GenerateNotification.BUNDLE_KEY_ANDROID_NOTIFICATION_ID;
import static com.onesignal.GenerateNotification.BUNDLE_KEY_ONESIGNAL_DATA;

// Process both notifications opens and dismisses.
class NotificationOpenedProcessor {

   private static final String TAG = NotificationOpenedProcessor.class.getCanonicalName();

   static void processFromContext(Context context, Intent intent) {
      if (!isOneSignalIntent(intent))
         return;

      if (context != null)
         OneSignal.initWithContext(context.getApplicationContext());

      handleDismissFromActionButtonPress(context, intent);

      processIntent(context, intent);
   }

   // Was Bundle created from our SDK? Prevents external Intents
   // TODO: Could most likely be simplified checking if BUNDLE_KEY_ONESIGNAL_DATA is present
   private static boolean isOneSignalIntent(Intent intent) {
      return intent.hasExtra(BUNDLE_KEY_ONESIGNAL_DATA) || intent.hasExtra("summary") || intent.hasExtra(BUNDLE_KEY_ANDROID_NOTIFICATION_ID);
   }

   private static void handleDismissFromActionButtonPress(Context context, Intent intent) {
      // Pressed an action button, need to clear the notification and close the notification area manually.
      if (intent.getBooleanExtra("action_button", false)) {
         NotificationManagerCompat.from(context).cancel(intent.getIntExtra(BUNDLE_KEY_ANDROID_NOTIFICATION_ID, 0));
         context.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
      }
   }

   static void processIntent(Context context, Intent intent) {
      OneSignalDbHelper dbHelper = OneSignalDbHelper.getInstance(context);
      String summaryGroup = intent.getStringExtra("summary");

      boolean dismissed = intent.getBooleanExtra("dismissed", false);

      OSNotificationIntentExtras intentExtras = null;
      if (!dismissed) {
         intentExtras = processToOpenIntent(context, intent, dbHelper, summaryGroup);

         if (intentExtras == null)
            return;
      }

      markNotificationsConsumed(context, intent, dbHelper, dismissed);

      // Notification is not a summary type but a single notification part of a group.
      if (summaryGroup == null) {
         String group = intent.getStringExtra("grp");
         if (group != null)
            NotificationSummaryManager.updateSummaryNotificationAfterChildRemoved(context, dbHelper, group, dismissed);
      }

      OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "processIntent from context: " + context + " and intent: " + intent);
      if (intent.getExtras() != null)
         OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "processIntent intent extras: " + intent.getExtras().toString());

      if (!dismissed) {
         if (!(context instanceof Activity))
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.ERROR, "NotificationOpenedProcessor processIntent from an non Activity context: " + context);
         else OneSignal.handleNotificationOpen((Activity) context, intentExtras.getDataArray(),
                 false, OSNotificationFormatHelper.getOSNotificationIdFromJson(intentExtras.getJsonData()));
      }
   }

   static OSNotificationIntentExtras processToOpenIntent(Context context, Intent intent, OneSignalDbHelper dbHelper, String summaryGroup) {
      JSONArray dataArray = null;
      JSONObject jsonData = null;
      try {
         jsonData = new JSONObject(intent.getStringExtra(BUNDLE_KEY_ONESIGNAL_DATA));

         if (!(context instanceof Activity))
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.ERROR, "NotificationOpenedProcessor processIntent from an non Activity context: " + context);
         else if (handleIAMPreviewOpen((Activity) context, jsonData))
            return null;

         jsonData.put(BUNDLE_KEY_ANDROID_NOTIFICATION_ID, intent.getIntExtra(BUNDLE_KEY_ANDROID_NOTIFICATION_ID, 0));
         intent.putExtra(BUNDLE_KEY_ONESIGNAL_DATA, jsonData.toString());
         dataArray = NotificationBundleProcessor.newJsonArray(new JSONObject(intent.getStringExtra(BUNDLE_KEY_ONESIGNAL_DATA)));
      } catch (JSONException e) {
         e.printStackTrace();
      }

      // We just opened a summary notification.
      if (summaryGroup != null)
         addChildNotifications(dataArray, summaryGroup, dbHelper);

      return new OSNotificationIntentExtras(dataArray, jsonData);
   }

   static boolean handleIAMPreviewOpen(@NonNull Activity context, @NonNull JSONObject jsonData) {
      String previewUUID = OSInAppMessagePreviewHandler.inAppPreviewPushUUID(jsonData);
      if (previewUUID == null)
         return false;

      OneSignal.getInAppMessageController().displayPreviewMessage(previewUUID);
      return true;
   }

   private static void addChildNotifications(JSONArray dataArray, String summaryGroup, OneSignalDbHelper writableDb) {
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
            } catch (JSONException e) {
               OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Could not parse JSON of sub notification in group: " + summaryGroup);
            }
         } while (cursor.moveToNext());
      }

      cursor.close();
   }

   private static void markNotificationsConsumed(Context context, Intent intent, OneSignalDbHelper writableDb, boolean dismissed) {
      String summaryGroup = intent.getStringExtra("summary");
      String whereStr;
      String[] whereArgs = null;

      if (summaryGroup != null) {
         boolean isGroupless = summaryGroup.equals(OneSignalNotificationManager.getGrouplessSummaryKey());
         if (isGroupless)
            whereStr = NotificationTable.COLUMN_NAME_GROUP_ID + " IS NULL";
         else {
            whereStr = NotificationTable.COLUMN_NAME_GROUP_ID + " = ?";
            whereArgs = new String[]{ summaryGroup };
         }

         if (!dismissed) {
            // Make sure when a notification is not being dismissed it is handled through the dashboard setting
            boolean shouldDismissAll = OneSignal.getClearGroupSummaryClick();
            if (!shouldDismissAll) {
               /* If the open event shouldn't clear all summary notifications then the SQL query
                * will look for the most recent notification instead of all grouped notifications */
               String mostRecentId = String.valueOf(OneSignalNotificationManager.getMostRecentNotifIdFromGroup(writableDb, summaryGroup, isGroupless));
               whereStr += " AND " + NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID + " = ?";
               whereArgs = isGroupless ?
                       new String[]{ mostRecentId } :
                       new String[]{ summaryGroup, mostRecentId };
            }
         }
      } else
         whereStr = NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID + " = " + intent.getIntExtra(BUNDLE_KEY_ANDROID_NOTIFICATION_ID, 0);

      clearStatusBarNotifications(context, writableDb, summaryGroup);
      writableDb.update(NotificationTable.TABLE_NAME, newContentValuesWithConsumed(intent), whereStr, whereArgs);
      BadgeCountUpdater.update(writableDb, context);
   }

   /**
    * Handles clearing the status bar notifications when opened
    */
   private static void clearStatusBarNotifications(Context context, OneSignalDbHelper writableDb, String summaryGroup) {
      // Handling for clearing the notification when opened
      if (summaryGroup != null)
         NotificationSummaryManager.clearNotificationOnSummaryClick(context, writableDb, summaryGroup);
      else {
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // The summary group is null, represents the last notification in the groupless group
            // Check that no more groupless notifications exist in the group and cancel the group
            int grouplessCount = OneSignalNotificationManager.getGrouplessNotifsCount(context);
            if (grouplessCount < 1) {
               int groupId = OneSignalNotificationManager.getGrouplessSummaryId();
               NotificationManager notificationManager = OneSignalNotificationManager.getNotificationManager(context);
               notificationManager.cancel(groupId);
            }
         }
      }
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
