package com.onesignal.example;

import android.app.Application;
import android.os.StrictMode;
import android.util.Log;

import com.onesignal.OneSignal;

import org.json.JSONObject;

public class OneSignalExampleApp extends Application {

   @Override
   public void onCreate() {
      super.onCreate();

      StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().detectAll().build());
      StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().detectAll().build());

      OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE);
      OneSignal.startInit(this)
          .setAutoPromptLocation(true)
          .setNotificationOpenedHandler(new ExampleNotificationOpenedHandler())
          .init();
   }

   private class ExampleNotificationOpenedHandler implements OneSignal.NotificationOpenedHandler {
      /**
       * Callback to implement in your app to handle when a notification is opened from the Android status bar or
       * a new one comes in while the app is running.
       * This method is located in this activity as an example, you may have any class you wish implement NotificationOpenedHandler and define this method.
       *
       * @param message        The message string the user seen/should see in the Android status bar.
       * @param additionalData The additionalData key value pair section you entered in on onesignal.com.
       * @param isActive       Was the app in the foreground when the notification was received.
       */
      @Override
      public void notificationOpened(String message, JSONObject additionalData, boolean isActive) {
         Log.e("OneSignalExample", "message: " + message);
         Log.e("OneSignalExample", "additional data: " + additionalData);
         //Log.e("OneSignalExample", "additionalData: " + additionalData.toString());
      }
   }
}