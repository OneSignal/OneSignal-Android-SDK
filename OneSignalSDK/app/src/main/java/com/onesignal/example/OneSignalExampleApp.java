package com.onesignal.example;

import android.app.Application;

import com.onesignal.OneSignal;

public class OneSignalExampleApp extends Application {

   @Override
   public void onCreate() {
      super.onCreate();
      OneSignal.setLogLevel(OneSignal.LOG_LEVEL.DEBUG, OneSignal.LOG_LEVEL.NONE);
      OneSignal.startInit(this)
          .setAutoPromptLocation(true)
//          .setNotificationOpenedHandler(new ExampleNotificationOpenedHandler())
          .init();
   }
}
