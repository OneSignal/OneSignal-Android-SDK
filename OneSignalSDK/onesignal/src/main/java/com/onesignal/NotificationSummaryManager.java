package com.onesignal;

import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import com.onesignal.OneSignalDbContract.NotificationTable;

import org.json.JSONException;
import org.json.JSONObject;

class NotificationSummaryManager {
   
   // A notification was just dismissed, check if it was a child to a summary notification and update it.
   static void updatePossibleDependentSummaryOnDismiss(Context context, OneSignalDb db, int androidNotificationId) {
      Cursor cursor = db.query(
          NotificationTable.TABLE_NAME,
          new String[] { NotificationTable.COLUMN_NAME_GROUP_ID }, // retColumn
          NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID + " = " + androidNotificationId,
          null, null, null, null);
      
      if (cursor.moveToFirst()) {
         String group = cursor.getString(cursor.getColumnIndex(NotificationTable.COLUMN_NAME_GROUP_ID));
         cursor.close();
         
         if (group != null)
            updateSummaryNotificationAfterChildRemoved(context, db, group, true);
      }
      else
         cursor.close();
   }
   
   // Called from an opened / dismissed / cancel event of a single notification to update it's parent the summary notification.
   static void updateSummaryNotificationAfterChildRemoved(Context context, OneSignalDb db, String group, boolean dismissed) {
      Cursor cursor = null;
      try {
         cursor = internalUpdateSummaryNotificationAfterChildRemoved(context, db, group, dismissed);
      } catch (Throwable t) {
         OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error running updateSummaryNotificationAfterChildRemoved!", t);
      } finally {
         if (cursor != null && !cursor.isClosed())
            cursor.close();
      }
   }

   private static Cursor internalUpdateSummaryNotificationAfterChildRemoved(Context context, OneSignalDb db, String group, boolean dismissed) {
      Cursor cursor = db.query(
          NotificationTable.TABLE_NAME,
          new String[] {
              NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID,
              NotificationTable.COLUMN_NAME_CREATED_TIME,
              NotificationTable.COLUMN_NAME_FULL_DATA,
          },
          NotificationTable.COLUMN_NAME_GROUP_ID + " = ? AND " + // Where String
              NotificationTable.COLUMN_NAME_DISMISSED + " = 0 AND " +
              NotificationTable.COLUMN_NAME_OPENED + " = 0 AND " +
              NotificationTable.COLUMN_NAME_IS_SUMMARY + " = 0" ,
          new String[] { group }, // whereArgs
          null, null,
          NotificationTable._ID + " DESC");   // sort order, new to old);
   
      int notificationsInGroup = cursor.getCount();
   
      // If all individual notifications consumed
      //   - Remove summary notification from the shade.
      //   - Mark summary notification as consumed.
      if (notificationsInGroup == 0) {
         cursor.close();
   
         Integer androidNotifId = getSummaryNotificationId(db, group);
         if (androidNotifId == null)
            return cursor;
      
         // Remove the summary notification from the shade.
         NotificationManager notificationManager = OneSignalNotificationManager.getNotificationManager(context);
         notificationManager.cancel(androidNotifId);
      
         // Mark the summary notification as opened or dismissed.
         ContentValues values = new ContentValues();
         values.put(dismissed ? NotificationTable.COLUMN_NAME_DISMISSED : NotificationTable.COLUMN_NAME_OPENED, 1);
         db.update(NotificationTable.TABLE_NAME,
             values,
             NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID + " = " + androidNotifId,
             null);
         return cursor;
      }
   
      // Only a single notification now in the group
      //   - Need to recreate a summary notification so it looks like a normal notifications since we
      //        only have one notification now.
      if (notificationsInGroup == 1) {
         cursor.close();
         Integer androidNotifId = getSummaryNotificationId(db, group);
         if (androidNotifId == null)
            return cursor;
         restoreSummary(context, group);
         return cursor;
      }
      
      // 2 or more still left in the group
      //  - Just need to update the summary notification.
      //  - Don't need start a broadcast / service as the extender doesn't support overriding
      //      the summary notification.
      try {
         cursor.moveToFirst();
         Long datetime = cursor.getLong(cursor.getColumnIndex(NotificationTable.COLUMN_NAME_CREATED_TIME));
         String jsonStr = cursor.getString(cursor.getColumnIndex(NotificationTable.COLUMN_NAME_FULL_DATA));
         cursor.close();

         Integer androidNotifId = getSummaryNotificationId(db, group);
         if (androidNotifId == null)
            return cursor;

         OSNotificationGenerationJob notificationJob = new OSNotificationGenerationJob(context);
         notificationJob.setRestoring(true);
         notificationJob.setShownTimeStamp(datetime);
         notificationJob.setJsonPayload(new JSONObject(jsonStr));

         GenerateNotification.updateSummaryNotification(notificationJob);
      } catch (JSONException e) {
         e.printStackTrace();
      }
      
      return cursor;
   }
   
   private static void restoreSummary(Context context, String group) {
      OneSignalDbHelper dbHelper = OneSignalDbHelper.getInstance(context);
      
      Cursor cursor = null;
      
      String[] whereArgs = { group };
      
      try {
         cursor = dbHelper.query(
             NotificationTable.TABLE_NAME,
             OSNotificationRestoreWorkManager.COLUMNS_FOR_RESTORE,
            NotificationTable.COLUMN_NAME_GROUP_ID + " = ? AND " +
             NotificationTable.COLUMN_NAME_DISMISSED + " = 0 AND " +
             NotificationTable.COLUMN_NAME_OPENED + " = 0 AND " +
             NotificationTable.COLUMN_NAME_IS_SUMMARY + " = 0",
             whereArgs,
             null,                           // group by
             null,                            // filter by row groups
             null
         );

         OSNotificationRestoreWorkManager.showNotificationsFromCursor(context, cursor, 0);
      } catch (Throwable t) {
         OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error restoring notification records! ", t);
      } finally {
         if (cursor != null && !cursor.isClosed())
            cursor.close();
      }
   }
   
   static Integer getSummaryNotificationId(OneSignalDb db, String group) {
      Integer androidNotifId = null;
      Cursor cursor = null;

      String whereStr = NotificationTable.COLUMN_NAME_GROUP_ID + " = ? AND " +
              NotificationTable.COLUMN_NAME_DISMISSED + " = 0 AND " +
              NotificationTable.COLUMN_NAME_OPENED + " = 0 AND " +
              NotificationTable.COLUMN_NAME_IS_SUMMARY + " = 1";
      String[] whereArgs = new String[] { group };

      try {
         // Get the Android Notification ID of the summary notification
         cursor = db.query(
             NotificationTable.TABLE_NAME,
                 new String[] { NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID }, // retColumn
                 whereStr,
                 whereArgs,
                 null,
                 null,
                 null);
      
         boolean hasRecord = cursor.moveToFirst();
         if (!hasRecord) {
            cursor.close();
            return null;
         }
         androidNotifId = cursor.getInt(cursor.getColumnIndex(NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID));
         cursor.close();
      } catch (Throwable t) {
         OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error getting android notification id for summary notification group: " + group, t);
      } finally {
         if (cursor != null && !cursor.isClosed())
            cursor.close();
      }
      
      return androidNotifId;
   }

   /**
    * Clears notifications from the status bar based on a few parameters
    */
   static void clearNotificationOnSummaryClick(Context context, OneSignalDbHelper dbHelper, String group) {
      // Obtain the group to clear notifications from
      Integer groupId = NotificationSummaryManager.getSummaryNotificationId(dbHelper, group);
      boolean isGroupless = group.equals(OneSignalNotificationManager.getGrouplessSummaryKey());

      NotificationManager notificationManager = OneSignalNotificationManager.getNotificationManager(context);
      // Obtain the most recent notification id
      Integer mostRecentId = OneSignalNotificationManager.getMostRecentNotifIdFromGroup(dbHelper, group, isGroupless);
      if (mostRecentId != null) {
         boolean shouldDismissAll = OneSignal.getClearGroupSummaryClick();
         if (shouldDismissAll) {

            // If the group is groupless, obtain the hardcoded groupless summary id
            if (isGroupless)
               groupId = OneSignalNotificationManager.getGrouplessSummaryId();

            // Clear the entire notification summary
            if (groupId != null)
               notificationManager.cancel(groupId);
         } else {
            // Clear the most recent notification from the status bar summary
            OneSignal.removeNotification(mostRecentId);
         }
      }
   }
}