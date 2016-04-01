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

public class SyncService extends Service {

   private void checkOnFocusSync() {
      long unsentTime = OneSignal.GetUnsentActiveTime();
      if (unsentTime < OneSignal.MIN_ON_FOCUS_TIME)
         return;

      OneSignal.sendOnFocus(unsentTime, true);
   }

   @Override
   public void onCreate() {
      // If service was started from outside the app.
      if (OneSignal.appContext == null) {
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
   }

   @Override
   public int onStartCommand(Intent intent, int flags, int startId) {
      return START_STICKY;
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


   /* Always make sure we are shutting down as quickly as possible here! We are on the main thread and have 20 sec before the process is forcefully killed.
      Also if the user reopens the app or there is another task still open on the same process it will hang the startup/UI there.
    */
   static void onTaskRemoved() {
      ActivityLifecycleHandler.focusHandlerThread.stopScheduledRunnable();
      OneSignalStateSynchronizer.stopAndPersist();
      OneSignal.onAppLostFocus(true); // Save only
   }
}
