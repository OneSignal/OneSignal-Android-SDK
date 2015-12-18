package com.onesignal.example;

import android.app.Application;
import android.os.StrictMode;

import com.onesignal.OneSignal;

public class OneSignalExampleApp extends Application {

   @Override
   public void onCreate() {
      super.onCreate();

      StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().detectAll().build());
      StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().detectAll().build());

      OneSignal.setLogLevel(OneSignal.LOG_LEVEL.DEBUG, OneSignal.LOG_LEVEL.NONE);
      OneSignal.startInit(this)
          .setAutoPromptLocation(true)
//          .setNotificationOpenedHandler(new ExampleNotificationOpenedHandler())
          .init();
   }
}
