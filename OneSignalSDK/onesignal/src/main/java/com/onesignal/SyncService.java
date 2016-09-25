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

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;

// This Service ensure tags, session data, etc are not lost by saving it to local storage before closing.
//  It then starts again if there is un-synced data that needs to be posted to OneSignal.
// The service is stopped with stopSelf() once completed.

public class SyncService extends Service {

   private void checkOnFocusSync() {
      long unsentTime = OneSignal.GetUnsentActiveTime();
      if (unsentTime < OneSignal.MIN_ON_FOCUS_TIME)
         return;

      OneSignal.sendOnFocus(unsentTime, true);
   }

   @Override
   public void onCreate() {
      if (OneSignal.startedSyncService)
         return;

      OneSignal.appContext = this.getApplicationContext();
      new Thread(new Runnable() {
         @Override
         public void run() {
            if (OneSignal.getUserId() == null) {
               stopSelf();
               return;
            }

            OneSignal.appId = OneSignal.getSavedAppId();

            OneSignalStateSynchronizer.initUserState(OneSignal.appContext);
            OneSignalStateSynchronizer.syncUserState(true);
            checkOnFocusSync();

            stopSelf();
         }
      }).start();
   }

   @Override
   public int onStartCommand(Intent intent, int flags, int startId) {
      // Starts sticky only if the app has shown an Activity.
      return OneSignal.startedSyncService ? START_STICKY : START_NOT_STICKY;
   }

   // This Service does not support bindings.
   @Nullable
   @Override
   public IBinder onBind(Intent intent) {
      return null;
   }


   // Called by Android when a user swipes a way a task. On Android 4.1+ the process will be killed shortly after.
   // However there is a rare case where if the process has 2 tasks that a both swiped away this will be called right after onCreate.
   @Override
   public void onTaskRemoved(Intent rootIntent) {
      super.onTaskRemoved(rootIntent);
      onTaskRemoved();
   }

   // NOTE: Currently onTaskRemoved takes about 100ms to run.
   // Please maintain this efficiency as it runs on the main thread!
   //   This is important as while this method is running the app will not be response if
   //   the user reopens or focuses another tasks part of the same process.
   // Also the process will be killed forcefully if this does not finish in 20 seconds.
   // Method behavior seems unaffected by the android:stopWithTask manifest entry.
   //   false is not required, tested on Android 4.4.2 and 6.0.1
   static void onTaskRemoved() {
      OneSignal.Log(OneSignal.LOG_LEVEL.VERBOSE, "Starting SyncService:onTaskRemoved.");
      ActivityLifecycleHandler.focusHandlerThread.stopScheduledRunnable();
      OneSignalStateSynchronizer.stopAndPersist();
      OneSignal.onAppLostFocus(true); // Save only
      OneSignal.Log(OneSignal.LOG_LEVEL.VERBOSE, "Completed SyncService:onTaskRemoved.");
   }
}
