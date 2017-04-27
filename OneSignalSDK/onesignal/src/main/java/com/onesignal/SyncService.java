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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;

// This Service ensure tags, session data, etc are not lost by saving it to local storage before closing.
//  It then starts again if there is un-synced data that needs to be posted to OneSignal.
// The service is stopped with stopSelf() once completed.

public class SyncService extends Service {
   
   static final int TASK_APP_STARTUP = 0;
   static final int TASK_SYNC = 1;
   private static boolean startedFromActivity;
   
   private static void checkOnFocusSync() {
      long unsentTime = OneSignal.GetUnsentActiveTime();
      if (unsentTime < OneSignal.MIN_ON_FOCUS_TIME)
         return;

      OneSignal.sendOnFocus(unsentTime, true);
   }
   
   private void doSync() {
      if (startedFromActivity)
         doForegroundSync();
      else
         doBackgroundSync();
   }
   
   private void doForegroundSync() {
      LocationGMS.getLocation(this, false,  new LocationGMS.LocationHandler() {
         public void complete(LocationGMS.LocationPoint point) {
            if (point != null)
               OneSignalStateSynchronizer.updateLocation(point);
         }
      });
   }
   
   private void doBackgroundSync() {
      OneSignal.appContext = getApplicationContext();
      new Thread(new Runnable() {
         @Override
         public void run() {
            if (OneSignal.getUserId() == null) {
               stopSelf();
               return;
            }
   
            OneSignal.appId = OneSignal.getSavedAppId();
            OneSignalStateSynchronizer.initUserState(OneSignal.appContext);
            
            LocationGMS.getLocation(OneSignal.appContext, false, new LocationGMS.LocationHandler() {
               @Override
               public void complete(LocationGMS.LocationPoint point) {
                  if (point != null)
                     OneSignalStateSynchronizer.updateLocation(point);
                  
                  OneSignalStateSynchronizer.syncUserState(true);
                  checkOnFocusSync();
   
                  stopSelf();
               }
            });
         }
      }, "OS_SYNCSRV_BG_SYNC").start();
   }

   @Override
   public void onCreate() {
   }

   @Override
   public int onStartCommand(Intent intent, int flags, int startId) {
      int task;
      if (intent != null)
         task = intent.getIntExtra("task", 0);
      else
         task = TASK_SYNC;
      
      if (task == TASK_APP_STARTUP)
         startedFromActivity = true;
      else if (task == TASK_SYNC)
         doSync();
      
      // Starts sticky only if the app has shown an Activity.
      return startedFromActivity ? START_STICKY : START_NOT_STICKY;
   }

   // This Service does not support bindings.
   @Nullable
   @Override
   public IBinder onBind(Intent intent) {
      return null;
   }


   // Called by Android when a user swipes a way a task.
   // On Android 4.1+ the process will be killed shortly after.
   // However there is a rare case where if the process has 2 tasks that a both swiped away this will be called right after onCreate.
   @Override
   public void onTaskRemoved(Intent rootIntent) {
      super.onTaskRemoved(rootIntent);
      onTaskRemoved(this);
   }

   // NOTE: Currently onTaskRemoved takes about 100ms to run.
   // Please maintain this efficiency as it runs on the main thread!
   //   This is important as the app will not response if
   //   the user reopens or focuses another tasks part of the same process.
   // The Process will be killed forcefully if this does not finish in 20 seconds.
   // Triggering seems unaffected by the presents or absence of android:stopWithTask="false".
   //   false is not required, tested on Android 4.4.2 and 6.0.1
   static void onTaskRemoved(Service service) {
      OneSignal.Log(OneSignal.LOG_LEVEL.VERBOSE, "Starting SyncService:onTaskRemoved.");
      
      ActivityLifecycleHandler.focusHandlerThread.stopScheduledRunnable();
      
      boolean scheduleServerRestart;
   
      scheduleServerRestart = OneSignalStateSynchronizer.stopAndPersist();
      scheduleServerRestart = OneSignal.onAppLostFocus(true) || scheduleServerRestart; // Save only
      
      OneSignal.Log(OneSignal.LOG_LEVEL.VERBOSE, "Completed SyncService:onTaskRemoved.");
   
      // stopSelf is important otherwise Android will show "Scheduling restart of crashed service"
      //   in the logcat which may have other side-affects.
      service.stopSelf();
      
      if (scheduleServerRestart)
         scheduleServiceSyncTask(service, System.currentTimeMillis() + 10000);
      else
         LocationGMS.scheduleUpdate(service);
   }

   static void scheduleServiceSyncTask(Context context, long atTime) {
      OneSignal.Log(OneSignal.LOG_LEVEL.VERBOSE, "scheduleServiceSyncTask:atTime: " + atTime);
      
      Intent intent = new Intent(context, SyncService.class);
      intent.putExtra("task", TASK_SYNC);
      
      // KEEP - PendingIntent.FLAG_UPDATE_CURRENT
      //    Some Samsung devices will throw the below exception otherwise.
      //    "java.lang.SecurityException: !@Too many alarms (500) registered"
      
      PendingIntent pendingIntent = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
      AlarmManager alarm = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
      alarm.set(AlarmManager.RTC_WAKEUP, atTime, pendingIntent);
   }
}
