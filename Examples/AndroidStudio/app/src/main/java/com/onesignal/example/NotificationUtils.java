package com.onesignal.example;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Build;
import android.service.notification.StatusBarNotification;
import android.support.annotation.RequiresApi;

import java.util.TreeMap;

class NotificationUtils {

   static class ChildNotification {
      int id;
      PendingIntent contentIntent;

      ChildNotification(int id, PendingIntent contentIntent) {
         this.id = id; this.contentIntent = contentIntent;
      }
   }

   @RequiresApi(api = Build.VERSION_CODES.M)
   static TreeMap<Long, ChildNotification> getTreeMapOfChildNotifications(Context context) {
      NotificationManager notifManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
      StatusBarNotification[] activeNotifs = notifManager.getActiveNotifications();

      // Create SortedMap so we can sort notifications based on display time
      TreeMap<Long, ChildNotification> activeChildNotifs = new TreeMap<>();
      for (StatusBarNotification activeNotif : activeNotifs) {
         if (isGroupSummary(activeNotif))
            continue;
         ChildNotification childNotification = new ChildNotification(activeNotif.getId(), activeNotif.getNotification().contentIntent);
         activeChildNotifs.put(activeNotif.getNotification().when, childNotification);
      }

      return activeChildNotifs;
   }

   @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
   static boolean isGroupSummary(StatusBarNotification notif) {
      return (notif.getNotification().flags & Notification.FLAG_GROUP_SUMMARY) != 0;
   }

   static void cancelNotification(Context context, int id) {
      NotificationManager notifManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
      notifManager.cancel(id);
   }

   // Call from either your NotificationOpenedHandler or each time your app is resumed
   static void cancelSummaryNotificationIfNoChildren(Context context) {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
         return;

      if (getTreeMapOfChildNotifications(context).size() == 0)
         cancelNotification(context, NotificationExtenderExample.NOTIFICATION_GROUP_ID);
   }
}
