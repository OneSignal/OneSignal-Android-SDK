/**
 * Modified MIT License
 *
 * Copyright 2017 OneSignal
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
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

class ActivityLifecycleHandler {

   static boolean nextResumeIsFirstActivity;

   interface ActivityAvailableListener {
      void available(Activity activity);
   }

   static Activity curActivity;
   private static ActivityAvailableListener mActivityAvailableListener;
   static FocusHandlerThread focusHandlerThread = new FocusHandlerThread();

   // Note: Only supports one callback, create a list when this needs to be used by more than the permissions dialog.
   static void setActivityAvailableListener(ActivityAvailableListener activityAvailableListener) {
      if (curActivity != null) {
         activityAvailableListener.available(curActivity);
         mActivityAvailableListener = activityAvailableListener;
      }
      else
         mActivityAvailableListener = activityAvailableListener;
   }

   public static void removeActivityAvailableListener(ActivityAvailableListener activityAvailableListener) {
      mActivityAvailableListener = null;
   }

   private static void setCurActivity(Activity activity) {
      curActivity = activity;
      if (mActivityAvailableListener != null)
         mActivityAvailableListener.available(curActivity);
   }

   static void onActivityCreated(Activity activity) {}
   static void onActivityStarted(Activity activity) {}

   static void onActivityResumed(Activity activity) {
      setCurActivity(activity);

      logCurActivity();
      handleFocus();
   }

   static void onActivityPaused(Activity activity) {
      if (activity == curActivity) {
         curActivity = null;
         handleLostFocus();
      }

      logCurActivity();
   }

   static void onActivityStopped(Activity activity) {
      OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "onActivityStopped: " + activity.getClass().getName());

      if (activity == curActivity) {
         curActivity = null;
         handleLostFocus();
      }

      logCurActivity();
   }

   static void onActivityDestroyed(Activity activity) {
      OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "onActivityDestroyed: " + activity.getClass().getName());

      if (activity == curActivity) {
         curActivity = null;
         handleLostFocus();
      }

      logCurActivity();
   }

   static private void logCurActivity() {
      OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "curActivity is NOW: " + (curActivity != null ? "" + curActivity.getClass().getName() + ":" + curActivity : "null"));
   }

   static private void handleLostFocus() {
      focusHandlerThread.runRunnable(new AppFocusRunnable());
   }

   static private void handleFocus() {
      if (focusHandlerThread.hasBackgrounded() || nextResumeIsFirstActivity) {
         nextResumeIsFirstActivity = false;
         focusHandlerThread.resetBackgroundState();
         OneSignal.onAppFocus();
      }
      else
         focusHandlerThread.stopScheduledRunnable();
   }

   static class FocusHandlerThread extends HandlerThread {
      Handler mHandler = null;
      private AppFocusRunnable appFocusRunnable;

      FocusHandlerThread() {
         super("FocusHandlerThread");
         start();
         mHandler = new Handler(getLooper());
      }

      Looper getHandlerLooper() {
         return  mHandler.getLooper();
      }

      void resetBackgroundState() {
         if (appFocusRunnable != null)
            appFocusRunnable.backgrounded = false;
      }

      void stopScheduledRunnable() {
         mHandler.removeCallbacksAndMessages(null);
      }

      void runRunnable(AppFocusRunnable runnable) {
         if (appFocusRunnable != null && appFocusRunnable.backgrounded && !appFocusRunnable.completed)
            return;

         appFocusRunnable = runnable;
         mHandler.removeCallbacksAndMessages(null);
         mHandler.postDelayed(runnable, 2000);
      }

      boolean hasBackgrounded() {
         return appFocusRunnable != null && appFocusRunnable.backgrounded;
      }
   }

   static private class AppFocusRunnable implements Runnable {
      private boolean backgrounded, completed;

      public void run() {
         if (curActivity != null)
            return;

         backgrounded = true;
         OneSignal.onAppLostFocus();
         completed = true;
      }
   }
}