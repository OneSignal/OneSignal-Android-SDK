package com.test.onesignal;

import com.onesignal.OneSignalPackagePrivateHelper;
import com.onesignal.ShadowCustomTabsClient;
import com.onesignal.ShadowFirebaseAnalytics;
import com.onesignal.ShadowFusedLocationApiWrapper;
import com.onesignal.ShadowGcmBroadcastReceiver;
import com.onesignal.ShadowLocationGMS;
import com.onesignal.ShadowNotificationManagerCompat;
import com.onesignal.ShadowOSUtils;
import com.onesignal.ShadowOneSignalRestClient;
import com.onesignal.ShadowPushRegistratorGPS;
import com.onesignal.StaticResetHelper;

import java.util.Set;

class TestHelpers {

   static void betweenTestsCleanup() {
      StaticResetHelper.restSetStaticFields();

      ShadowOneSignalRestClient.resetStatics();

      ShadowPushRegistratorGPS.resetStatics();
   
      ShadowNotificationManagerCompat.enabled = true;

      ShadowOSUtils.subscribableStatus = 1;
   
      ShadowCustomTabsClient.resetStatics();
      ShadowGcmBroadcastReceiver.resetStatics();

      ShadowFusedLocationApiWrapper.resetStatics();
      ShadowLocationGMS.resetStatics();

      ShadowFirebaseAnalytics.resetStatics();

      // DB seems to be cleaned up on it's own.
      /*
      SQLiteDatabase writableDb = OneSignalDbHelper.getInstance(RuntimeEnvironment.application).getWritableDatabase();
      writableDb.delete(OneSignalPackagePrivateHelper.NotificationTable.TABLE_NAME, null, null);
      writableDb.close();
      */
   }
   
   static void threadAndTaskWait() throws Exception {
      boolean createdNewThread;
      do {
         createdNewThread = false;
         boolean joinedAThread;
         do {
            joinedAThread = false;
            Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
            
            for (Thread thread : threadSet) {
               if (thread.getName().startsWith("OS_")) {
                  thread.join();
                  joinedAThread = createdNewThread = true;
               }
            }
         } while (joinedAThread);
         
         boolean advancedRunnables = OneSignalPackagePrivateHelper.runAllNetworkRunnables();
         advancedRunnables = OneSignalPackagePrivateHelper.runFocusRunnables() || advancedRunnables;
         
         if (advancedRunnables)
            createdNewThread = true;
      } while (createdNewThread);
   }
}
