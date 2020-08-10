package com.onesignal;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.service.notification.StatusBarNotification;
import android.support.annotation.RequiresApi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.robolectric.util.Scheduler;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

import static org.robolectric.Shadows.shadowOf;

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

   public static Integer getMostRecentNotifIdFromGroup(OneSignalDbHelper db, String group, boolean isGroupless) {
      return OneSignalNotificationManager.getMostRecentNotifIdFromGroup(db, group, isGroupless);
   }

}
