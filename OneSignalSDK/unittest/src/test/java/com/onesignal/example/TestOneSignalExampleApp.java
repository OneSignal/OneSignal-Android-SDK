package com.onesignal.example;

import org.robolectric.TestLifecycleApplication;

import java.lang.reflect.Method;


public class TestOneSignalExampleApp extends OneSignalExampleApp implements TestLifecycleApplication {

   @Override
   public void onCreate() {
      // Override our normal OneSignalExampleApp onCreate so it does not interfere with our tests.
   }

   @Override
   public void beforeTest(Method method) {
   }

   @Override
   public void prepareTest(Object test) {
   }

   @Override
   public void afterTest(Method method) {
   }
}
