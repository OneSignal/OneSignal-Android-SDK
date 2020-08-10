package com.onesignal;

import android.app.Notification;
import android.content.Context;
import android.database.Cursor;
import android.os.Build;
import android.service.notification.StatusBarNotification;
import android.support.annotation.RequiresApi;

import com.onesignal.OneSignalDbContract.NotificationTable;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

// Ensures old notifications are cleared up to a limit before displaying new ones
class NotificationLimitManager {

   // Android does not allow a package to have more than 49 total notifications being shown.
   //   This limit prevents the following error;
   // E/NotificationService: Package has already posted 50 notifications.
   //                        Not showing more.  package=####
   // Even though it says 50 in the error it is really a limit of 49.
   // See NotificationManagerService.java in the AOSP source
   //
   private static final int MAX_NUMBER_OF_NOTIFICATIONS_INT = 49;
   static final String MAX_NUMBER_OF_NOTIFICATIONS_STR = Integer.toString(MAX_NUMBER_OF_NOTIFICATIONS_INT);

   private static int getMaxNumberOfNotificationsInt() {
      return MAX_NUMBER_OF_NOTIFICATIONS_INT;
   }

   private static String getMaxNumberOfNotificationsString() {
      return MAX_NUMBER_OF_NOTIFICATIONS_STR;
   }

   // Used to cancel the oldest notifications to make room for new notifications we are about to display
   // If we don't make this room users will NOT be alerted of new notifications for the app.
   static void clearOldestOverLimit(Context context, int notifsToMakeRoomFor) {
      try {
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            clearOldestOverLimitStandard(context, notifsToMakeRoomFor);
         else
            clearOldestOverLimitFallback(context, notifsToMakeRoomFor);
      } catch(Throwable t) {
         // try-catch for Android 6.0.X and possibly 8.0.0 bug work around, getActiveNotifications bug
         clearOldestOverLimitFallback(context, notifsToMakeRoomFor);
      }
   }

   // Cancel the oldest notifications based on what the Android system reports is in the shade.
   // This could be any notification, not just a OneSignal notification
   @RequiresApi(api = Build.VERSION_CODES.M)
   static void clearOldestOverLimitStandard(Context context, int notifsToMakeRoomFor) throws Throwable {
      StatusBarNotification[] activeNotifs = OneSignalNotificationManager.getActiveNotifications(context);

      int notifsToClear = (activeNotifs.length - getMaxNumberOfNotificationsInt()) + notifsToMakeRoomFor;
      // We have enough room in the notification shade, no need to clear any notifications
      if (notifsToClear < 1)
         return;

      // Create SortedMap so we can sort notifications based on display time
      SortedMap<Long, Integer> activeNotifIds = new TreeMap<>();
      for (StatusBarNotification activeNotif : activeNotifs) {
         if (isGroupSummary(activeNotif))
            continue;
         activeNotifIds.put(activeNotif.getNotification().when, activeNotif.getId());
      }

      // Clear the oldest based on the count in notifsToClear
      for(Map.Entry<Long, Integer> mapData : activeNotifIds.entrySet()) {
         OneSignal.cancelNotification(mapData.getValue());
         if (--notifsToClear <= 0)
            break;
      }
   }

   // This cancels any notifications based on the oldest in the local SQL database
   static void clearOldestOverLimitFallback(Context context, int notifsToMakeRoomFor) {
      OneSignalDbHelper dbHelper = OneSignalDbHelper.getInstance(context);

      Cursor cursor = null;
      try {
         cursor = dbHelper.query(
            NotificationTable.TABLE_NAME,
            new String[] { NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID },
            OneSignalDbHelper.recentUninteractedWithNotificationsWhere().toString(),
            null,
            null,
            null,
            OneSignalDbContract.NotificationTable._ID, // sort order, old to new
            getMaxNumberOfNotificationsString() + notifsToMakeRoomFor // limit
         );

         int notifsToClear = (cursor.getCount() - getMaxNumberOfNotificationsInt()) + notifsToMakeRoomFor;
         // We have enough room in the notification shade, no need to clear any notifications
         if (notifsToClear < 1)
            return;

         while (cursor.moveToNext()) {
            int existingId = cursor.getInt(cursor.getColumnIndex(NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID));
            OneSignal.cancelNotification(existingId);

            if (--notifsToClear <= 0)
               break;
         }
      } catch (Throwable t) {
         OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error clearing oldest notifications over limit! ", t);
      } finally {
         if (cursor != null && !cursor.isClosed())
            cursor.close();
      }
   }

   @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
   static boolean isGroupSummary(StatusBarNotification notif) {
      return (notif.getNotification().flags & Notification.FLAG_GROUP_SUMMARY) != 0;
   }
}
