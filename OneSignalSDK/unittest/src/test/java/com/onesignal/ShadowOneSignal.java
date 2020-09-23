package com.onesignal;

import android.util.Log;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

@Implements(OneSignal.class)
public class ShadowOneSignal {

   public static String messages = "";

   public static void Log(final OneSignal.LOG_LEVEL level, String message, Throwable throwable) {
      messages += message;
      Log.e("", message, throwable);
   }

   @Implementation
   public static void fireNotificationOpenedHandler(final OSNotificationOpenedResult openedResult) {
      OneSignal.notificationOpenedHandler.notificationOpened(openedResult);
   }
}
