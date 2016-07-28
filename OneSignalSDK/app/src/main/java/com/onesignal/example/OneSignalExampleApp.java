package com.onesignal.example;

import android.app.Application;
import android.os.StrictMode;
import android.util.Log;

import com.onesignal.OSNotification;
import com.onesignal.OSNotificationOpenResult;
import com.onesignal.OneSignal;

public class OneSignalExampleApp extends Application {

   @Override
   public void onCreate() {
      super.onCreate();

      StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().detectAll().build());
      StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().detectAll().build());

      OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE);
      OneSignal.startInit(this)
//          .autoPromptLocation(true)
          .setNotificationOpenedHandler(new ExampleNotificationOpenedHandler())
          .init();
   }

   private class ExampleNotificationOpenedHandler implements OneSignal.NotificationOpenedHandler {
      /**
       * Callback to implement in your app to handle when a notification is opened from the Android status bar or
       * a new one comes in while the app is running.
       * This method is located in this activity as an example, you may have any class you wish implement NotificationOpenedHandler and define this method.
       *
       * @param openedResult The message string the user seen/should see in the Android status bar.
       */
      @Override
      public void notificationOpened(OSNotificationOpenResult openedResult) {
         Log.e("OneSignalExample", "body: " + openedResult.notification.payload.body);
         Log.e("OneSignalExample", "additional data: " + openedResult.notification.payload.additionalData);
         //Log.e("OneSignalExample", "additionalData: " + additionalData.toString());
      }
   }
}