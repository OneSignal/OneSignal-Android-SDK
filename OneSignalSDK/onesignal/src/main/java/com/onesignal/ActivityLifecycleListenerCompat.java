/**
 * Modified MIT License
 *
 * Copyright 2016 OneSignal
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * 1. The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * 2. All copies of substantial portions of the Software may only be used in connection
 * with services provided by OneSignal.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.onesignal;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.OnActivityPausedListener;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

// registerActivityLifecycleCallbacks equivalent for devices older then 4.0 (API 14)
class ActivityLifecycleListenerCompat {

   static void startListener() {
      try {
         final Class activityThreadClass = Class.forName("android.app.ActivityThread");
         final Object activityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null);

         Field instrumentationField = activityThreadClass.getDeclaredField("mInstrumentation");
         instrumentationField.setAccessible(true);
         Instrumentation instrumentation =  (Instrumentation)instrumentationField.get(activityThread);
         final Instrumentation.ActivityMonitor allActivitiesMonitor = instrumentation.addMonitor((String)null, null, false);

         startMonitorThread(activityThreadClass, activityThread, allActivitiesMonitor);
      } catch (Throwable t) {
         t.printStackTrace();
      }
   }

   private static void startMonitorThread(final Class activityThreadClass, final Object activityThread, final Instrumentation.ActivityMonitor allActivitiesMonitor) {
      new Thread(new Runnable() {
         @Override
         public void run() {
            try {
               OnActivityPausedListener pausedListener = new OnActivityPausedListener() {
                  @Override
                  public void onPaused(Activity activity) {
                     ActivityLifecycleHandler.onActivityPaused(activity);
                  }
               };
               Method registerOnActivityPausedListener = activityThreadClass.getMethod("registerOnActivityPausedListener", Activity.class, OnActivityPausedListener.class);

               while (true) {
                  // Wait for new activity events, does not fire for pauses through.
                  // waitForActivity should also return for onStop and onDestroy according to android/app/Instrumentation.java but this is not happening on a real device.
                  Activity currentActivity = allActivitiesMonitor.waitForActivity();

                  if (!currentActivity.isFinishing()) {
                     ActivityLifecycleHandler.onActivityResumed(currentActivity);
                     registerOnActivityPausedListener.invoke(activityThread, currentActivity, pausedListener);
                  }
               }
            } catch (Throwable t) {
               t.printStackTrace();
            }
         }
      }).start();
   }
}