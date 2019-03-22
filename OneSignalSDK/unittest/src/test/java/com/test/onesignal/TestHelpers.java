package com.test.onesignal;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.onesignal.OneSignalDbHelper;
import com.onesignal.OneSignalPackagePrivateHelper;
import com.onesignal.OneSignalPackagePrivateHelper.OneSignalPrefs;
import com.onesignal.ShadowCustomTabsClient;
import com.onesignal.ShadowFirebaseAnalytics;
import com.onesignal.ShadowFusedLocationApiWrapper;
import com.onesignal.ShadowGcmBroadcastReceiver;
import com.onesignal.ShadowGoogleApiClientCompatProxy;
import com.onesignal.ShadowNotificationManagerCompat;
import com.onesignal.ShadowOSUtils;
import com.onesignal.ShadowOneSignalDbHelper;
import com.onesignal.ShadowOneSignalRestClient;
import com.onesignal.ShadowOneSignalRestClientWithMockConnection;
import com.onesignal.ShadowPushRegistratorGCM;
import com.onesignal.StaticResetHelper;

import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowSystemClock;
import org.robolectric.util.Scheduler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import static org.robolectric.Shadows.shadowOf;

class TestHelpers {

   static Exception lastException;

   static void beforeTestInitAndCleanup() {
      stopAllOSThreads();

      StaticResetHelper.restSetStaticFields();

      ShadowOneSignalRestClient.resetStatics();

      ShadowPushRegistratorGCM.resetStatics();
   
      ShadowNotificationManagerCompat.enabled = true;

      ShadowOSUtils.subscribableStatus = 1;
   
      ShadowCustomTabsClient.resetStatics();
      ShadowGcmBroadcastReceiver.resetStatics();

      ShadowFusedLocationApiWrapper.resetStatics();

      ShadowFirebaseAnalytics.resetStatics();

      ShadowGoogleApiClientCompatProxy.restSetStaticFields();
      ShadowOneSignalDbHelper.restSetStaticFields();
      ShadowOneSignalRestClientWithMockConnection.resetStatics();

      lastException = null;

      OneSignalPackagePrivateHelper.OneSignalPrefs.initializePool();
   }

   static void afterTestCleanup() {
      try {
         stopAllOSThreads();
      } catch (Exception e) {
         e.printStackTrace();
      }

      OneSignalDbHelper.getInstance(RuntimeEnvironment.application).getReadableDatabase().close();
   }

   static void stopAllOSThreads() {
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

   static Thread getThreadByName(String threadName) {
      for (Thread t : Thread.getAllStackTraces().keySet()) {
         if (t.getName().equals(threadName))
            return t;
      }
      return null;
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
      stopAllOSThreads();
      flushBufferedSharedPrefs();
      StaticResetHelper.restSetStaticFields();
   }

   static ArrayList<HashMap<String, Object>> getAllNotificationRecords() {
      SQLiteDatabase readableDatabase = OneSignalDbHelper.getInstance(RuntimeEnvironment.application).getReadableDatabase();
      Cursor cursor = readableDatabase.query(
         OneSignalPackagePrivateHelper.NotificationTable.TABLE_NAME,
         null,
         null,
         null,
         null, // group by
         null, // filter by row groups
         null, // sort order, new to old
         null // limit
      );

      ArrayList<HashMap<String, Object>> mapList = new ArrayList<>();
      while (cursor.moveToNext()) {
         HashMap<String, Object> map = new HashMap<>();
         for(int i = 0; i < cursor.getColumnCount(); i++) {
            int type = cursor.getType(i);
            String key = cursor.getColumnName(i);

             if (type == Cursor.FIELD_TYPE_INTEGER)
                map.put(key, cursor.getLong(i));
             else if (type == Cursor.FIELD_TYPE_FLOAT)
                map.put(key, cursor.getFloat(i));
             else
                map.put(key, cursor.getString(i));
         }
         mapList.add(map);
      }

      cursor.close();

      return mapList;
   }

   static void advanceTimeByMs(long advanceBy) {
      ShadowSystemClock.setCurrentTimeMillis(System.currentTimeMillis() +  advanceBy);
   }
}
