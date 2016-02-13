package com.onesignal;

import android.os.Looper;

import org.robolectric.util.Scheduler;

import java.util.Map;

import static org.robolectric.Shadows.shadowOf;

public class OneSignalPackagePrivateHelper {
   public static void runAllNetworkRunnables() {
      for (Map.Entry<Integer, OneSignalStateSynchronizer.NetworkHandlerThread> handlerThread : OneSignalStateSynchronizer.networkHandlerThreads.entrySet()) {
         Scheduler scheduler = shadowOf(handlerThread.getValue().getLooper()).getScheduler();
         while (scheduler.advanceToNextPostedRunnable()) {}
      }
   }

   public static void runFocusRunnables() {
      Looper looper = ActivityLifecycleHandler.focusHandlerThread.getHandlerLooper();
      if (looper == null) return;
      
      Scheduler scheduler = shadowOf(looper).getScheduler();
      while (scheduler.advanceToNextPostedRunnable()) {}
   }

   public static void resetRunnables() {
      for (Map.Entry<Integer, OneSignalStateSynchronizer.NetworkHandlerThread> handlerThread : OneSignalStateSynchronizer.networkHandlerThreads.entrySet())
         handlerThread.getValue().stopScheduledRunnable();

      Looper looper = ActivityLifecycleHandler.focusHandlerThread.getHandlerLooper();
      if (looper == null) return;

      shadowOf(looper).reset();
   }

   public static void SyncService_onTaskRemoved() {
      SyncService.onTaskRemoved();
   }


   public class NotificationTable extends OneSignalDbContract.NotificationTable { }
}
