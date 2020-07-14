package com.test.onesignal;

import android.app.NotificationManager;
import android.content.Context;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.onesignal.OneSignal;
import com.onesignal.OneSignalPackagePrivateHelper.NotificationLimitManager;
import com.onesignal.ShadowAdvertisingIdProviderGPS;
import com.onesignal.ShadowCustomTabsClient;
import com.onesignal.ShadowCustomTabsSession;
import com.onesignal.ShadowNotificationLimitManager;
import com.onesignal.ShadowOSUtils;
import com.onesignal.ShadowOneSignalRestClient;
import com.onesignal.ShadowPushRegistratorFCM;
import com.onesignal.StaticResetHelper;
import com.onesignal.example.BlankActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import static com.onesignal.OneSignalPackagePrivateHelper.NotificationBundleProcessor_ProcessFromFCMIntentService;
import static com.onesignal.ShadowOneSignalRestClient.setRemoteParamsGetHtmlResponse;
import static com.test.onesignal.GenerateNotificationRunner.getBaseNotifBundle;
import static com.test.onesignal.TestHelpers.afterTestCleanup;
import static com.test.onesignal.TestHelpers.threadAndTaskWait;
import static junit.framework.Assert.assertEquals;

@Config(packageName = "com.onesignal.example",
        instrumentedPackages = { "com.onesignal" },
        shadows = {
            ShadowNotificationLimitManager.class,
            ShadowPushRegistratorFCM.class,
            ShadowOSUtils.class,
            ShadowAdvertisingIdProviderGPS.class,
            ShadowOneSignalRestClient.class,
            ShadowCustomTabsClient.class,
            ShadowCustomTabsSession.class,
        },
        sdk = 26
)
@RunWith(RobolectricTestRunner.class)
public class NotificationLimitManagerRunner {

   private BlankActivity blankActivity;
   private NotificationManager notificationManager;

   @BeforeClass // Runs only once, before any tests
   public static void setUpClass() throws Exception {
      ShadowLog.stream = System.out;
      TestHelpers.beforeTestSuite();
      StaticResetHelper.saveStaticValues();
   }

   @Before
   public void beforeEachTest() throws Exception {
      ActivityController<BlankActivity> blankActivityController = Robolectric.buildActivity(BlankActivity.class).create();
      blankActivity = blankActivityController.get();
      notificationManager = (NotificationManager)blankActivity.getSystemService(Context.NOTIFICATION_SERVICE);
      TestHelpers.beforeTestInitAndCleanup();

      // Set remote_params GET response
      setRemoteParamsGetHtmlResponse();
      OneSignal.setAppId("b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      OneSignal.setAppContext(blankActivity);
      OneSignal.setInFocusDisplaying(OneSignal.OSInFocusDisplayOption.Notification);
      threadAndTaskWait();
   }

   @After
   public void afterEachTest() throws Exception {
      afterTestCleanup();
   }

   @Test
   public void clearStandardMakingRoomForOneWhenAtLimit() throws Throwable {
      createNotification(blankActivity, 1);
      createNotification(blankActivity, 2);

      NotificationLimitManager.clearOldestOverLimitStandard(blankActivity, 1);

      assertEquals(1, notificationManager.getActiveNotifications().length);
      assertEquals(2, notificationManager.getActiveNotifications()[0].getId());
   }

   @Test
   public void clearStandardShouldNotCancelAnyNotificationsWhenUnderLimit() throws Throwable {
      createNotification(blankActivity, 1);

      NotificationLimitManager.clearOldestOverLimitStandard(blankActivity, 1);

      assertEquals(1, notificationManager.getActiveNotifications().length);
   }

   @Test
   public void clearStandardShouldSkipGroupSummaryNotification() throws Throwable {
      NotificationCompat.Builder notifBuilder = new NotificationCompat.Builder(blankActivity, "");
      notifBuilder.setWhen(1);
      // We should not clear summary notifications, these will go away if all child notifications are canceled
      notifBuilder.setGroupSummary(true);
      NotificationManagerCompat.from(blankActivity).notify(1, notifBuilder.build());

      createNotification(blankActivity, 2);

      NotificationLimitManager.clearOldestOverLimitStandard(blankActivity, 1);

      assertEquals(1 , notificationManager.getActiveNotifications()[0].getId());
   }

   // Helper Methods
   private static void createNotification(Context context, int notifId) {
      NotificationCompat.Builder notifBuilder = new NotificationCompat.Builder(context, "");
      notifBuilder.setWhen(notifId); // Android automatically sets this normally.
      NotificationManagerCompat.from(context).notify(notifId, notifBuilder.build());
   }

   @Test
   public void clearFallbackMakingRoomForOneWhenAtLimit() {
      NotificationBundleProcessor_ProcessFromFCMIntentService(blankActivity,  getBaseNotifBundle("UUID1"), null);
      NotificationBundleProcessor_ProcessFromFCMIntentService(blankActivity,  getBaseNotifBundle("UUID2"), null);

      NotificationLimitManager.clearOldestOverLimitFallback(blankActivity, 1);

      assertEquals(1, notificationManager.getActiveNotifications().length);
   }

   @Test
   public void clearFallbackShouldNotCancelAnyNotificationsWhenUnderLimit() {
      NotificationBundleProcessor_ProcessFromFCMIntentService(blankActivity,  getBaseNotifBundle("UUID1"), null);

      NotificationLimitManager.clearOldestOverLimitFallback(blankActivity, 1);

      assertEquals(1, notificationManager.getActiveNotifications().length);
   }
}
