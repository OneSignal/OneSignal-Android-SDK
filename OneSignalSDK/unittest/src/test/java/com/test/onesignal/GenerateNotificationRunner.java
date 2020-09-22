/**
 * Modified MIT License
 *
 * Copyright 2018 OneSignal
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

package com.test.onesignal;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.test.core.app.ApplicationProvider;

import com.onesignal.BundleCompat;
import com.onesignal.FCMBroadcastReceiver;
import com.onesignal.MockOSTimeImpl;
import com.onesignal.MockOneSignalDBHelper;
import com.onesignal.OSNotificationDisplayedResult;
import com.onesignal.OSNotificationExtender;
import com.onesignal.OSNotificationGenerationJob;
import com.onesignal.OSNotificationOpenResult;
import com.onesignal.OSNotificationPayload;
import com.onesignal.OSNotificationReceived;
import com.onesignal.OneSignal;
import com.onesignal.OneSignalNotificationManagerPackageHelper;
import com.onesignal.OneSignalPackagePrivateHelper;
import com.onesignal.OneSignalPackagePrivateHelper.NotificationTable;
import com.onesignal.OneSignalPackagePrivateHelper.OSNotificationRestoreWorkManager;
import com.onesignal.OneSignalPackagePrivateHelper.TestOneSignalPrefs;
import com.onesignal.OneSignalShadowPackageManager;
import com.onesignal.ShadowBadgeCountUpdater;
import com.onesignal.ShadowCustomTabsClient;
import com.onesignal.ShadowCustomTabsSession;
import com.onesignal.ShadowFCMBroadcastReceiver;
import com.onesignal.ShadowGenerateNotification;
import com.onesignal.ShadowNotificationManagerCompat;
import com.onesignal.ShadowOSUtils;
import com.onesignal.ShadowOSViewUtils;
import com.onesignal.ShadowOSWebView;
import com.onesignal.ShadowOneSignal;
import com.onesignal.ShadowOneSignalRestClient;
import com.onesignal.ShadowReceiveReceiptController;
import com.onesignal.ShadowRoboNotificationManager;
import com.onesignal.ShadowRoboNotificationManager.PostedNotification;
import com.onesignal.ShadowTimeoutHandler;
import com.onesignal.StaticResetHelper;
import com.onesignal.example.BlankActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static com.onesignal.OneSignalPackagePrivateHelper.FCMBroadcastReceiver_onReceived_withIntent;
import static com.onesignal.OneSignalPackagePrivateHelper.FCMBroadcastReceiver_processBundle;
import static com.onesignal.OneSignalPackagePrivateHelper.GenerateNotification.BUNDLE_KEY_ACTION_ID;
import static com.onesignal.OneSignalPackagePrivateHelper.GenerateNotification.BUNDLE_KEY_ANDROID_NOTIFICATION_ID;
import static com.onesignal.OneSignalPackagePrivateHelper.GenerateNotification.BUNDLE_KEY_ONESIGNAL_DATA;
import static com.onesignal.OneSignalPackagePrivateHelper.NotificationBundleProcessor.PUSH_MINIFIED_BUTTONS_LIST;
import static com.onesignal.OneSignalPackagePrivateHelper.NotificationBundleProcessor.PUSH_MINIFIED_BUTTON_ID;
import static com.onesignal.OneSignalPackagePrivateHelper.NotificationBundleProcessor.PUSH_MINIFIED_BUTTON_TEXT;
import static com.onesignal.OneSignalPackagePrivateHelper.NotificationBundleProcessor_ProcessFromFCMIntentService;
import static com.onesignal.OneSignalPackagePrivateHelper.NotificationBundleProcessor_ProcessFromFCMIntentService_NoWrap;
import static com.onesignal.OneSignalPackagePrivateHelper.NotificationOpenedProcessor_processFromContext;
import static com.onesignal.OneSignalPackagePrivateHelper.NotificationSummaryManager_updateSummaryNotificationAfterChildRemoved;
import static com.onesignal.OneSignalPackagePrivateHelper.OneSignal_setTime;
import static com.onesignal.OneSignalPackagePrivateHelper.OneSignal_setupNotificationExtensionServiceClass;
import static com.onesignal.OneSignalPackagePrivateHelper.createInternalPayloadBundle;
import static com.onesignal.ShadowOneSignalRestClient.setRemoteParamsGetHtmlResponse;
import static com.onesignal.ShadowRoboNotificationManager.getNotificationsInGroup;
import static com.test.onesignal.RestClientAsserts.assertReportReceivedAtIndex;
import static com.test.onesignal.RestClientAsserts.assertRestCalls;
import static com.test.onesignal.TestHelpers.fastColdRestartApp;
import static com.test.onesignal.TestHelpers.threadAndTaskWait;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;
import static org.robolectric.Shadows.shadowOf;

@Config(packageName = "com.onesignal.example",
        shadows = {
            ShadowRoboNotificationManager.class,
            ShadowOneSignalRestClient.class,
            ShadowBadgeCountUpdater.class,
            ShadowNotificationManagerCompat.class,
            ShadowOSUtils.class,
            ShadowOSViewUtils.class,
            ShadowCustomTabsClient.class,
            ShadowCustomTabsSession.class,
            OneSignalShadowPackageManager.class
        },
        sdk = 21
)
@RunWith(RobolectricTestRunner.class)
public class GenerateNotificationRunner {

   private static int callbackCounter = 0;

   private static final String ONESIGNAL_APP_ID = "b4f7f966-d8cc-11e4-bed1-df8f05be55ba";
   private static final String notifMessage = "Robo test message";

   private Activity blankActivity;
   private static ActivityController<BlankActivity> blankActivityController;

   private OSNotificationGenerationJob.AppNotificationGenerationJob lastAppNotificationJob;
   private static OSNotificationReceived lastNotificationReceived;
   private MockOneSignalDBHelper dbHelper;
   private MockOSTimeImpl time;

   @BeforeClass // Runs only once, before any tests
   public static void setUpClass() throws Exception {
      ShadowLog.stream = System.out;
      TestHelpers.beforeTestSuite();
      StaticResetHelper.saveStaticValues();
   }
   
   @Before // Before each test
   public void beforeEachTest() throws Exception {
      blankActivityController = Robolectric.buildActivity(BlankActivity.class).create();
      blankActivity = blankActivityController.get();
      blankActivity.getApplicationInfo().name = "UnitTestApp";
      dbHelper = new MockOneSignalDBHelper(ApplicationProvider.getApplicationContext());
      time = new MockOSTimeImpl();

      callbackCounter = 0;
      lastAppNotificationJob = null;
      lastNotificationReceived = null;

      TestHelpers.beforeTestInitAndCleanup();

      setClearGroupSummaryClick(true);

      NotificationManager notificationManager = OneSignalNotificationManagerPackageHelper.getNotificationManager(blankActivity);
      notificationManager.cancelAll();
      OSNotificationRestoreWorkManager.restored = false;

      OneSignal_setTime(time);

      // Set remote_params GET response
      setRemoteParamsGetHtmlResponse();
   }

   @AfterClass
   public static void afterEverything() throws Exception {
      StaticResetHelper.restSetStaticFields();
   }
   
   public static Bundle getBaseNotifBundle() {
      return getBaseNotifBundle("UUID");
   }
   
   public static Bundle getBaseNotifBundle(String id) {
      Bundle bundle = new Bundle();
      bundle.putString("alert", notifMessage);
      bundle.putString("custom", "{\"i\": \"" + id + "\"}");
      
      return bundle;
   }

   private static Intent createOpenIntent(int notifId, Bundle bundle) {
      return new Intent()
          .putExtra(BUNDLE_KEY_ANDROID_NOTIFICATION_ID, notifId)
          .putExtra(BUNDLE_KEY_ONESIGNAL_DATA, OneSignalPackagePrivateHelper.bundleAsJSONObject(bundle).toString());
   }
   
   private Intent createOpenIntent(Bundle bundle) {
      return createOpenIntent(ShadowRoboNotificationManager.lastNotifId, bundle);
   }
   
   @Test
   @Config (sdk = 22, shadows = { ShadowGenerateNotification.class })
   public void shouldSetTitleCorrectly() throws Exception {
      // Should use app's Title by default
      Bundle bundle = getBaseNotifBundle();
      NotificationBundleProcessor_ProcessFromFCMIntentService(blankActivity, bundle, null);
      threadAndTaskWait();

      assertEquals("UnitTestApp", ShadowRoboNotificationManager.getLastShadowNotif().getContentTitle());
      
      // Should allow title from FCM payload.
      bundle = getBaseNotifBundle("UUID2");
      bundle.putString("title", "title123");
      NotificationBundleProcessor_ProcessFromFCMIntentService(blankActivity, bundle, null);
      threadAndTaskWait();

      assertEquals("title123", ShadowRoboNotificationManager.getLastShadowNotif().getContentTitle());
   }
   
   @Test
   @Config (sdk = 22, shadows = { ShadowGenerateNotification.class })
   public void shouldProcessRestore() throws Exception {
      BundleCompat bundle = createInternalPayloadBundle(getBaseNotifBundle());
      bundle.putInt("android_notif_id", 0);
      bundle.putBoolean("is_restoring", true);
      
      NotificationBundleProcessor_ProcessFromFCMIntentService_NoWrap(blankActivity, bundle, null);
      threadAndTaskWait();

      assertEquals("UnitTestApp", ShadowRoboNotificationManager.getLastShadowNotif().getContentTitle());
   }

   @Test
   @Config(sdk = Build.VERSION_CODES.O)
   public void shouldNotRestoreActiveNotifs() throws Exception {
      // Display a notification
      Bundle bundle = getBaseNotifBundle("UUID1");
      bundle.putString("grp", "test1");
      NotificationBundleProcessor_ProcessFromFCMIntentService(blankActivity, bundle, null);
      threadAndTaskWait();

      // Restore notifs
      OSNotificationRestoreWorkManager.beginEnqueueingWork(blankActivity, false);
      threadAndTaskWait();

      // Assert that no restoration jobs were scheduled
      JobScheduler scheduler = (JobScheduler) blankActivity.getSystemService(Context.JOB_SCHEDULER_SERVICE);
      assertTrue(scheduler.getAllPendingJobs().isEmpty());
   }

   private static OSNotificationOpenResult lastOpenResult;
   
   @Test
   @Config(shadows = { ShadowGenerateNotification.class })
   public void shouldContainPayloadWhenOldSummaryNotificationIsOpened() throws Exception {
      OneSignal.setAppId("b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      OneSignal.initWithContext(blankActivity);
      OneSignal.setNotificationOpenedHandler(new OneSignal.NotificationOpenedHandler() {
         @Override
         public void notificationOpened(OSNotificationOpenResult result) {
            lastOpenResult = result;
         }
      });
      
      // Display 2 notifications that will be grouped together.
      Bundle bundle = getBaseNotifBundle("UUID1");
      bundle.putString("grp", "test1");
      NotificationBundleProcessor_ProcessFromFCMIntentService(blankActivity, bundle, null);
   
      bundle = getBaseNotifBundle("UUID2");
      bundle.putString("grp", "test1");
      NotificationBundleProcessor_ProcessFromFCMIntentService(blankActivity, bundle, null);
   
      // Go forward 4 weeks
      time.advanceSystemTimeBy(2_419_202);
      
      // Display a 3 normal notification.
      NotificationBundleProcessor_ProcessFromFCMIntentService(blankActivity, getBaseNotifBundle("UUID3"), null);
      threadAndTaskWait();

      Map<Integer, PostedNotification> postedNotifs = ShadowRoboNotificationManager.notifications;
      
      // Open the summary notification
      Iterator<Map.Entry<Integer, PostedNotification>> postedNotifsIterator = postedNotifs.entrySet().iterator();
      PostedNotification postedNotification = postedNotifsIterator.next().getValue();
      Intent intent = Shadows.shadowOf(postedNotification.notif.contentIntent).getSavedIntent();
      NotificationOpenedProcessor_processFromContext(blankActivity, intent);
      threadAndTaskWait();

      // Make sure we get a payload when it is opened.
      assertNotNull(lastOpenResult.getNotification().getPayload());
   }

   @Test
   @Config(shadows = { ShadowGenerateNotification.class })
   public void shouldSetCorrectNumberOfButtonsOnSummaryNotification() throws Exception {
      // Setup - Init
      OneSignal.setAppId("b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      OneSignal.initWithContext(blankActivity);
      threadAndTaskWait();
   
      // Setup - Display a single notification with a grouped.
      Bundle bundle = getBaseNotifBundle("UUID1");
      bundle.putString("grp", "test1");
      bundle.putString("custom", "{\"i\": \"some_UUID\", \"a\": {\"actionButtons\": [{\"text\": \"test\"} ]}}");
      NotificationBundleProcessor_ProcessFromFCMIntentService(blankActivity, bundle, null);
      threadAndTaskWait();
   
      Map<Integer, PostedNotification> postedNotifs = ShadowRoboNotificationManager.notifications;
      Iterator<Map.Entry<Integer, PostedNotification>> postedNotifsIterator = postedNotifs.entrySet().iterator();
      PostedNotification postedSummaryNotification = postedNotifsIterator.next().getValue();
   
      assertEquals(Notification.FLAG_GROUP_SUMMARY, postedSummaryNotification.notif.flags & Notification.FLAG_GROUP_SUMMARY);
      assertEquals(1, postedSummaryNotification.notif.actions.length);
   }
   
   @Test
   @Config(shadows = { ShadowGenerateNotification.class })
   public void shouldCancelAllNotificationsPartOfAGroup() throws Exception {
      // Setup - Init
      OneSignal.setAppId("b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      OneSignal.initWithContext(blankActivity);
      threadAndTaskWait();
      
      // Setup - Display 3 notifications, 2 of which that will be grouped together.
      Bundle bundle = getBaseNotifBundle("UUID0");
      NotificationBundleProcessor_ProcessFromFCMIntentService(blankActivity, bundle, null);
      threadAndTaskWait();

      bundle = getBaseNotifBundle("UUID1");
      bundle.putString("grp", "test1");
      NotificationBundleProcessor_ProcessFromFCMIntentService(blankActivity, bundle, null);
      threadAndTaskWait();

      bundle = getBaseNotifBundle("UUID2");
      bundle.putString("grp", "test1");
      NotificationBundleProcessor_ProcessFromFCMIntentService(blankActivity, bundle, null);
      threadAndTaskWait();

      assertEquals(4, ShadowRoboNotificationManager.notifications.size());
      
      OneSignal.cancelGroupedNotifications("test1");
      threadAndTaskWait();

      assertEquals(1, ShadowRoboNotificationManager.notifications.size());
   }

   @Test
   @Config(sdk = Build.VERSION_CODES.N, shadows = { ShadowGenerateNotification.class })
   public void testFourNotificationsUseProvidedGroup() throws Exception {
      OneSignal.setAppId("b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      OneSignal.initWithContext(blankActivity.getApplicationContext());
      threadAndTaskWait();

      // Add 4 grouped notifications
      postNotificationWithOptionalGroup(4, "test1");
      threadAndTaskWait();

      assertEquals(4, getNotificationsInGroup("test1").size());
   }

   @Test
   @Config(sdk = Build.VERSION_CODES.N, shadows = { ShadowGenerateNotification.class })
   public void testFourGrouplessNotificationsUseDefaultGroup() throws Exception {
      OneSignal.setAppId("b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      OneSignal.initWithContext(blankActivity.getApplicationContext());
      threadAndTaskWait();

      // Add 4 groupless notifications
      postNotificationWithOptionalGroup(4, null);
      threadAndTaskWait();

      assertEquals(4, getNotificationsInGroup("os_group_undefined").size());
   }

    @Test
    @Config(sdk = Build.VERSION_CODES.LOLLIPOP, shadows = { ShadowGenerateNotification.class })
    public void testNotifDismissAllOnGroupSummaryClickForAndroidUnderM() throws Exception {
        OneSignal.setAppId("b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
        OneSignal.initWithContext(blankActivity);
        threadAndTaskWait();

        SQLiteDatabase readableDb = dbHelper.getSQLiteDatabaseWithRetries();

        postNotificationsAndSimulateSummaryClick(true, "test1");

        // Validate SQL DB has removed all grouped notifs
        int activeGroupNotifCount = queryNotificationCountFromGroup(readableDb, "test1", true);
        assertEquals(0, activeGroupNotifCount);
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.LOLLIPOP, shadows = { ShadowGenerateNotification.class })
    public void testNotifDismissRecentOnGroupSummaryClickForAndroidUnderM() throws Exception {
        OneSignal.setAppId("b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
        OneSignal.initWithContext(blankActivity);
        threadAndTaskWait();

        SQLiteDatabase readableDb = dbHelper.getSQLiteDatabaseWithRetries();

        postNotificationsAndSimulateSummaryClick(false, "test1");

        // Validate SQL DB has removed most recent grouped notif
        int activeGroupNotifCount = queryNotificationCountFromGroup(readableDb, "test1", true);
        assertEquals(3, activeGroupNotifCount);
    }

   @Test
   @Config(sdk = Build.VERSION_CODES.N, shadows = { ShadowGenerateNotification.class })
   public void testNotifDismissAllOnGroupSummaryClick() throws Exception {
      OneSignal.setAppId("b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      OneSignal.initWithContext(blankActivity);
      threadAndTaskWait();

      SQLiteDatabase readableDb = dbHelper.getSQLiteDatabaseWithRetries();

      postNotificationsAndSimulateSummaryClick(true, "test1");

      // Validate SQL DB has removed all grouped notifs
      int activeGroupNotifCount = queryNotificationCountFromGroup(readableDb, "test1", true);
      assertEquals(0, activeGroupNotifCount);
   }

   @Test
   @Config(sdk = Build.VERSION_CODES.N, shadows = { ShadowGenerateNotification.class })
   public void testNotifDismissRecentOnGroupSummaryClick() throws Exception {
      OneSignal.setAppId("b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      OneSignal.initWithContext(blankActivity);
      threadAndTaskWait();

      postNotificationsAndSimulateSummaryClick(false, "test1");
      threadAndTaskWait();

      // Validate SQL DB has removed most recent grouped notif
      SQLiteDatabase readableDb = dbHelper.getSQLiteDatabaseWithRetries();
      int activeGroupNotifCount = queryNotificationCountFromGroup(readableDb, "test1", true);
      threadAndTaskWait();

      assertEquals(3, activeGroupNotifCount);
   }

   @Test
   @Config(sdk = Build.VERSION_CODES.N, shadows = { ShadowGenerateNotification.class })
   public void testNotifDismissAllOnGrouplessSummaryClick() throws Exception {
      OneSignal.setAppId("b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      OneSignal.initWithContext(blankActivity);
      threadAndTaskWait();

      SQLiteDatabase readableDb = dbHelper.getSQLiteDatabaseWithRetries();

      postNotificationsAndSimulateSummaryClick(true, null);

      // Validate SQL DB has removed all groupless notifs
      int activeGroupNotifCount = queryNotificationCountFromGroup(readableDb, null, true);
      assertEquals(0, activeGroupNotifCount);
   }

   @Test
   @Config(sdk = Build.VERSION_CODES.N, shadows = { ShadowGenerateNotification.class })
   public void testNotifDismissRecentOnGrouplessSummaryClick() throws Exception {
      OneSignal.setAppId("b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      OneSignal.initWithContext(blankActivity);
      threadAndTaskWait();

      SQLiteDatabase readableDb = dbHelper.getSQLiteDatabaseWithRetries();

      postNotificationsAndSimulateSummaryClick(false, null);
      threadAndTaskWait();

      // Validate SQL DB has removed most recent groupless notif
      int activeGroupNotifCount = queryNotificationCountFromGroup(readableDb, null, true);
      assertEquals(3, activeGroupNotifCount);
   }

   private void postNotificationsAndSimulateSummaryClick(boolean shouldDismissAll, String group) throws Exception {
      // Add 4 notifications
      Bundle bundle = postNotificationWithOptionalGroup(4, group);
      setClearGroupSummaryClick(shouldDismissAll);
      threadAndTaskWait();

      // Obtain the summary id
      Map<Integer, PostedNotification> postedNotifs = ShadowRoboNotificationManager.notifications;
      Iterator<Map.Entry<Integer, PostedNotification>> postedNotifsIterator = postedNotifs.entrySet().iterator();
      PostedNotification postedSummaryNotification = postedNotifsIterator.next().getValue();

      // Simulate summary click
      String groupKey = group != null ?
              group :
              OneSignalNotificationManagerPackageHelper.getGrouplessSummaryKey();

      Intent intent = createOpenIntent(postedSummaryNotification.id, bundle).putExtra("summary", groupKey);
      NotificationOpenedProcessor_processFromContext(blankActivity, intent);
      threadAndTaskWait();
   }

   private void setClearGroupSummaryClick(boolean shouldDismissAll) {
      TestOneSignalPrefs.saveBool(TestOneSignalPrefs.PREFS_ONESIGNAL, TestOneSignalPrefs.PREFS_OS_CLEAR_GROUP_SUMMARY_CLICK, shouldDismissAll);
   }

   @Test
   @Config(sdk = Build.VERSION_CODES.N, shadows = { ShadowGenerateNotification.class, ShadowGenerateNotification.class })
   public void testGrouplessSummaryKeyReassignmentAtFourOrMoreNotification() throws Exception {
      OneSignal.setAppId("b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      OneSignal.initWithContext(blankActivity);
      threadAndTaskWait();

      // Add 3 groupless notifications
      postNotificationWithOptionalGroup(3, null);
      threadAndTaskWait();

      // Assert before 4, no notif summary is created
      int count = OneSignalNotificationManagerPackageHelper.getActiveNotifications(blankActivity).length;
      assertEquals(3, count);

      // Add 4 groupless notifications
      postNotificationWithOptionalGroup(4, null);
      threadAndTaskWait();

      // Assert after 4, a notif summary is created
      count = OneSignalNotificationManagerPackageHelper.getActiveNotifications(blankActivity).length;
      assertEquals(5, count);

      SQLiteDatabase readableDb = dbHelper.getSQLiteDatabaseWithRetries();
      // Validate no DB changes occurred and this is only a runtime change to the groupless notifs
      // Check for 4 null group id notifs
      int nullGroupCount = queryNotificationCountFromGroup(readableDb, null, true);
      assertEquals(4, nullGroupCount);

      // Check for 0 os_group_undefined group id notifs
      int groupCount = queryNotificationCountFromGroup(readableDb, OneSignalNotificationManagerPackageHelper.getGrouplessSummaryKey(), true);
      assertEquals(0, groupCount);
   }

    private @Nullable Bundle postNotificationWithOptionalGroup(int notifCount, @Nullable String group) {
       Bundle bundle = null;
       for (int i = 0; i < notifCount; i++) {
          bundle = getBaseNotifBundle("UUID" + i);
          bundle.putString("grp", group);

          NotificationBundleProcessor_ProcessFromFCMIntentService(blankActivity, bundle, null);
       }
       return bundle;
    }

   public int queryNotificationCountFromGroup(SQLiteDatabase db, String group, boolean activeNotifs) {
      boolean isGroupless = group == null;

      String whereStr = isGroupless ?
              NotificationTable.COLUMN_NAME_GROUP_ID + " IS NULL" :
              NotificationTable.COLUMN_NAME_GROUP_ID + " = ?";
      whereStr += " AND " + NotificationTable.COLUMN_NAME_DISMISSED + " = ? AND " +
              NotificationTable.COLUMN_NAME_OPENED + " = ? AND " +
              NotificationTable.COLUMN_NAME_IS_SUMMARY + " = 0";

      String active = activeNotifs ? "0" : "1";
      String[] whereArgs = isGroupless ?
              new String[]{ active, active } :
              new String[]{ group, active, active };

      Cursor cursor = db.query(NotificationTable.TABLE_NAME,
              null,
              whereStr,
              whereArgs,
              null,
              null,
              null);
      cursor.moveToFirst();

      return cursor.getCount();
   }

   @Test
   // We need ShadowTimeoutHandler because RestoreJobService run under an AsyncTask, in that way we can avoid deadlock due to Roboelectric tying to shadow
   // Handlers under AsyncTask, and Roboelectric doesn't support handler outside it's custom Main Thread
   @Config(shadows = { ShadowGenerateNotification.class, ShadowTimeoutHandler.class })
   public void shouldCancelNotificationAndUpdateSummary() throws Exception {
      // Setup - Init
      OneSignal.setAppId("b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      OneSignal.initWithContext(blankActivity);
      threadAndTaskWait();
      runImplicitServices(); // Flushes out other services, seems to be a roboelectric bug
      
      // Setup - Display 3 notifications that will be grouped together.
      Bundle bundle = getBaseNotifBundle("UUID1");
      bundle.putString("grp", "test1");
      NotificationBundleProcessor_ProcessFromFCMIntentService(blankActivity, bundle, null);
      threadAndTaskWait();

      bundle = getBaseNotifBundle("UUID2");
      bundle.putString("grp", "test1");
      NotificationBundleProcessor_ProcessFromFCMIntentService(blankActivity, bundle, null);
      threadAndTaskWait();

      bundle = getBaseNotifBundle("UUID3");
      bundle.putString("grp", "test1");
      NotificationBundleProcessor_ProcessFromFCMIntentService(blankActivity, bundle, null);
      threadAndTaskWait();
      
      Map<Integer, PostedNotification> postedNotifs = ShadowRoboNotificationManager.notifications;
      Iterator<Map.Entry<Integer, PostedNotification>> postedNotifsIterator = postedNotifs.entrySet().iterator();
      
      // Test - 3 notifis + 1 summary
      assertEquals(4, postedNotifs.size());

      // Test - First notification should be the summary
      PostedNotification postedSummaryNotification = postedNotifsIterator.next().getValue();
      assertEquals("3 new messages", postedSummaryNotification.getShadow().getContentText());
      assertEquals(Notification.FLAG_GROUP_SUMMARY, postedSummaryNotification.notif.flags & Notification.FLAG_GROUP_SUMMARY);
   
      // Setup - Let's cancel a child notification.
      PostedNotification postedNotification = postedNotifsIterator.next().getValue();
      OneSignal.cancelNotification(postedNotification.id);
      threadAndTaskWait();

      // Test - It should update summary text to say 2 notifications
      postedNotifs = ShadowRoboNotificationManager.notifications;
      assertEquals(3, postedNotifs.size());       // 2 notifis + 1 summary
      postedNotifsIterator = postedNotifs.entrySet().iterator();
      postedSummaryNotification = postedNotifsIterator.next().getValue();
      assertEquals("2 new messages", postedSummaryNotification.getShadow().getContentText());
      assertEquals(Notification.FLAG_GROUP_SUMMARY, postedSummaryNotification.notif.flags & Notification.FLAG_GROUP_SUMMARY);
   
      // Setup - Let's cancel a 2nd child notification.
      postedNotification = postedNotifsIterator.next().getValue();
      OneSignal.cancelNotification(postedNotification.id);
      threadAndTaskWait();

      runImplicitServices();
      Thread.sleep(1_000); // TODO: Service runs AsyncTask. Need to wait for this
   
      // Test - It should update summary notification to be the text of the last remaining one.
      postedNotifs = ShadowRoboNotificationManager.notifications;
      assertEquals(2, postedNotifs.size()); // 1 notifis + 1 summary
      postedNotifsIterator = postedNotifs.entrySet().iterator();
      postedSummaryNotification = postedNotifsIterator.next().getValue();
      assertEquals(notifMessage, postedSummaryNotification.getShadow().getContentText());
      assertEquals(Notification.FLAG_GROUP_SUMMARY, postedSummaryNotification.notif.flags & Notification.FLAG_GROUP_SUMMARY);
      
      // Test - Let's make sure we will have our last notification too
      postedNotification = postedNotifsIterator.next().getValue();
      assertEquals(notifMessage, postedNotification.getShadow().getContentText());
      
      // Setup - Let's cancel our 3rd and last child notification.
      OneSignal.cancelNotification(postedNotification.id);
      threadAndTaskWait();

      // Test - No more notifications! :)
      postedNotifs = ShadowRoboNotificationManager.notifications;
      assertEquals(0, postedNotifs.size());
   }

   // NOTE: SIDE EFFECT: Consumes non-Implicit without running them.
   private void runImplicitServices() throws Exception {
      do {
         Intent intent = Shadows.shadowOf(blankActivity).getNextStartedService();
         if (intent == null)
            break;

         ComponentName componentName = intent.getComponent();
         if (componentName == null)
            break;

         Class serviceClass = Class.forName(componentName.getClassName());

         Robolectric.buildService(serviceClass, intent).create().startCommand(0, 0);
      } while (true);
   }
   
   @Test
   @Config(shadows = { ShadowGenerateNotification.class })
   public void shouldUpdateBadgesWhenDismissingNotification() {
      Bundle bundle = getBaseNotifBundle();
      NotificationBundleProcessor_ProcessFromFCMIntentService(blankActivity, bundle, null);
      assertEquals(notifMessage, ShadowRoboNotificationManager.getLastShadowNotif().getContentText());
      assertEquals(1, ShadowBadgeCountUpdater.lastCount);
   
      Map<Integer, PostedNotification> postedNotifs = ShadowRoboNotificationManager.notifications;
      Iterator<Map.Entry<Integer, PostedNotification>> postedNotifsIterator = postedNotifs.entrySet().iterator();
      PostedNotification postedNotification = postedNotifsIterator.next().getValue();
      Intent intent = Shadows.shadowOf(postedNotification.notif.deleteIntent).getSavedIntent();
      NotificationOpenedProcessor_processFromContext(blankActivity, intent);
   
      assertEquals(0, ShadowBadgeCountUpdater.lastCount);
   }
   
   @Test
   public void shouldNotSetBadgesWhenNotificationPermissionIsDisabled() throws Exception {
      ShadowNotificationManagerCompat.enabled = false;
      OneSignal.setAppId("b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      OneSignal.initWithContext(blankActivity);
      threadAndTaskWait();
      
      Bundle bundle = getBaseNotifBundle();
      NotificationBundleProcessor_ProcessFromFCMIntentService(blankActivity, bundle, null);
      assertEquals(0, ShadowBadgeCountUpdater.lastCount);
   }

   @Test
   @Config(shadows = { ShadowGenerateNotification.class })
   public void shouldSetBadgesWhenRestoringNotifications() throws Exception {
      NotificationBundleProcessor_ProcessFromFCMIntentService(blankActivity, getBaseNotifBundle(), null);
      ShadowBadgeCountUpdater.lastCount = 0;

      OSNotificationRestoreWorkManager.beginEnqueueingWork(blankActivity, false);
      threadAndTaskWait();

      assertEquals(1, ShadowBadgeCountUpdater.lastCount);
   }

   @Test public void shouldNotRestoreNotificationsIfPermissionIsDisabled() throws Exception {
      NotificationBundleProcessor_ProcessFromFCMIntentService(blankActivity, getBaseNotifBundle(), null);

      ShadowNotificationManagerCompat.enabled = false;
      OSNotificationRestoreWorkManager.beginEnqueueingWork(blankActivity, false);
      threadAndTaskWait();

      assertNull(Shadows.shadowOf(blankActivity).getNextStartedService());
   }
   
   @Test
   public void shouldNotShowNotificationWhenAlertIsBlankOrNull() {
      Bundle bundle = getBaseNotifBundle();
      bundle.remove("alert");
      NotificationBundleProcessor_ProcessFromFCMIntentService(blankActivity, bundle, null);
   
      assertNoNotifications();
   
      bundle = getBaseNotifBundle("UUID2");
      bundle.putString("alert", "");
      NotificationBundleProcessor_ProcessFromFCMIntentService(blankActivity, bundle, null);
   
      assertNoNotifications();

      assertNotificationDbRecords(2);
   }
   
   @Test
   @Config(shadows = { ShadowGenerateNotification.class })
   public void shouldUpdateNormalNotificationDisplayWhenReplacingANotification() throws Exception {
      // Setup - init
      OneSignal.setAppId("b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      OneSignal.initWithContext(blankActivity);
      threadAndTaskWait();
   
      // Setup - Display 2 notifications with the same group and collapse_id
      Bundle bundle = getBaseNotifBundle("UUID1");
      bundle.putString("grp", "test1");
      bundle.putString("collapse_key", "1");
      NotificationBundleProcessor_ProcessFromFCMIntentService(blankActivity, bundle, null);
   
      bundle = getBaseNotifBundle("UUID2");
      bundle.putString("grp", "test1");
      bundle.putString("collapse_key", "1");
      NotificationBundleProcessor_ProcessFromFCMIntentService(blankActivity, bundle, null);

      // Test - Summary created and sub notification. Summary will look the same as the normal notification.
      Map<Integer, PostedNotification> postedNotifs = ShadowRoboNotificationManager.notifications;
      Iterator<Map.Entry<Integer, PostedNotification>> postedNotifsIterator = postedNotifs.entrySet().iterator();
      assertEquals(2, postedNotifs.size());
      PostedNotification postedSummaryNotification = postedNotifsIterator.next().getValue();
      assertEquals(notifMessage, postedSummaryNotification.getShadow().getContentText());
      assertEquals(Notification.FLAG_GROUP_SUMMARY, postedSummaryNotification.notif.flags & Notification.FLAG_GROUP_SUMMARY);
   
      int lastNotifId = postedNotifsIterator.next().getValue().id;
      ShadowRoboNotificationManager.notifications.clear();
      
      // Setup - Restore
      BundleCompat bundle2 = createInternalPayloadBundle(bundle);
      bundle2.putInt("android_notif_id", lastNotifId);
      bundle2.putBoolean("is_restoring", true);
      NotificationBundleProcessor_ProcessFromFCMIntentService_NoWrap(blankActivity, bundle2, null);
      threadAndTaskWait();

      // Test - Restored notifications display exactly the same as they did when received.
      postedNotifs = ShadowRoboNotificationManager.notifications;
      postedNotifsIterator = postedNotifs.entrySet().iterator();
      // Test - 1 notifi + 1 summary
      assertEquals(2, postedNotifs.size());
      assertEquals(notifMessage, postedSummaryNotification.getShadow().getContentText());
      assertEquals(Notification.FLAG_GROUP_SUMMARY, postedSummaryNotification.notif.flags & Notification.FLAG_GROUP_SUMMARY);
   }

   @Test
   @Config(shadows = { ShadowGenerateNotification.class })
   public void shouldHandleBasicNotifications() throws Exception {
      // Make sure the notification got posted and the content is correct.
      Bundle bundle = getBaseNotifBundle();
      NotificationBundleProcessor_ProcessFromFCMIntentService(blankActivity, bundle, null);
      assertEquals(notifMessage, ShadowRoboNotificationManager.getLastShadowNotif().getContentText());
      assertEquals(1, ShadowBadgeCountUpdater.lastCount);

      // Should have 1 DB record with the correct time stamp
      SQLiteDatabase readableDb = dbHelper.getSQLiteDatabaseWithRetries();
      Cursor cursor = readableDb.query(NotificationTable.TABLE_NAME, new String[] { "created_time" }, null, null, null, null, null);
      assertEquals(1, cursor.getCount());
      // Time stamp should be set and within a small range.
      long currentTime = System.currentTimeMillis() / 1000;
      cursor.moveToFirst();
      assertTrue(cursor.getLong(0) > currentTime - 2 && cursor.getLong(0) <= currentTime);
      cursor.close();

      // Should get marked as opened.
      NotificationOpenedProcessor_processFromContext(blankActivity, createOpenIntent(bundle));
      cursor = readableDb.query(NotificationTable.TABLE_NAME, new String[] { "opened", "android_notification_id" }, null, null, null, null, null);
      cursor.moveToFirst();
      assertEquals(1, cursor.getInt(0));
      assertEquals(0, ShadowBadgeCountUpdater.lastCount);
      cursor.close();

      // Should not display a duplicate notification, count should still be 1
      NotificationBundleProcessor_ProcessFromFCMIntentService(blankActivity, bundle, null);
      readableDb = dbHelper.getSQLiteDatabaseWithRetries();
      cursor = readableDb.query(NotificationTable.TABLE_NAME, null, null, null, null, null, null);
      assertEquals(1, cursor.getCount());
      assertEquals(0, ShadowBadgeCountUpdater.lastCount);
      cursor.close();

      // Display a second notification
      bundle = getBaseNotifBundle("UUID2");
      NotificationBundleProcessor_ProcessFromFCMIntentService(blankActivity, bundle, null);

      // Go forward 4 weeks
      // Note: Does not effect the SQL function strftime
      time.advanceSystemTimeBy(2_419_202);

      // Restart the app so OneSignalCacheCleaner can clean out old notifications
      fastColdRestartApp();
      OneSignal_setTime(time);
      threadAndTaskWait();

      // Display a 3rd notification
      // Should of been added for a total of 2 records now.
      // First opened should of been cleaned up, 1 week old non opened notification should stay, and one new record.
      bundle = getBaseNotifBundle("UUID3");
      NotificationBundleProcessor_ProcessFromFCMIntentService(blankActivity, bundle, null);
      readableDb = dbHelper.getSQLiteDatabaseWithRetries();
      cursor = readableDb.query(NotificationTable.TABLE_NAME, new String[] { }, null, null, null, null, null);

      assertEquals(1, cursor.getCount());

      cursor.close();
   }

   @Test
   @Config(shadows = { ShadowGenerateNotification.class })
   public void shouldRestoreNotifications() throws Exception {
      restoreNotifications();
      threadAndTaskWait();

      assertEquals(0, ShadowBadgeCountUpdater.lastCount);

      NotificationBundleProcessor_ProcessFromFCMIntentService(blankActivity, getBaseNotifBundle(), null);

      restoreNotifications();
      threadAndTaskWait();

      assertEquals(1, ShadowBadgeCountUpdater.lastCount);

//      Intent intent = Shadows.shadowOf(blankActivity).getNextStartedService();
//      assertEquals(RestoreJobService.class.getName(), intent.getComponent().getClassName());

      // Go forward 1 week
      // Note: Does not effect the SQL function strftime
      time.advanceSystemTimeBy(604_801);

      // Restorer should not fire service since the notification is over 1 week old.
      restoreNotifications();
      threadAndTaskWait();
//      assertNull(Shadows.shadowOf(blankActivity).getNextStartedService());

      assertEquals(0, ShadowBadgeCountUpdater.lastCount);
   }

   private void restoreNotifications() {
      OSNotificationRestoreWorkManager.restored = false;
      OSNotificationRestoreWorkManager.beginEnqueueingWork(blankActivity, false);
   }

   private void helperShouldRestoreNotificationsPastExpireTime(boolean should) throws Exception {
      long ttl = 60L;
      Bundle bundle = getBaseNotifBundle();
      bundle.putLong("google.sent_time", System.currentTimeMillis());
      bundle.putLong("google.ttl", ttl);
      NotificationBundleProcessor_ProcessFromFCMIntentService(blankActivity, bundle, null);

      restoreNotifications();
      threadAndTaskWait();

      assertEquals(1, ShadowBadgeCountUpdater.lastCount);

      // Go forward just past the TTL of the notification
      time.advanceSystemTimeBy(ttl + 1);
      restoreNotifications();
      if (should)
         assertEquals(1, ShadowBadgeCountUpdater.lastCount);
      else
         assertEquals(0, ShadowBadgeCountUpdater.lastCount);
   }

   @Test
   @Config(shadows = { ShadowGenerateNotification.class })
   public void doNotRestoreNotificationsPastExpireTime() throws Exception {
      helperShouldRestoreNotificationsPastExpireTime(false);
   }

   @Test
   @Config(shadows = { ShadowGenerateNotification.class })
   public void restoreNotificationsPastExpireTimeIfSettingIsDisabled() throws Exception {
      TestOneSignalPrefs.saveBool(TestOneSignalPrefs.PREFS_ONESIGNAL, TestOneSignalPrefs.PREFS_OS_RESTORE_TTL_FILTER, false);
      helperShouldRestoreNotificationsPastExpireTime(true);
   }

   @Test
   public void badgeCountShouldNotIncludeOldNotifications() {
      NotificationBundleProcessor_ProcessFromFCMIntentService(blankActivity, getBaseNotifBundle(), null);

      // Go forward 1 week
      time.advanceSystemTimeBy(604_801);

      // Should not count as a badge
      SQLiteDatabase readableDb = dbHelper.getSQLiteDatabaseWithRetries();
      OneSignalPackagePrivateHelper.BadgeCountUpdater.update(readableDb, blankActivity);
      assertEquals(0, ShadowBadgeCountUpdater.lastCount);
   }

   @Test
   @Config(shadows = { ShadowGenerateNotification.class })
   public void shouldGenerate2BasicGroupNotifications() throws Exception {
      ShadowOSUtils.hasAllRecommendedFCMLibraries(true);
      // First init run for appId to be saved
      // At least OneSignal was init once for user to be subscribed
      // If this doesn't' happen, notifications will not arrive
      OneSignal.setAppId(ONESIGNAL_APP_ID);
      OneSignal.initWithContext(blankActivity);
      threadAndTaskWait();
      fastColdRestartApp();
      // We only care about request post first init
      ShadowOneSignalRestClient.resetStatics();

      Log.i(GenerateNotificationRunner.class.getCanonicalName(), "****** AFTER RESET STATICS ******");
      setRemoteParamsGetHtmlResponse();
      ShadowOSUtils.hasAllRecommendedFCMLibraries(true);

      // Make sure the notification got posted and the content is correct.
      Bundle bundle = getBaseNotifBundle();
      bundle.putString("grp", "test1");
      NotificationBundleProcessor_ProcessFromFCMIntentService(blankActivity, bundle, null);
      threadAndTaskWait();

      Map<Integer, PostedNotification> postedNotifs = ShadowRoboNotificationManager.notifications;
      assertEquals(2, postedNotifs.size());

      // Test summary notification
      Iterator<Map.Entry<Integer, PostedNotification>> postedNotifsIterator = postedNotifs.entrySet().iterator();
      PostedNotification postedNotification = postedNotifsIterator.next().getValue();

      assertEquals(notifMessage, postedNotification.getShadow().getContentText());
      assertEquals(Notification.FLAG_GROUP_SUMMARY, postedNotification.notif.flags & Notification.FLAG_GROUP_SUMMARY);

      // Test Android Wear notification
      postedNotification = postedNotifsIterator.next().getValue();
      assertEquals(notifMessage, postedNotification.getShadow().getContentText());
      assertEquals(0, postedNotification.notif.flags & Notification.FLAG_GROUP_SUMMARY);
      // Badge count should only be one as only one notification is visible in the notification area.
      assertEquals(1, ShadowBadgeCountUpdater.lastCount);

      // Should be 2 DB entries (summary and individual)
      SQLiteDatabase readableDb = dbHelper.getSQLiteDatabaseWithRetries();
      Cursor cursor = readableDb.query(NotificationTable.TABLE_NAME, null, null, null, null, null, null);
      assertEquals(2, cursor.getCount());
      cursor.close();

      // Add another notification to the group.
      ShadowRoboNotificationManager.notifications.clear();
      bundle = new Bundle();
      bundle.putString("alert", "Notif test 2");
      bundle.putString("custom", "{\"i\": \"UUID2\"}");
      bundle.putString("grp", "test1");
      NotificationBundleProcessor_ProcessFromFCMIntentService(blankActivity, bundle, null);
      threadAndTaskWait();

      postedNotifs = ShadowRoboNotificationManager.notifications;
      assertEquals(2, postedNotifs.size());
      assertEquals(2, ShadowBadgeCountUpdater.lastCount);

      postedNotifsIterator = postedNotifs.entrySet().iterator();
      postedNotification = postedNotifsIterator.next().getValue();
      assertEquals("2 new messages",postedNotification.getShadow().getContentText());
      assertEquals(Notification.FLAG_GROUP_SUMMARY, postedNotification.notif.flags & Notification.FLAG_GROUP_SUMMARY);

      // Test Android Wear notification
      postedNotification = postedNotifsIterator.next().getValue();
      assertEquals("Notif test 2", postedNotification.getShadow().getContentText());
      assertEquals(0, postedNotification.notif.flags & Notification.FLAG_GROUP_SUMMARY);

      // Should be 3 DB entries (summary and 2 individual)
      readableDb = dbHelper.getSQLiteDatabaseWithRetries();
      cursor = readableDb.query(NotificationTable.TABLE_NAME, null, null, null, null, null, null);
      assertEquals(3, cursor.getCount());

      // Open summary notification
      postedNotifsIterator = postedNotifs.entrySet().iterator();
      postedNotification = postedNotifsIterator.next().getValue();
      Intent intent = createOpenIntent(postedNotification.id, bundle).putExtra("summary", "test1");
      NotificationOpenedProcessor_processFromContext(blankActivity, intent);
      // Wait for remote params call
      threadAndTaskWait();

      assertEquals(0, ShadowBadgeCountUpdater.lastCount);
      // 2 open calls should fire + remote params + 2 players call
      assertEquals(5, ShadowOneSignalRestClient.networkCallCount);
      assertEquals("notifications/UUID2", ShadowOneSignalRestClient.requests.get(3).url);
      assertEquals("notifications/UUID", ShadowOneSignalRestClient.requests.get(4).url);
      ShadowRoboNotificationManager.notifications.clear();

      // Send 3rd notification
      bundle = new Bundle();
      bundle.putString("alert", "Notif test 3");
      bundle.putString("custom", "{\"i\": \"UUID3\"}");
      bundle.putString("grp", "test1");
      NotificationBundleProcessor_ProcessFromFCMIntentService(blankActivity, bundle, null);
      threadAndTaskWait();

      postedNotifsIterator = postedNotifs.entrySet().iterator();
      postedNotification = postedNotifsIterator.next().getValue();
      assertEquals("Notif test 3", postedNotification.getShadow().getContentText());
      assertEquals(Notification.FLAG_GROUP_SUMMARY, postedNotification.notif.flags & Notification.FLAG_GROUP_SUMMARY);
      assertEquals(1, ShadowBadgeCountUpdater.lastCount);
      cursor.close();
   }
   
   @Test
   public void shouldHandleOpeningInAppAlertWithGroupKeySet() {
      SQLiteDatabase writableDb = dbHelper.getSQLiteDatabaseWithRetries();
      NotificationSummaryManager_updateSummaryNotificationAfterChildRemoved(blankActivity, writableDb, "some_group", false);
   }

   @Test
   @Config(shadows = {ShadowFCMBroadcastReceiver.class, ShadowGenerateNotification.class })
   public void shouldSetButtonsCorrectly() throws Exception {
      final Intent intent = new Intent();
      intent.setAction("com.google.android.c2dm.intent.RECEIVE");
      intent.putExtra("message_type", "gcm");
      Bundle bundle = getBaseNotifBundle();
      addButtonsToReceivedPayload(bundle);
      intent.putExtras(bundle);

      FCMBroadcastReceiver_onReceived_withIntent(blankActivity, intent);
      threadAndTaskWait();

      // Normal notifications should be generated right from the BroadcastReceiver
      //   without creating a service.
      assertNull(Shadows.shadowOf(blankActivity).getNextStartedService());
      
      Map<Integer, PostedNotification> postedNotifs = ShadowRoboNotificationManager.notifications;
      Iterator<Map.Entry<Integer, PostedNotification>> postedNotifsIterator = postedNotifs.entrySet().iterator();
      PostedNotification lastNotification = postedNotifsIterator.next().getValue();
      
      assertEquals(1, lastNotification.notif.actions.length);
      String json_data = shadowOf(lastNotification.notif.actions[0].actionIntent).getSavedIntent().getStringExtra(BUNDLE_KEY_ONESIGNAL_DATA);
      assertEquals("id1", new JSONObject(json_data).optString(BUNDLE_KEY_ACTION_ID));
   }

   @Test
   @Config(shadows = { ShadowGenerateNotification.class })
   public void shouldSetAlertnessFieldsOnNormalPriority() {
      Bundle bundle = getBaseNotifBundle();
      bundle.putString("pri", "5"); // Notifications from dashboard have priority 5 by default
      NotificationBundleProcessor_ProcessFromFCMIntentService(blankActivity, bundle, null);

      assertEquals(NotificationCompat.PRIORITY_DEFAULT, ShadowRoboNotificationManager.getLastNotif().priority);
      final int alertnessFlags = Notification.DEFAULT_SOUND | Notification.DEFAULT_LIGHTS | Notification.DEFAULT_VIBRATE;
      assertEquals(alertnessFlags, ShadowRoboNotificationManager.getLastNotif().defaults);
   }

   @Test
   @Config(shadows = { ShadowGenerateNotification.class })
   public void shouldNotSetAlertnessFieldsOnLowPriority() throws Exception {
      Bundle bundle = getBaseNotifBundle();
      bundle.putString("pri", "4");
      NotificationBundleProcessor_ProcessFromFCMIntentService(blankActivity, bundle, null);
      threadAndTaskWait();

      assertEquals(NotificationCompat.PRIORITY_LOW, ShadowRoboNotificationManager.getLastNotif().priority);
      assertEquals(0, ShadowRoboNotificationManager.getLastNotif().defaults);
   }

   @Test
   @Config(shadows = { ShadowGenerateNotification.class })
   public void shouldSetExpireTimeCorrectlyFromGoogleTTL() {
      long sentTime = 1_553_035_338_000L;
      long ttl = 60L;

      Bundle bundle = getBaseNotifBundle();
      bundle.putLong("google.sent_time", sentTime);
      bundle.putLong("google.ttl", ttl);
      NotificationBundleProcessor_ProcessFromFCMIntentService(blankActivity, bundle, null);

      HashMap<String, Object> notification = TestHelpers.getAllNotificationRecords(dbHelper).get(0);
      long expireTime = (Long)notification.get(NotificationTable.COLUMN_NAME_EXPIRE_TIME);
      assertEquals(sentTime + (ttl * 1_000), expireTime * 1_000);
   }

   @Test
   @Config(shadows = { ShadowGenerateNotification.class })
   public void shouldSetExpireTimeCorrectlyWhenMissingFromPayload() {
      NotificationBundleProcessor_ProcessFromFCMIntentService(blankActivity, getBaseNotifBundle(), null);

      long expireTime = (Long)TestHelpers.getAllNotificationRecords(dbHelper).get(0).get(NotificationTable.COLUMN_NAME_EXPIRE_TIME);
      assertEquals((SystemClock.currentThreadTimeMillis() / 1_000L) + 259_200, expireTime);
   }

   // TODO: Once we figure out the correct way to process notifications with high priority using the WorkManager
//   @Test
//   @Config(shadows = {ShadowFCMBroadcastReceiver.class}, sdk = 26)
//   public void shouldStartFCMServiceOnAndroidOWhenPriorityIsHighAndContainsRemoteResource() {
//      Intent intentFCM = new Intent();
//      intentFCM.setAction("com.google.android.c2dm.intent.RECEIVE");
//      intentFCM.putExtra("message_type", "gcm");
//      Bundle bundle = getBaseNotifBundle();
//      bundle.putString("pri", "10");
//      bundle.putString("licon", "http://domain.com/image.jpg");
//      addButtonsToReceivedPayload(bundle);
//      intentFCM.putExtras(bundle);
//
//      FCMBroadcastReceiver broadcastReceiver = new FCMBroadcastReceiver();
//      broadcastReceiver.onReceive(ApplicationProvider.getApplicationContext(), intentFCM);
//
//      Intent blankActivityIntent = Shadows.shadowOf(blankActivity).getNextStartedService();
//      assertEquals(FCMIntentService.class.getName(), blankActivityIntent.getComponent().getClassName());
//   }

   private static @NonNull Bundle inAppPreviewMockPayloadBundle() throws JSONException {
      Bundle bundle = new Bundle();
      bundle.putString("custom", new JSONObject() {{
         put("i", "UUID");
         put("a", new JSONObject() {{
            put("os_in_app_message_preview_id", "UUID");
         }});
      }}.toString());
      return bundle;
   }

   @Test
   @Config(shadows = { ShadowOneSignalRestClient.class, ShadowOSWebView.class })
   public void shouldShowInAppPreviewWhenInFocus() throws Exception {
      OneSignal.setAppId("b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      OneSignal.initWithContext(blankActivity);
      blankActivityController.resume();
      threadAndTaskWait();

      Intent intent = new Intent();
      intent.setAction("com.google.android.c2dm.intent.RECEIVE");
      intent.putExtra("message_type", "gcm");
      intent.putExtras(inAppPreviewMockPayloadBundle());

      new FCMBroadcastReceiver().onReceive(blankActivity, intent);
      threadAndTaskWait();

      assertEquals("PGh0bWw+PC9odG1sPg==", ShadowOSWebView.lastData);
   }

   @Test
   @Config(shadows = { ShadowOneSignalRestClient.class, ShadowOSWebView.class })
   public void shouldShowInAppPreviewWhenOpeningPreviewNotification() throws Exception {
      OneSignal.setAppId("b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      OneSignal.initWithContext(blankActivity);
      blankActivityController.resume();
      threadAndTaskWait();

      Bundle bundle = new Bundle();
      bundle.putString("custom", new JSONObject() {{
         put("i", "UUID1");
         put("a", new JSONObject() {{
            put("os_in_app_message_preview_id", "UUID");
         }});
      }}.toString());

      Intent notificationOpenIntent = createOpenIntent(2, inAppPreviewMockPayloadBundle());
      NotificationOpenedProcessor_processFromContext(blankActivity, notificationOpenIntent);

      assertEquals("PGh0bWw+PC9odG1sPg==", ShadowOSWebView.lastData);
   }

   @Test
   @Config(shadows = { ShadowReceiveReceiptController.class, ShadowGenerateNotification.class })
   public void shouldSendReceivedReceiptWhenEnabled() throws Exception {
      String appId = "b2f7f966-d8cc-11e4-bed1-df8f05be55ba";
      OneSignal.setAppId(appId);
      OneSignal.initWithContext(blankActivity);
      threadAndTaskWait();
      NotificationBundleProcessor_ProcessFromFCMIntentService(blankActivity, getBaseNotifBundle(), null);
      threadAndTaskWait();

      assertReportReceivedAtIndex(
         2,
         "UUID",
         new JSONObject().put("app_id", appId).put("player_id", ShadowOneSignalRestClient.pushUserId)
      );
   }

   @Test
   public void shouldNotSendReceivedReceiptWhenDisabled() throws Exception {
      String appId = "b2f7f966-d8cc-11e4-bed1-df8f05be55ba";
      OneSignal.setAppId(appId);
      OneSignal.initWithContext(blankActivity);
      threadAndTaskWait();

      NotificationBundleProcessor_ProcessFromFCMIntentService(blankActivity, getBaseNotifBundle(), null);
      threadAndTaskWait();

      assertRestCalls(2);
   }

   @Test
   @Config(sdk = 17, shadows = { ShadowGenerateNotification.class })
   public void testNotificationExtensionServiceOverridePropertiesWithSummaryApi17() throws Exception {
      // 1. Setup notification extension service as well as notifications to receive
      setupNotificationExtensionServiceOverridePropertiesWithSummary();

      Map<Integer, PostedNotification> postedNotifs = ShadowRoboNotificationManager.notifications;
      Iterator<Map.Entry<Integer, PostedNotification>> postedNotifsIterator = postedNotifs.entrySet().iterator();

      // 2. First notification should be the summary with the custom sound set
      PostedNotification postedSummaryNotification = postedNotifsIterator.next().getValue();
      assertThat(postedSummaryNotification.notif.flags & Notification.DEFAULT_SOUND, not(Notification.DEFAULT_SOUND));
      assertEquals("content://media/internal/audio/media/32", postedSummaryNotification.notif.sound.toString());

      // 3. Make sure only 1 summary notification has been posted
      assertEquals(1, postedNotifs.size());
   }

   @Test
   @Config(sdk = 21, shadows = { ShadowGenerateNotification.class })
   public void testNotificationExtensionServiceOverridePropertiesWithSummary() throws Exception {
      // 1. Setup notification extension service as well as received notifications/summary
      setupNotificationExtensionServiceOverridePropertiesWithSummary();

      Map<Integer, PostedNotification> postedNotifs = ShadowRoboNotificationManager.notifications;
      Iterator<Map.Entry<Integer, PostedNotification>> postedNotifsIterator = postedNotifs.entrySet().iterator();

      // 2. First notification should be the summary with the custom sound set
      PostedNotification postedSummaryNotification = postedNotifsIterator.next().getValue();
      assertThat(postedSummaryNotification.notif.flags & Notification.DEFAULT_SOUND, not(Notification.DEFAULT_SOUND));
      assertEquals("content://media/internal/audio/media/32", postedSummaryNotification.notif.sound.toString());

      // 3. Individual notification 1 should not play a sound
      PostedNotification notification = postedNotifsIterator.next().getValue();
      assertThat(notification.notif.flags & Notification.DEFAULT_SOUND, not(Notification.DEFAULT_SOUND));
      assertNull(notification.notif.sound);

      // 4. Individual notification 2 should not play a sound
      notification = postedNotifsIterator.next().getValue();
      assertThat(notification.notif.flags & Notification.DEFAULT_SOUND, not(Notification.DEFAULT_SOUND));
      assertNull(notification.notif.sound);
   }

   /**
    * Test to make sure changed bodies and titles are used for the summary notification
    * @see #testNotificationExtensionServiceOverridePropertiesWithSummaryApi17
    * @see #testNotificationExtensionServiceOverridePropertiesWithSummary
    */
   private void setupNotificationExtensionServiceOverridePropertiesWithSummary() throws Exception {
      // 1. Setup correct notification extension service class
      startNotificationExtensionService("com.test.onesignal.GenerateNotificationRunner$" +
              "NotificationExtensionService_notificationProcessingOverrideProperties");

      // 2. Add app context and setup the established notification extension service
      OneSignal.initWithContext(ApplicationProvider.getApplicationContext());
      OneSignal_setupNotificationExtensionServiceClass();

      // 3. Post 2 notifications with the same grp key so a summary is generated
      Bundle bundle = getBaseNotifBundle("UUID1");
      bundle.putString("grp", "test1");
      FCMBroadcastReceiver_processBundle(blankActivity, bundle);
      threadAndTaskWait();

      bundle = getBaseNotifBundle("UUID2");
      bundle.putString("grp", "test1");
      FCMBroadcastReceiver_processBundle(blankActivity, bundle);
      threadAndTaskWait();

      Map<Integer, PostedNotification> postedNotifs = ShadowRoboNotificationManager.notifications;
      Iterator<Map.Entry<Integer, PostedNotification>> postedNotifsIterator = postedNotifs.entrySet().iterator();

      // 4. First notification should be the summary
      PostedNotification postedSummaryNotification = postedNotifsIterator.next().getValue();
      assertEquals("2 new messages", postedSummaryNotification.getShadow().getContentText());
      if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT)
         assertEquals(Notification.FLAG_GROUP_SUMMARY, postedSummaryNotification.notif.flags & Notification.FLAG_GROUP_SUMMARY);

      // 5. Make sure summary build saved and used the developer's extender settings for the body and title
      if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR2) {
         CharSequence[] lines = postedSummaryNotification.notif.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
         for (CharSequence line : lines)
            assertEquals("[Modified Title] [Modified Body(ContentText)]", line.toString());
      }
   }

   /**
    * @see #testNotificationExtensionServiceOverridePropertiesWithSummaryApi17
    * @see #testNotificationExtensionServiceOverridePropertiesWithSummary
    */
   public static class NotificationExtensionService_notificationProcessingOverrideProperties implements OneSignal.NotificationProcessingHandler {

      @Override
      public void notificationProcessing(Context context, OSNotificationReceived notification) {
         OSNotificationExtender.OverrideSettings overrideSettings = new OSNotificationExtender.OverrideSettings();
         overrideSettings.setExtender(new NotificationCompat.Extender() {
            @Override
            public NotificationCompat.Builder extend(NotificationCompat.Builder builder) {
               // Must disable the default sound when setting a custom one
               try {
                  Field mNotificationField = NotificationCompat.Builder.class.getDeclaredField("mNotification");
                  mNotificationField.setAccessible(true);
                  Notification mNotification = (Notification) mNotificationField.get(builder);

                  mNotification.flags &= ~Notification.DEFAULT_SOUND;
                  builder.setDefaults(mNotification.flags);
               } catch (Throwable t) {
                  t.printStackTrace();
               }

               return builder.setSound(Uri.parse("content://media/internal/audio/media/32"))
                       .setColor(new BigInteger("FF00FF00", 16).intValue())
                       .setContentTitle("[Modified Title]")
                       .setStyle(new NotificationCompat.BigTextStyle().bigText("[Modified Body(bigText)]"))
                       .setContentText("[Modified Body(ContentText)]");
            }
         });
         notification.setModifiedContent(overrideSettings);

         // Display called to show notification
         OSNotificationDisplayedResult notificationDisplayedResult = notification.display();

         // Complete is called to end NotificationProcessingHandler
         notification.complete();
      }
   }

   @Test
   @Config(shadows = { ShadowOneSignal.class, ShadowGenerateNotification.class })
   public void testNotificationExtensionService_notificationProcessingProperties() throws Exception {
      // 1. Setup correct notification extension service class
      startNotificationExtensionService("com.test.onesignal.GenerateNotificationRunner$" +
              "NotificationExtensionService_notificationProcessingProperties");

      // 2. Add app context and setup the established notification extension service
      OneSignal.initWithContext(ApplicationProvider.getApplicationContext());
      OneSignal_setupNotificationExtensionServiceClass();

      // 3. Test that WorkManager begins processing the notification
      boolean ret = FCMBroadcastReceiver_processBundle(blankActivity, getBaseNotifBundle());
      threadAndTaskWait();
      assertTrue(ret);

      // 4. Receive a notification with all data fields used
      FCMBroadcastReceiver_processBundle(blankActivity, getBundleWithAllOptionsSet());
      threadAndTaskWait();

      // 5. Evaluate the notification received within the NotificationProcessingHandler
      OSNotificationReceived notificationReceived = NotificationExtensionService_notificationProcessingProperties.notification;
      OSNotificationPayload notificationPayload = notificationReceived.getPayload();
      assertEquals("Test H", notificationPayload.getTitle());
      assertEquals("Test B", notificationPayload.getBody());
      assertEquals("9764eaeb-10ce-45b1-a66d-8f95938aaa51", notificationPayload.getNotificationID());

      assertEquals(0, notificationPayload.getLockScreenVisibility());
      assertEquals("FF0000FF", notificationPayload.getSmallIconAccentColor());
      assertEquals("703322744261", notificationPayload.getFromProjectNumber());
      assertEquals("FFFFFF00", notificationPayload.getLedColor());
      assertEquals("big_picture", notificationPayload.getBigPicture());
      assertEquals("large_icon", notificationPayload.getLargeIcon());
      assertEquals("small_icon", notificationPayload.getSmallIcon());
      assertEquals("test_sound", notificationPayload.getSound());
      assertEquals("You test $[notif_count] MSGs!", notificationPayload.getGroupMessage());
      assertEquals("http://google.com", notificationPayload.getLaunchURL());
      assertEquals(10, notificationPayload.getPriority());
      assertEquals("a_key", notificationPayload.getCollapseId());

      assertEquals("id1", notificationPayload.getActionButtons().get(0).getId());
      assertEquals("button1", notificationPayload.getActionButtons().get(0).getText());
      assertEquals("ic_menu_share", notificationPayload.getActionButtons().get(0).getIcon());
      assertEquals("id2", notificationPayload.getActionButtons().get(1).getId());
      assertEquals("button2", notificationPayload.getActionButtons().get(1).getText());
      assertEquals("ic_menu_send", notificationPayload.getActionButtons().get(1).getIcon());

      assertEquals("test_image_url", notificationPayload.getBackgroundImageLayout().getImage());
      assertEquals("FF000000", notificationPayload.getBackgroundImageLayout().getTitleTextColor());
      assertEquals("FFFFFFFF", notificationPayload.getBackgroundImageLayout().getBodyTextColor());

      JSONObject additionalData = notificationPayload.getAdditionalData();
      assertEquals("myValue", additionalData.getString("myKey"));
      assertEquals("nValue", additionalData.getJSONObject("nested").getString("nKey"));

      // 6. Make sure the notification id is not -1 (not restoring)
      assertThat(NotificationExtensionService_notificationProcessingProperties.notificationId, not(-1));

      // 7. Test a basic notification without anything special
      FCMBroadcastReceiver_processBundle(blankActivity, getBaseNotifBundle());
      threadAndTaskWait();
      assertFalse(ShadowOneSignal.messages.contains("Error assigning"));

      // 8. Test that a notification is still displayed if the developer's code in onNotificationProcessing throws an Exception
      NotificationExtensionService_notificationProcessingProperties.throwInAppCode = true;
      FCMBroadcastReceiver_processBundle(blankActivity, getBaseNotifBundle("NewUUID1"));
      threadAndTaskWait();

      // 9. Make sure 3 notifications have been posted
      assertTrue(ShadowOneSignal.messages.contains("onNotificationProcessing throw an exception"));
      Map<Integer, PostedNotification> postedNotifs = ShadowRoboNotificationManager.notifications;
      assertEquals(3, postedNotifs.size());
   }

   /**
    * @see #testNotificationExtensionService_notificationProcessingProperties
    */
   public static class NotificationExtensionService_notificationProcessingProperties implements OneSignal.NotificationProcessingHandler {
      public static OSNotificationReceived notification;
      public static int notificationId = -1;
      public static boolean throwInAppCode;

      @Override
      public void notificationProcessing(Context context, OSNotificationReceived notification) {
         if (throwInAppCode)
            throw new NullPointerException();

         NotificationExtensionService_notificationProcessingProperties.notification = notification;

         OSNotificationExtender.OverrideSettings overrideSettings = new OSNotificationExtender.OverrideSettings();
         notification.setModifiedContent(overrideSettings);

         OSNotificationDisplayedResult notificationDisplayedResult = notification.display();
         notificationId = notificationDisplayedResult.getAndroidNotificationId();

         notification.complete();
      }
   }

   @Test
   @Config(shadows = { ShadowGenerateNotification.class })
   public void testNotificationProcessing_twoNotificationsWithSameOverrideAndroidNotificationId() throws Exception {
      // 1. Setup correct notification extension service class
      startNotificationExtensionService("com.test.onesignal.GenerateNotificationRunner$" +
              "NotificationExtensionService_notificationProcessingOverrideAndroidNotification");

      // 2. Add app context and setup the established notification extension service
      OneSignal.initWithContext(ApplicationProvider.getApplicationContext());
      OneSignal_setupNotificationExtensionServiceClass();

      // 3. Generate two notifications with different API notification ids
      FCMBroadcastReceiver_processBundle(blankActivity, getBaseNotifBundle("NewUUID1"));
      threadAndTaskWait();

      // 4. Make sure service was called
      assertNotNull(lastNotificationReceived);
      lastNotificationReceived = null;

      FCMBroadcastReceiver_processBundle(blankActivity, getBaseNotifBundle("NewUUID2"));
      threadAndTaskWait();

      // 5. Make sure service was called
      assertNotNull(lastNotificationReceived);

      // 6. Only 1 notification count should exist since the same Android Notification Id was used
      assertEquals(1, ShadowBadgeCountUpdater.lastCount);
   }

   /**
    * @see #testNotificationProcessing_twoNotificationsWithSameOverrideAndroidNotificationId
    */
   public static class NotificationExtensionService_notificationProcessingOverrideAndroidNotification implements OneSignal.NotificationProcessingHandler {

      @Override
      public void notificationProcessing(final Context context, OSNotificationReceived notification) {
         lastNotificationReceived = notification;

         OSNotificationExtender.OverrideSettings overrideSettings = new OSNotificationExtender.OverrideSettings();
         overrideSettings.setAndroidNotificationId(1);
         notification.setModifiedContent(overrideSettings);

         // Display called to show notification
         OSNotificationDisplayedResult notificationDisplayedResult = notification.display();

         // Complete is called to end NotificationProcessingHandler
         notification.complete();
      }
   }

   @Test
   // No MainThread mock since notification is not being display
   public void testNotificationProcessing_whenAlertIsNull() throws Exception {
      // 1. Setup correct notification extension service class
      startNotificationExtensionService("com.test.onesignal.GenerateNotificationRunner$" +
              "NotificationExtensionService_notificationProcessingDisplayAndComplete");

      // 2. Add app context and setup the established notification extension service
      OneSignal.initWithContext(ApplicationProvider.getApplicationContext());
      OneSignal_setupNotificationExtensionServiceClass();

      // 3. Receive a notification
      Bundle bundle = getBaseNotifBundle();
      bundle.remove("alert");
      FCMBroadcastReceiver_processBundle(blankActivity, bundle);
      threadAndTaskWait();

      // 4. Make sure service was called
      assertNotNull(lastNotificationReceived);

      // 5. Make sure 1 notification exists in DB
      assertNotificationDbRecords(1);
   }

   @Test
   @Config(shadows = { ShadowGenerateNotification.class })
   public void testNotificationProcessing_whenAppSwipedAway() throws Exception {
      // 1. Setup correct notification extension service class
      startNotificationExtensionService("com.test.onesignal.GenerateNotificationRunner$" +
              "NotificationExtensionService_notificationProcessingDisplayAndComplete");

      // First init run for appId to be saved
      // At least OneSignal was init once for user to be subscribed
      // If this doesn't' happen, notifications will not arrive
      OneSignal.setAppId(ONESIGNAL_APP_ID);
      OneSignal.initWithContext(blankActivity);
      threadAndTaskWait();
      fastColdRestartApp();
      // We only care about request post first init
      ShadowOneSignalRestClient.resetStatics();

      // 2. Add app context and setup the established notification extension service under init
      // We should not wait for thread to finish since in true behaviour app will continue processing even if thread are running
      // Example: don't wait for remote param call Extension Service should be set independently of the threads
      OneSignal.initWithContext(ApplicationProvider.getApplicationContext());

      // 3. Receive a notification
      Bundle bundle = getBaseNotifBundle();
      FCMBroadcastReceiver_processBundle(blankActivity, bundle);
      threadAndTaskWait();

      // 4. Make sure service was called
      assertNotNull(lastNotificationReceived);

      // 5. Make sure 1 notification exists in DB
      assertNotificationDbRecords(1);
   }

   /**
    * @see #testNotificationProcessing_whenAlertIsNull
    * @see #testNotificationProcessing_whenAppSwipedAway
    */
   public static class NotificationExtensionService_notificationProcessingDisplayAndComplete implements OneSignal.NotificationProcessingHandler {

      @Override
      public void notificationProcessing(Context context, OSNotificationReceived notification) {
         lastNotificationReceived = notification;

         // Display called to show notification
         OSNotificationDisplayedResult notificationDisplayedResult = notification.display();

         // Complete is called to end NotificationProcessingHandler
         notification.complete();
      }
   }

   @Test
   @Config(shadows = { ShadowGenerateNotification.class })
   public void testNotificationProcessing_displayNotCalled() throws Exception {
      // 1. Setup correct notification extension service class
      startNotificationExtensionService("com.test.onesignal.GenerateNotificationRunner$" +
              "NotificationExtensionService_notificationProcessingDisplayNotCalled");

      // 2. Add app context and setup the established notification extension service
      OneSignal.initWithContext(ApplicationProvider.getApplicationContext());
      OneSignal_setupNotificationExtensionServiceClass();

      // 3. Receive a notification
      Bundle bundle = getBaseNotifBundle();
      FCMBroadcastReceiver_processBundle(blankActivity, bundle);
      threadAndTaskWait();

      // 4. Make sure service was called
      assertNotNull(lastNotificationReceived);

      // 5. Make sure running on main thread check was not called, this is only called for showing the notification
      assertFalse(ShadowGenerateNotification.isRunningOnMainThreadCheckCalled());

      // 6. Make sure 1 notification exists in DB
      assertNotificationDbRecords(1);
   }

   @Test
   @Config(shadows = { ShadowGenerateNotification.class })
   public void testNotificationProcessingAndForegroundHandler_displayNotCalled_notCallsForegroundHandler() throws Exception {
      // 1. Setup correct notification extension service class
      startNotificationExtensionService("com.test.onesignal.GenerateNotificationRunner$" +
              "NotificationExtensionService_notificationProcessingDisplayNotCalled");

      // 2. Init OneSignal
      OneSignal.setAppId("b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      OneSignal.initWithContext(blankActivity);
      OneSignal.setNotificationWillShowInForegroundHandler(new OneSignal.NotificationWillShowInForegroundHandler() {
         @Override
         public void notificationWillShowInForeground(OSNotificationGenerationJob.AppNotificationGenerationJob notificationJob) {
            lastAppNotificationJob = notificationJob;
            // Call complete to end without waiting default 30 second timeout
            notificationJob.complete();
         }
      });
      threadAndTaskWait();

      blankActivityController.resume();
      threadAndTaskWait();

      // 3. Receive a notification in foreground
      FCMBroadcastReceiver_processBundle(blankActivity, getBaseNotifBundle());
      threadAndTaskWait();

      // 4. Make sure service was called
      assertNotNull(lastNotificationReceived);

      // 5. Make sure running on main thread check was not called, this is only called for showing the notification
      assertFalse(ShadowGenerateNotification.isRunningOnMainThreadCheckCalled());

      // 6. Make sure the AppNotificationJob is null
      assertNull(lastAppNotificationJob);

      // 7. Make sure 1 notification exists in DB
      assertNotificationDbRecords(1);
   }

   /**
    * @see #testNotificationProcessing_displayNotCalled
    * @see #testNotificationProcessingAndForegroundHandler_displayNotCalled_notCallsForegroundHandler
    */
   public static class NotificationExtensionService_notificationProcessingDisplayNotCalled implements OneSignal.NotificationProcessingHandler {

      @Override
      public void notificationProcessing(Context context, OSNotificationReceived notification) {
         lastNotificationReceived = notification;

         // No call display, this will avoid notification display

         // Complete is called to end NotificationProcessingHandler
         notification.complete();
      }
   }

   @Test
   @Config(shadows = { ShadowGenerateNotification.class })
   public void testNotificationProcessing_completeNotCalled() throws Exception {
      // 1. Setup correct notification extension service class
      startNotificationExtensionService("com.test.onesignal.GenerateNotificationRunner$" +
              "NotificationExtensionService_notificationProcessingCompleteNotCalled");

      // 2. Add app context and setup the established notification extension service
      OneSignal.initWithContext(ApplicationProvider.getApplicationContext());
      OneSignal_setupNotificationExtensionServiceClass();

      // 3. Receive a notification
      Bundle bundle = getBaseNotifBundle();
      FCMBroadcastReceiver_processBundle(blankActivity, bundle);
      threadAndTaskWait();

      // 4. Make sure service was called
      assertNotNull(lastNotificationReceived);

      // 5. Make sure 1 notification exists in DB
      assertNotificationDbRecords(1);
   }

   @Test
   @Config(shadows = { ShadowGenerateNotification.class })
   public void testNotificationProcessingAndForegroundHandler_completeNotCalled_callsForegroundHandler() throws Exception {
      // 1. Setup correct notification extension service class
      startNotificationExtensionService("com.test.onesignal.GenerateNotificationRunner$" +
              "NotificationExtensionService_notificationProcessingCompleteNotCalled");

      // 2. Init OneSignal
      OneSignal.setAppId("b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      OneSignal.initWithContext(blankActivity);
      OneSignal.setNotificationWillShowInForegroundHandler(new OneSignal.NotificationWillShowInForegroundHandler() {
         @Override
         public void notificationWillShowInForeground(OSNotificationGenerationJob.AppNotificationGenerationJob notificationJob) {
            lastAppNotificationJob = notificationJob;
            // Call complete to end without waiting default 30 second timeout
            notificationJob.complete();
         }
      });
      threadAndTaskWait();

      blankActivityController.resume();
      threadAndTaskWait();

      // 3. Receive a notification in foreground
      FCMBroadcastReceiver_processBundle(blankActivity, getBaseNotifBundle());
      threadAndTaskWait();

      // 4. Make sure service was called
      assertNotNull(lastNotificationReceived);

      // 5. Make sure the AppNotificationJob is not null
      assertNotNull(lastAppNotificationJob);
      assertEquals(OneSignal.OSNotificationDisplay.NOTIFICATION, lastAppNotificationJob.getNotificationDisplayOption());

      // 6. Make sure 1 notification exists in DB
      assertNotificationDbRecords(1);
   }

   /**
    * @see #testNotificationProcessing_completeNotCalled
    * @see #testNotificationProcessingAndForegroundHandler_completeNotCalled_callsForegroundHandler
    */
   public static class NotificationExtensionService_notificationProcessingCompleteNotCalled implements OneSignal.NotificationProcessingHandler {

      @Override
      public void notificationProcessing(Context context, OSNotificationReceived notification) {
         lastNotificationReceived = notification;

         // Display called to show notification
         OSNotificationDisplayedResult notificationDisplayedResult = notification.display();

         // Complete not called to end NotificationProcessingHandler, depend on timeout to finish
      }
   }

   @Test
   @Config(shadows = { ShadowGenerateNotification.class })
   public void testNotificationWillShowInForegroundHandlerIsCallWhenReceivingNotificationInForeground() throws Exception {
      // 1. Init OneSignal
      OneSignal.setAppId("b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      OneSignal.initWithContext(blankActivity);
      OneSignal.setNotificationWillShowInForegroundHandler(new OneSignal.NotificationWillShowInForegroundHandler() {
         @Override
         public void notificationWillShowInForeground(OSNotificationGenerationJob.AppNotificationGenerationJob notificationJob) {
            lastAppNotificationJob = notificationJob;
            // Call complete to end without waiting default 30 second timeout
            notificationJob.complete();
         }
      });
      threadAndTaskWait();

      blankActivityController.resume();
      threadAndTaskWait();

      // 2. Receive a notification
      FCMBroadcastReceiver_processBundle(blankActivity, getBaseNotifBundle());
      threadAndTaskWait();

      // 3. Make sure the AppNotificationJob is not null
      assertNotNull(lastAppNotificationJob);
      assertEquals(OneSignal.OSNotificationDisplay.NOTIFICATION, lastAppNotificationJob.getNotificationDisplayOption());

      // 4. Make sure 1 notification exists in DB
      assertNotificationDbRecords(1);
   }

   @Test
   @Config(shadows = { ShadowGenerateNotification.class })
   public void testNotificationWillShowInForegroundHandler_setNotificationDisplayOptionToNotification() throws Exception {
      // 1. Init OneSignal
      OneSignal.setAppId("b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      OneSignal.initWithContext(blankActivity);
      OneSignal.setNotificationWillShowInForegroundHandler(new OneSignal.NotificationWillShowInForegroundHandler() {
         @Override
         public void notificationWillShowInForeground(OSNotificationGenerationJob.AppNotificationGenerationJob notificationJob) {
            notificationJob.setNotificationDisplayOption(OneSignal.OSNotificationDisplay.NOTIFICATION);
            lastAppNotificationJob = notificationJob;
            // Call complete to end without waiting default 30 second timeout
            notificationJob.complete();
         }
      });
      threadAndTaskWait();

      blankActivityController.resume();
      threadAndTaskWait();

      // 2. Receive a notification
      FCMBroadcastReceiver_processBundle(blankActivity, getBaseNotifBundle());
      threadAndTaskWait();

      // 3. Make sure the AppNotificationJob is not null
      assertNotNull(lastAppNotificationJob);
      assertEquals(OneSignal.OSNotificationDisplay.NOTIFICATION, lastAppNotificationJob.getNotificationDisplayOption());

      // 4. Make sure 1 notification exists in DB
      assertNotificationDbRecords(1);
   }

   @Test
   @Config(shadows = { ShadowGenerateNotification.class })
   public void testNotificationWillShowInForegroundHandler_setNotificationDisplayOptionToSilent() throws Exception {
      // 1. Init OneSignal
      OneSignal.setAppId("b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      OneSignal.initWithContext(blankActivity);
      OneSignal.setNotificationWillShowInForegroundHandler(new OneSignal.NotificationWillShowInForegroundHandler() {
         @Override
         public void notificationWillShowInForeground(OSNotificationGenerationJob.AppNotificationGenerationJob notificationJob) {
            notificationJob.setNotificationDisplayOption(OneSignal.OSNotificationDisplay.SILENT);
            lastAppNotificationJob = notificationJob;
            // Call complete to end without waiting default 30 second timeout
            notificationJob.complete();
         }
      });
      threadAndTaskWait();

      blankActivityController.resume();
      threadAndTaskWait();

      // 2. Receive a notification
      FCMBroadcastReceiver_processBundle(blankActivity, getBaseNotifBundle());
      threadAndTaskWait();

      // 3. Make sure the AppNotificationJob is not null
      assertNotNull(lastAppNotificationJob);
      assertEquals(OneSignal.OSNotificationDisplay.SILENT, lastAppNotificationJob.getNotificationDisplayOption());

      // 4. Make sure 1 notification exists in DB
      assertNotificationDbRecords(1);
   }

   @Test
   @Config(shadows = { ShadowGenerateNotification.class })
   public void testNotificationCompletesFlows_withNullNotificationWillShowInForegroundHandler() throws Exception {
      // 1. Init OneSignal
      OneSignal.setAppId("b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      OneSignal.initWithContext(blankActivity);
      threadAndTaskWait();

      blankActivityController.resume();
      threadAndTaskWait();

      // 3. Receive a notification
      FCMBroadcastReceiver_processBundle(blankActivity, getBaseNotifBundle());
      threadAndTaskWait();

      // 4. Make sure the AppNotificationJob is null
      assertNull(lastAppNotificationJob);

      // 5. Make sure 1 notification exists in DB
      assertNotificationDbRecords(1);
   }

   @Test
   @Config(shadows = { ShadowGenerateNotification.class })
   public void testNotificationWillShowInForegroundHandler_doesNotFireWhenAppBackgrounded() throws Exception {
      // 1. Init OneSignal
      OneSignal.setAppId("b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      OneSignal.initWithContext(blankActivity);
      OneSignal.setNotificationWillShowInForegroundHandler(new OneSignal.NotificationWillShowInForegroundHandler() {
         @Override
         public void notificationWillShowInForeground(OSNotificationGenerationJob.AppNotificationGenerationJob notificationJob) {
            lastAppNotificationJob = notificationJob;
            // Call complete to end without waiting default 30 second timeout
            notificationJob.complete();
         }
      });
      threadAndTaskWait();

      // 3. Background the app
      blankActivityController.pause();
      threadAndTaskWait();

      // 4. Receive a notification
      FCMBroadcastReceiver_processBundle(blankActivity, getBaseNotifBundle());
      threadAndTaskWait();

      // 5. Make sure the AppNotificationJob is null since the app is in background when receiving a notification
      assertNull(lastAppNotificationJob);

      // 6. Make sure 1 notification exists in DB
      assertNotificationDbRecords(1);
   }

   @Test
   @Config(shadows = { ShadowGenerateNotification.class, ShadowTimeoutHandler.class })
   public void testNotificationWillShowInForegroundHandler_notCallCompleteShowsNotificationAfterTimeout() throws Exception {
      // 1. Init OneSignal
      OneSignal.setAppId("b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      OneSignal.initWithContext(blankActivity);
      OneSignal.setNotificationWillShowInForegroundHandler(new OneSignal.NotificationWillShowInForegroundHandler() {
         @Override
         public void notificationWillShowInForeground(OSNotificationGenerationJob.AppNotificationGenerationJob notificationJob) {
            callbackCounter++;
            lastAppNotificationJob = notificationJob;
         }
      });
      threadAndTaskWait();

      blankActivityController.resume();
      threadAndTaskWait();

      // Mock timeout to 1, given that we are not calling complete inside NotificationWillShowInForegroundHandler we depend on the timeout complete
      ShadowTimeoutHandler.setMockDelayMillis(1);
      // 2. Receive a notification
      FCMBroadcastReceiver_processBundle(blankActivity, getBaseNotifBundle());
      threadAndTaskWait();

      // 3. Make sure the AppNotificationJob is not null
      assertNotNull(lastAppNotificationJob);
      assertEquals(OneSignal.OSNotificationDisplay.NOTIFICATION, lastAppNotificationJob.getNotificationDisplayOption());

      // 4. Make sure the callback counter is only fired once for App NotificationWillShowInForegroundHandler
      assertEquals(1, callbackCounter);

      // 5. Make sure 1 notification exists in DB
      assertNotificationDbRecords(1);
   }

   @Test
   @Config(shadows = { ShadowGenerateNotification.class })
   public void testNotificationWillShowInForegroundHandler_notificationJobPayload() throws Exception {
      // 1. Init OneSignal
      OneSignal.setAppId("b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      OneSignal.initWithContext(blankActivity);
      OneSignal.setNotificationWillShowInForegroundHandler(new OneSignal.NotificationWillShowInForegroundHandler() {
         @Override
         public void notificationWillShowInForeground(OSNotificationGenerationJob.AppNotificationGenerationJob notificationJob) {
            lastAppNotificationJob = notificationJob;

            try {
               // Make sure all of the accessible getters have the expected values
               assertEquals("UUID1", notificationJob.getApiNotificationId());
               assertEquals("title1", notificationJob.getTitle());
               assertEquals("Notif message 1", notificationJob.getBody());
               JsonAsserts.equals(new JSONObject("{\"myKey1\": \"myValue1\", \"myKey2\": \"myValue2\"}"), notificationJob.getAdditionalData());
            } catch (JSONException e) {
               e.printStackTrace();
            }

            // Call complete to end without waiting default 30 second timeout
            notificationJob.complete();
         }
      });

      blankActivityController.resume();
      threadAndTaskWait();

      // 2. Receive a notification
      // Setup - Display a single notification with a grouped.
      Bundle bundle = getBaseNotifBundle("UUID1");
      bundle.putString("title", "title1");
      bundle.putString("alert", "Notif message 1");
      bundle.putString("custom", "{\"i\": \"UUID1\", " +
              "                    \"a\": {" +
              "                              \"myKey1\": \"myValue1\", " +
              "                              \"myKey2\": \"myValue2\"" +
              "                           }" +
              "                   }");
      FCMBroadcastReceiver_processBundle(blankActivity, bundle);
      threadAndTaskWait();

      // 3. Make sure the AppNotificationJob is not null
      assertNotNull(lastAppNotificationJob);
      // Not restoring or duplicate notification, so the Android notif id should not be -1
      assertNotEquals(-1, lastAppNotificationJob.getAndroidNotificationId());
      assertEquals(OneSignal.OSNotificationDisplay.NOTIFICATION, lastAppNotificationJob.getNotificationDisplayOption());

      // 4. Make sure 1 notification exists in DB
      assertNotificationDbRecords(1);
   }

   @Test
   @Config(shadows = { ShadowTimeoutHandler.class })
   public void testNotificationWillShowInForegroundHandler_workTimeLongerThanTimeout() throws Exception {
      // 1. Init OneSignal
      OneSignal.setAppId("b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      OneSignal.initWithContext(blankActivity);
      OneSignal.setNotificationWillShowInForegroundHandler(new OneSignal.NotificationWillShowInForegroundHandler() {
         @Override
         public void notificationWillShowInForeground(final OSNotificationGenerationJob.AppNotificationGenerationJob notificationJob) {
            callbackCounter++;
            lastAppNotificationJob = notificationJob;

            // Simulate doing work for 3 seconds
            try {
               Thread.sleep(3_000L);
               notificationJob.complete();
            } catch (InterruptedException e) {
               e.printStackTrace();
            }
         }
      });
      threadAndTaskWait();

      blankActivityController.resume();
      threadAndTaskWait();

      // Mock timeout to 1, given that we are delaying the complete inside NotificationExtensionService and NotificationWillShowInForegroundHandler
      // We depend on the timeout complete
      ShadowTimeoutHandler.setMockDelayMillis(1);
      // 2. Receive a notification
      FCMBroadcastReceiver_processBundle(blankActivity, getBaseNotifBundle());
      threadAndTaskWait();

      // 3. Make sure AppNotificationJob is not null
      assertNotNull(lastAppNotificationJob);
      assertEquals(OneSignal.OSNotificationDisplay.NOTIFICATION, lastAppNotificationJob.getNotificationDisplayOption());

      // 4. Make sure the callback counter is only fired once for App NotificationWillShowInForegroundHandler
      assertEquals(1, callbackCounter);

      // 5. Make sure 1 notification exists in DB
      assertNotificationDbRecords(1);
   }

   /**
    * Add the correct manifest meta-data key and value regarding the NotificationExtensionServiceClass to the
    *    mocked OneSignalShadowPackageManager metaData Bundle
    */
   private void startNotificationExtensionService(String servicePath) {
      OneSignalShadowPackageManager.addManifestMetaData("com.onesignal.NotificationExtensionServiceClass", servicePath);
   }

   /* Helpers */
   
   private static void assertNoNotifications() {
      assertEquals(0, ShadowRoboNotificationManager.notifications.size());
   }

   private static Bundle getBundleWithAllOptionsSet() {
      Bundle bundle = new Bundle();

      bundle.putString("title", "Test H");
      bundle.putString("alert", "Test B");
      bundle.putString("vis", "0");
      bundle.putString("bgac", "FF0000FF");
      bundle.putString("from", "703322744261");
      bundle.putString("ledc", "FFFFFF00");
      bundle.putString("bicon", "big_picture");
      bundle.putString("licon", "large_icon");
      bundle.putString("sicon", "small_icon");
      bundle.putString("sound", "test_sound");
      bundle.putString("grp_msg", "You test $[notif_count] MSGs!");
      bundle.putString("collapse_key", "a_key"); // FCM sets this to 'do_not_collapse' when not set.
      bundle.putString("bg_img", "{\"img\": \"test_image_url\"," +
                                  "\"tc\": \"FF000000\"," +
                                  "\"bc\": \"FFFFFFFF\"}");

      bundle.putInt("pri", 10);
      bundle.putString("custom",
                     "{\"a\": {" +
                     "        \"myKey\": \"myValue\"," +
                     "        \"nested\": {\"nKey\": \"nValue\"}," +
                     "        \"actionButtons\": [{\"id\": \"id1\", \"text\": \"button1\", \"icon\": \"ic_menu_share\"}," +
                     "                            {\"id\": \"id2\", \"text\": \"button2\", \"icon\": \"ic_menu_send\"}" +
                     "        ]," +
                     "         \"actionId\": \"__DEFAULT__\"" +
                     "      }," +
                     "\"u\":\"http://google.com\"," +
                     "\"i\":\"9764eaeb-10ce-45b1-a66d-8f95938aaa51\"" +
                     "}");

      return bundle;
   }

   private void assertNotificationDbRecords(int expected) {
      SQLiteDatabase readableDb = dbHelper.getSQLiteDatabaseWithRetries();
      Cursor cursor = readableDb.query(NotificationTable.TABLE_NAME, new String[] { "created_time" }, null, null, null, null, null);
      assertEquals(expected, cursor.getCount());
      cursor.close();
   }

   private void addButtonsToReceivedPayload(@NonNull Bundle bundle) {
      try {
         JSONArray buttonList = new JSONArray() {{
            put(new JSONObject() {{
               put(PUSH_MINIFIED_BUTTON_ID, "id1");
               put(PUSH_MINIFIED_BUTTON_TEXT, "text1");
            }});
         }};
         bundle.putString(PUSH_MINIFIED_BUTTONS_LIST, buttonList.toString());
      } catch (JSONException e) {
         e.printStackTrace();
      }
   }

}
