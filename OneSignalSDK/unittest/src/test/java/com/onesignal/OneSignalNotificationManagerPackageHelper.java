package com.onesignal;

import android.app.NotificationManager;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.service.notification.StatusBarNotification;
import android.support.annotation.RequiresApi;

public class OneSignalNotificationManagerPackageHelper {

   private static abstract class RunnableArg<T> {
      abstract void run(T object) throws Exception;
   }

   public static String getGrouplessSummaryKey() {
      return OneSignalNotificationManager.getGrouplessSummaryKey();
   }

   public static NotificationManager getNotificationManager(Context context) {
      return OneSignalNotificationManager.getNotificationManager(context);
   }

   @RequiresApi(api = Build.VERSION_CODES.M)
   public static StatusBarNotification[] getActiveNotifications(Context context) {
      return getNotificationManager(context).getActiveNotifications();
   }

   public static Integer getMostRecentNotifIdFromGroup(SQLiteDatabase db, String group, boolean isGroupless) {
      return OneSignalNotificationManager.getMostRecentNotifIdFromGroup(db, group, isGroupless);
   }

}
