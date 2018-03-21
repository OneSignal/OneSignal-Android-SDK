package com.test.onesignal;

import com.onesignal.OneSignalPackagePrivateHelper;
import com.onesignal.OneSignalPackagePrivateHelper.OneSignalPrefs;
import com.onesignal.ShadowCustomTabsClient;
import com.onesignal.ShadowFirebaseAnalytics;
import com.onesignal.ShadowFusedLocationApiWrapper;
import com.onesignal.ShadowGcmBroadcastReceiver;
import com.onesignal.ShadowGoogleApiClientCompatProxy;
import com.onesignal.ShadowNotificationManagerCompat;
import com.onesignal.ShadowOSUtils;
import com.onesignal.ShadowOneSignalRestClient;
import com.onesignal.ShadowPushRegistratorGPS;
import com.onesignal.StaticResetHelper;

import org.robolectric.util.Scheduler;

import java.util.Set;

import static org.robolectric.Shadows.shadowOf;

class TestHelpers {

   static Exception lastException;

   static void beforeTestInitAndCleanup() {

      StaticResetHelper.restSetStaticFields();

      ShadowOneSignalRestClient.resetStatics();

      ShadowPushRegistratorGPS.resetStatics();
   
      ShadowNotificationManagerCompat.enabled = true;

      ShadowOSUtils.subscribableStatus = 1;
   
      ShadowCustomTabsClient.resetStatics();
      ShadowGcmBroadcastReceiver.resetStatics();

      ShadowFusedLocationApiWrapper.resetStatics();

      ShadowFirebaseAnalytics.resetStatics();

      ShadowGoogleApiClientCompatProxy.restSetStaticFields();

      // DB seems to be cleaned up on it's own.
      /*
      SQLiteDatabase writableDb = OneSignalDbHelper.getInstance(RuntimeEnvironment.application).getWritableDatabase();
      writableDb.delete(OneSignalPackagePrivateHelper.NotificationTable.TABLE_NAME, null, null);
      writableDb.close();
      */

      lastException = null;

      OneSignalPackagePrivateHelper.OneSignalPrefs.initializePool();
   }

   static void afterTestCleanup() {
      try {
         stopAllOSThreads();
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   static void stopAllOSThreads() throws Exception {
      boolean joinedAThread;
      do {
         joinedAThread = false;
         Set<Thread> threadSet = Thread.getAllStackTraces().keySet();

         for (Thread thread : threadSet) {
            if (thread.getName().startsWith("OS_")) {
               thread.interrupt();
               joinedAThread = true;
            }
         }
      } while (joinedAThread);
   }

   static void flushBufferedSharedPrefs() {
      OneSignalPrefs.WritePrefHandlerThread handlerThread = OneSignalPackagePrivateHelper.OneSignalPrefs.prefsHandler;

      synchronized (handlerThread.mHandler) {
         Scheduler scheduler = shadowOf(handlerThread.getLooper()).getScheduler();
         while (scheduler.runOneTask());
      }
   }

   // Join all OS_ threads
   //   Returns true if we had to join any threads
   static boolean runOSThreads() throws Exception {
      boolean createdNewThread = false;
      boolean joinedAThread;
      do {
         joinedAThread = false;
         Set<Thread> threadSet = Thread.getAllStackTraces().keySet();

         for (Thread thread : threadSet) {
            if (thread.getName().startsWith("OS_")) {
               if (ShadowOneSignalRestClient.isAFrozenThread(thread))
                  continue;

               thread.join(0, 1);

               if (lastException != null)
                  throw lastException;
               joinedAThread = createdNewThread = true;
            }
         }
      } while (joinedAThread);

      return createdNewThread;
   }

   // Run any OneSignal background threads including any pending runnables
   static void threadAndTaskWait() throws Exception {
      boolean createdNewThread;
      do {
         createdNewThread = runOSThreads();
         
         boolean advancedRunnables = OneSignalPackagePrivateHelper.runAllNetworkRunnables();
         advancedRunnables = OneSignalPackagePrivateHelper.runFocusRunnables() || advancedRunnables;
         
         if (advancedRunnables)
            createdNewThread = true;
      } while (createdNewThread);

      if (lastException != null)
         throw lastException;
   }

   private static boolean ranBeforeTestSuite;
   static void beforeTestSuite() {
      if (ranBeforeTestSuite)
         return;

      beforeTestInitAndCleanup();

      System.out.println("beforeTestSuite!!!!!!");

      // Setup process global exception handler to catch any silent exceptions on background threads
      Thread.setDefaultUncaughtExceptionHandler(
         new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
               lastException = new Exception(e);
            }
         }
      );
      ranBeforeTestSuite = true;
   }

   static void fastAppRestart() {
      flushBufferedSharedPrefs();
      StaticResetHelper.restSetStaticFields();
   }
}
