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
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.widget.Button;

import com.onesignal.BundleCompat;
import com.onesignal.GcmBroadcastReceiver;
import com.onesignal.GcmIntentService;
import com.onesignal.MockOneSignalDBHelper;
import com.onesignal.NotificationExtenderService;
import com.onesignal.OSNotification;
import com.onesignal.OSNotificationOpenResult;
import com.onesignal.OSNotificationPayload;
import com.onesignal.OSNotificationReceivedResult;
import com.onesignal.OneSignal;
import com.onesignal.OneSignalNotificationManagerPackageHelper;
import com.onesignal.OneSignalPackagePrivateHelper;
import com.onesignal.OneSignalPackagePrivateHelper.NotificationRestorer;
import com.onesignal.OneSignalPackagePrivateHelper.NotificationTable;
import com.onesignal.OneSignalPackagePrivateHelper.TestOneSignalPrefs;
import com.onesignal.RestoreJobService;
import com.onesignal.ShadowBadgeCountUpdater;
import com.onesignal.ShadowCustomTabsClient;
import com.onesignal.ShadowCustomTabsSession;
import com.onesignal.ShadowGcmBroadcastReceiver;
import com.onesignal.ShadowNotificationManagerCompat;
import com.onesignal.ShadowOSUtils;
import com.onesignal.ShadowOSViewUtils;
import com.onesignal.ShadowOSWebView;
import com.onesignal.ShadowOneSignal;
import com.onesignal.ShadowOneSignalRestClient;
import com.onesignal.ShadowReceiveReceiptController;
import com.onesignal.ShadowRoboNotificationManager;
import com.onesignal.ShadowRoboNotificationManager.PostedNotification;
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
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowLog;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static com.onesignal.OneSignalPackagePrivateHelper.GenerateNotification.BUNDLE_KEY_ACTION_ID;
import static com.onesignal.OneSignalPackagePrivateHelper.GenerateNotification.BUNDLE_KEY_ANDROID_NOTIFICATION_ID;
import static com.onesignal.OneSignalPackagePrivateHelper.GenerateNotification.BUNDLE_KEY_ONESIGNAL_DATA;
import static com.onesignal.OneSignalPackagePrivateHelper.NotificationBundleProcessor.PUSH_MINIFIED_BUTTONS_LIST;
import static com.onesignal.OneSignalPackagePrivateHelper.NotificationBundleProcessor.PUSH_MINIFIED_BUTTON_ID;
import static com.onesignal.OneSignalPackagePrivateHelper.NotificationBundleProcessor.PUSH_MINIFIED_BUTTON_TEXT;
import static com.onesignal.OneSignalPackagePrivateHelper.NotificationBundleProcessor_ProcessFromGCMIntentService;
import static com.onesignal.OneSignalPackagePrivateHelper.NotificationBundleProcessor_ProcessFromGCMIntentService_NoWrap;
import static com.onesignal.OneSignalPackagePrivateHelper.NotificationOpenedProcessor_processFromContext;
import static com.onesignal.OneSignalPackagePrivateHelper.NotificationSummaryManager_updateSummaryNotificationAfterChildRemoved;
import static com.onesignal.OneSignalPackagePrivateHelper.createInternalPayloadBundle;
import static com.onesignal.ShadowRoboNotificationManager.getNotificationsInGroup;
import static com.test.onesignal.RestClientAsserts.assertReportReceivedAtIndex;
import static com.test.onesignal.RestClientAsserts.assertRestCalls;
import static com.test.onesignal.TestHelpers.advanceSystemTimeBy;
import static com.test.onesignal.TestHelpers.fastColdRestartApp;
import static com.test.onesignal.TestHelpers.threadAndTaskWait;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;
import static org.robolectric.Shadows.shadowOf;

@Config(packageName = "com.onesignal.example",
        instrumentedPackages = { "com.onesignal" },
        shadows = {
            ShadowRoboNotificationManager.class,
            ShadowOneSignalRestClient.class,
            ShadowBadgeCountUpdater.class,
            ShadowNotificationManagerCompat.class,
            ShadowOSUtils.class,
            ShadowOSViewUtils.class,
            ShadowCustomTabsClient.class,
            ShadowCustomTabsSession.class
        },
        sdk = 21
)
@RunWith(RobolectricTestRunner.class)
public class GenerateNotificationRunner {
   
   private Activity blankActivity;
   private static ActivityController<BlankActivity> blankActivityController;
   
   private static final String notifMessage = "Robo test message";

   private MockOneSignalDBHelper dbHelper;

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
      dbHelper = new MockOneSignalDBHelper(RuntimeEnvironment.application);

      overrideNotificationId = -1;
      
      TestHelpers.beforeTestInitAndCleanup();

      setClearGroupSummaryClick(true);

      NotificationManager notificationManager = OneSignalNotificationManagerPackageHelper.getNotificationManager(blankActivity);
      notificationManager.cancelAll();
      NotificationRestorer.restored = false;
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
   @Config (sdk = 22)
   public void shouldSetTitleCorrectly() {
      // Should use app's Title by default
      Bundle bundle = getBaseNotifBundle();
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);
      assertEquals("UnitTestApp", ShadowRoboNotificationManager.getLastShadowNotif().getContentTitle());
      
      // Should allow title from GCM payload.
      bundle = getBaseNotifBundle("UUID2");
      bundle.putString("title", "title123");
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);
      assertEquals("title123", ShadowRoboNotificationManager.getLastShadowNotif().getContentTitle());
   }
   
   @Test
   @Config (sdk = 22)
   public void shouldProcessRestore() {
      BundleCompat bundle = createInternalPayloadBundle(getBaseNotifBundle());
      bundle.putInt("android_notif_id", 0);
      bundle.putBoolean("restoring", true);
      
      NotificationBundleProcessor_ProcessFromGCMIntentService_NoWrap(blankActivity, bundle, null);
      assertEquals("UnitTestApp", ShadowRoboNotificationManager.getLastShadowNotif().getContentTitle());
   }

   @Test
   @Config(sdk = Build.VERSION_CODES.O)
   public void shouldNotRestoreActiveNotifs() throws Exception {
      // Display a notification
      Bundle bundle = getBaseNotifBundle("UUID1");
      bundle.putString("grp", "test1");
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);

      //try to restore notifs
      NotificationRestorer.restore(blankActivity);

      threadAndTaskWait();

      //assert that no restoration jobs were scheduled...
      JobScheduler scheduler = (JobScheduler)blankActivity.getSystemService(Context.JOB_SCHEDULER_SERVICE);
      assertTrue(scheduler.getAllPendingJobs().isEmpty());
   }


   private static OSNotificationOpenResult lastOpenResult;
   
   @Test
   public void shouldContainPayloadWhenOldSummaryNotificationIsOpened() {
      OneSignal.setInFocusDisplaying(OneSignal.OSInFocusDisplayOption.Notification);
      OneSignal.init(blankActivity, "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba", new OneSignal.NotificationOpenedHandler() {
         @Override
         public void notificationOpened(OSNotificationOpenResult result) {
            lastOpenResult = result;
         }
      });
      
      // Display 2 notifications that will be grouped together.
      Bundle bundle = getBaseNotifBundle("UUID1");
      bundle.putString("grp", "test1");
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);
   
      bundle = getBaseNotifBundle("UUID2");
      bundle.putString("grp", "test1");
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);
   
      // Go forward 4 weeks
      advanceSystemTimeBy(2_419_202);
      
      // Display a 3 normal notification.
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, getBaseNotifBundle("UUID3"), null);
   
   
      Map<Integer, PostedNotification> postedNotifs = ShadowRoboNotificationManager.notifications;
      
      // Open the summary notification
      Iterator<Map.Entry<Integer, PostedNotification>> postedNotifsIterator = postedNotifs.entrySet().iterator();
      PostedNotification postedNotification = postedNotifsIterator.next().getValue();
      Intent intent = Shadows.shadowOf(postedNotification.notif.contentIntent).getSavedIntent();
      NotificationOpenedProcessor_processFromContext(blankActivity, intent);
      
      // Make sure we get a payload when it is opened.
      assertNotNull(lastOpenResult.notification.payload);
   }

   @Test
   public void shouldSetCorrectNumberOfButtonsOnSummaryNotification() throws Exception {
      // Setup - Init
      OneSignal.setInFocusDisplaying(OneSignal.OSInFocusDisplayOption.Notification);
      OneSignal.init(blankActivity, "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      threadAndTaskWait();
   
      // Setup - Display a single notification with a grouped.
      Bundle bundle = getBaseNotifBundle("UUID1");
      bundle.putString("grp", "test1");
      bundle.putString("custom", "{\"i\": \"some_UUID\", \"a\": {\"actionButtons\": [{\"text\": \"test\"} ]}}");
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);
   
   
      Map<Integer, PostedNotification> postedNotifs = ShadowRoboNotificationManager.notifications;
      Iterator<Map.Entry<Integer, PostedNotification>> postedNotifsIterator = postedNotifs.entrySet().iterator();
      PostedNotification postedSummaryNotification = postedNotifsIterator.next().getValue();
   
      assertEquals(Notification.FLAG_GROUP_SUMMARY, postedSummaryNotification.notif.flags & Notification.FLAG_GROUP_SUMMARY);
      assertEquals(1, postedSummaryNotification.notif.actions.length);
   }
   
   @Test
   public void shouldCancelAllNotificationsPartOfAGroup() throws Exception {
      // Setup - Init
      OneSignal.setInFocusDisplaying(OneSignal.OSInFocusDisplayOption.Notification);
      OneSignal.init(blankActivity, "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      threadAndTaskWait();
      
      // Setup - Display 3 notifications, 2 of which that will be grouped together.
      Bundle bundle = getBaseNotifBundle("UUID0");
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);
      
      bundle = getBaseNotifBundle("UUID1");
      bundle.putString("grp", "test1");
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);
      bundle = getBaseNotifBundle("UUID2");
      bundle.putString("grp", "test1");
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);
      
      assertEquals(4, ShadowRoboNotificationManager.notifications.size());
      
      OneSignal.cancelGroupedNotifications("test1");
      assertEquals(1, ShadowRoboNotificationManager.notifications.size());
   }


   @Test
   @Config(sdk = Build.VERSION_CODES.N)
   public void testFourNotificationsUseProvidedGroup() throws Exception {
      OneSignal.init(blankActivity.getApplicationContext(), "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      threadAndTaskWait();

      // Add 4 grouped notifications
      postNotificationWithOptionalGroup(4, "test1");

      assertEquals(4, getNotificationsInGroup("test1").size());
   }

   @Test
   @Config(sdk = Build.VERSION_CODES.N)
   public void testFourGrouplessNotificationsUseDefaultGroup() throws Exception {
      OneSignal.init(blankActivity.getApplicationContext(), "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      threadAndTaskWait();

      // Add 4 groupless notifications
      postNotificationWithOptionalGroup(4, null);

      assertEquals(4, getNotificationsInGroup("os_group_undefined").size());
   }

    @Test
    @Config(sdk = Build.VERSION_CODES.LOLLIPOP)
    public void testNotifDismissAllOnGroupSummaryClickForAndroidUnderM() throws Exception {
        OneSignal.setInFocusDisplaying(OneSignal.OSInFocusDisplayOption.Notification);
        OneSignal.init(blankActivity, "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
        threadAndTaskWait();

        SQLiteDatabase readableDb = dbHelper.getSQLiteDatabaseWithRetries();

        postNotificationsAndSimulateSummaryClick(true, "test1");

        // Validate SQL DB has removed all grouped notifs
        int activeGroupNotifCount = queryNotificationCountFromGroup(readableDb, "test1", true);
        assertEquals(0, activeGroupNotifCount);
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.LOLLIPOP)
    public void testNotifDismissRecentOnGroupSummaryClickForAndroidUnderM() throws Exception {
        OneSignal.setInFocusDisplaying(OneSignal.OSInFocusDisplayOption.Notification);
        OneSignal.init(blankActivity, "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
        threadAndTaskWait();

        SQLiteDatabase readableDb = dbHelper.getSQLiteDatabaseWithRetries();

        postNotificationsAndSimulateSummaryClick(false, "test1");

        // Validate SQL DB has removed most recent grouped notif
        int activeGroupNotifCount = queryNotificationCountFromGroup(readableDb, "test1", true);
        assertEquals(3, activeGroupNotifCount);
    }

   @Test
   @Config(sdk = Build.VERSION_CODES.N)
   public void testNotifDismissAllOnGroupSummaryClick() throws Exception {
      OneSignal.setInFocusDisplaying(OneSignal.OSInFocusDisplayOption.Notification);
      OneSignal.init(blankActivity, "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      threadAndTaskWait();

      SQLiteDatabase readableDb = dbHelper.getSQLiteDatabaseWithRetries();

      postNotificationsAndSimulateSummaryClick(true, "test1");

      // Validate SQL DB has removed all grouped notifs
      int activeGroupNotifCount = queryNotificationCountFromGroup(readableDb, "test1", true);
      assertEquals(0, activeGroupNotifCount);
   }

   @Test
   @Config(sdk = Build.VERSION_CODES.N)
   public void testNotifDismissRecentOnGroupSummaryClick() throws Exception {
      OneSignal.setInFocusDisplaying(OneSignal.OSInFocusDisplayOption.Notification);
      OneSignal.init(blankActivity, "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      threadAndTaskWait();

      SQLiteDatabase readableDb = dbHelper.getSQLiteDatabaseWithRetries();

      postNotificationsAndSimulateSummaryClick(false, "test1");

      // Validate SQL DB has removed most recent grouped notif
      int activeGroupNotifCount = queryNotificationCountFromGroup(readableDb, "test1", true);
      assertEquals(3, activeGroupNotifCount);
   }

   @Test
   @Config(sdk = Build.VERSION_CODES.N)
   public void testNotifDismissAllOnGrouplessSummaryClick() throws Exception {
      OneSignal.setInFocusDisplaying(OneSignal.OSInFocusDisplayOption.Notification);
      OneSignal.init(blankActivity, "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      threadAndTaskWait();

      SQLiteDatabase readableDb = dbHelper.getSQLiteDatabaseWithRetries();

      postNotificationsAndSimulateSummaryClick(true, null);

      // Validate SQL DB has removed all groupless notifs
      int activeGroupNotifCount = queryNotificationCountFromGroup(readableDb, null, true);
      assertEquals(0, activeGroupNotifCount);
   }

   @Test
   @Config(sdk = Build.VERSION_CODES.N)
   public void testNotifDismissRecentOnGrouplessSummaryClick() throws Exception {
      OneSignal.setInFocusDisplaying(OneSignal.OSInFocusDisplayOption.Notification);
      OneSignal.init(blankActivity, "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      threadAndTaskWait();

      SQLiteDatabase readableDb = dbHelper.getSQLiteDatabaseWithRetries();

      postNotificationsAndSimulateSummaryClick(false, null);

      // Validate SQL DB has removed most recent groupless notif
      int activeGroupNotifCount = queryNotificationCountFromGroup(readableDb, null, true);
      assertEquals(3, activeGroupNotifCount);
   }

   private void postNotificationsAndSimulateSummaryClick(boolean shouldDismissAll, String group) {
      // Add 4 notifications
      Bundle bundle = postNotificationWithOptionalGroup(4, group);
      setClearGroupSummaryClick(shouldDismissAll);

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
   }

   private void setClearGroupSummaryClick(boolean shouldDismissAll) {
      TestOneSignalPrefs.saveBool(TestOneSignalPrefs.PREFS_ONESIGNAL, TestOneSignalPrefs.PREFS_OS_CLEAR_GROUP_SUMMARY_CLICK, shouldDismissAll);
   }


   @Test
   @Config(sdk = Build.VERSION_CODES.N)
   public void testGrouplessSummaryKeyReassignmentAtFourOrMoreNotification() throws Exception {
      OneSignal.setInFocusDisplaying(OneSignal.OSInFocusDisplayOption.Notification);
      OneSignal.init(blankActivity, "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      threadAndTaskWait();

      // Add 3 groupless notifications
      postNotificationWithOptionalGroup(3, null);

      // Assert before 4, no notif summary is created
      int count = OneSignalNotificationManagerPackageHelper.getActiveNotifications(blankActivity).length;
      assertEquals(3, count);

      // Add 4 groupless notifications
      postNotificationWithOptionalGroup(4, null);

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

          NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);
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
   public void shouldCancelNotificationAndUpdateSummary() throws Exception {
      // Setup - Init
      OneSignal.setInFocusDisplaying(OneSignal.OSInFocusDisplayOption.Notification);
      OneSignal.init(blankActivity, "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      threadAndTaskWait();
      runImplicitServices(); // Flushes out other services, seems to be a roboelectric bug
      
      // Setup - Display 3 notifications that will be grouped together.
      Bundle bundle = getBaseNotifBundle("UUID1");
      bundle.putString("grp", "test1");
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);
   
      bundle = getBaseNotifBundle("UUID2");
      bundle.putString("grp", "test1");
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);
   
      bundle = getBaseNotifBundle("UUID3");
      bundle.putString("grp", "test1");
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);
   
      
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
   public void shouldUpdateBadgesWhenDismissingNotification() {
      Bundle bundle = getBaseNotifBundle();
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);
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
      OneSignal.init(blankActivity, "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      threadAndTaskWait();
      
      Bundle bundle = getBaseNotifBundle();
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);
      assertEquals(0, ShadowBadgeCountUpdater.lastCount);
   }

   @Test
   public void shouldSetBadgesWhenRestoringNotifications() {
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, getBaseNotifBundle(), null);
      ShadowBadgeCountUpdater.lastCount = 0;

      NotificationRestorer.restore(blankActivity);

      assertEquals(1, ShadowBadgeCountUpdater.lastCount);
   }

   @Test public void shouldNotRestoreNotificationsIfPermissionIsDisabled() {
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, getBaseNotifBundle(), null);

      ShadowNotificationManagerCompat.enabled = false;
      NotificationRestorer.restore(blankActivity);

      assertNull(Shadows.shadowOf(blankActivity).getNextStartedService());
   }
   
   @Test
   public void shouldNotShowNotificationWhenAlertIsBlankOrNull() {
      Bundle bundle = getBaseNotifBundle();
      bundle.remove("alert");
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);
   
      assertNoNotifications();
   
      bundle = getBaseNotifBundle("UUID2");
      bundle.putString("alert", "");
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);
   
      assertNoNotifications();

      assertNotificationDbRecords(2);
   }
   
   @Test
   public void shouldUpdateNormalNotificationDisplayWhenReplacingANotification() throws Exception {
      // Setup - init
      OneSignal.setInFocusDisplaying(OneSignal.OSInFocusDisplayOption.Notification);
      OneSignal.init(blankActivity, "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      threadAndTaskWait();
   
      // Setup - Display 2 notifications with the same group and collapse_id
      Bundle bundle = getBaseNotifBundle("UUID1");
      bundle.putString("grp", "test1");
      bundle.putString("collapse_key", "1");
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);
   
      bundle = getBaseNotifBundle("UUID2");
      bundle.putString("grp", "test1");
      bundle.putString("collapse_key", "1");
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);
   
      
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
      bundle2.putBoolean("restoring", true);
      NotificationBundleProcessor_ProcessFromGCMIntentService_NoWrap(blankActivity, bundle2, null);
      
      // Test - Restored notifications display exactly the same as they did when received.
      postedNotifs = ShadowRoboNotificationManager.notifications;
      postedNotifsIterator = postedNotifs.entrySet().iterator();
      // Test - 1 notifi + 1 summary
      assertEquals(2, postedNotifs.size());
      assertEquals(notifMessage, postedSummaryNotification.getShadow().getContentText());
      assertEquals(Notification.FLAG_GROUP_SUMMARY, postedSummaryNotification.notif.flags & Notification.FLAG_GROUP_SUMMARY);
   }
   

   @Test
   public void shouldHandleBasicNotifications() throws Exception {
      // Make sure the notification got posted and the content is correct.
      Bundle bundle = getBaseNotifBundle();
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);
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
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);
      readableDb = dbHelper.getSQLiteDatabaseWithRetries();
      cursor = readableDb.query(NotificationTable.TABLE_NAME, null, null, null, null, null, null);
      assertEquals(1, cursor.getCount());
      assertEquals(0, ShadowBadgeCountUpdater.lastCount);
      cursor.close();

      // Display a second notification
      bundle = getBaseNotifBundle("UUID2");
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);

      // Go forward 4 weeks
      // Note: Does not effect the SQL function strftime
      advanceSystemTimeBy(2_419_202);

      // Restart the app so OneSignalCacheCleaner can clean out old notifications
      fastColdRestartApp();
      threadAndTaskWait();

      // Display a 3rd notification
      // Should of been added for a total of 2 records now.
      // First opened should of been cleaned up, 1 week old non opened notification should stay, and one new record.
      bundle = getBaseNotifBundle("UUID3");
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);
      readableDb = dbHelper.getSQLiteDatabaseWithRetries();
      cursor = readableDb.query(NotificationTable.TABLE_NAME, new String[] { }, null, null, null, null, null);

      assertEquals(1, cursor.getCount());

      cursor.close();
   }

   @Test
   public void shouldRestoreNotifications() {
      NotificationRestorer.restore(blankActivity); NotificationRestorer.restored = false;

      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, getBaseNotifBundle(), null);

      NotificationRestorer.restore(blankActivity); NotificationRestorer.restored = false;
      Intent intent = Shadows.shadowOf(blankActivity).getNextStartedService();
      assertEquals(RestoreJobService.class.getName(), intent.getComponent().getClassName());

      // Go forward 1 week
      // Note: Does not effect the SQL function strftime
      advanceSystemTimeBy(604_801);

      // Restorer should not fire service since the notification is over 1 week old.
      NotificationRestorer.restore(blankActivity); NotificationRestorer.restored = false;
      assertNull(Shadows.shadowOf(blankActivity).getNextStartedService());
   }

   private void assertRestoreRan() {
      Intent intent = Shadows.shadowOf(blankActivity).getNextStartedService();
      assertEquals(RestoreJobService.class.getName(), intent.getComponent().getClassName());
   }
   private void assertRestoreDidNotRun() {
      assertNull(Shadows.shadowOf(blankActivity).getNextStartedService());
   }

   private void restoreNotifications() {
      NotificationRestorer.restored = false;
      NotificationRestorer.restore(blankActivity);
   }

   private void helperShouldRestoreNotificationsPastExpireTime(boolean should) {
      long ttl = 60L;
      Bundle bundle = getBaseNotifBundle();
      bundle.putLong("google.sent_time", System.currentTimeMillis());
      bundle.putLong("google.ttl", ttl);
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);

      restoreNotifications();
      assertRestoreRan();

      // Go forward just past the TTL of the notification
      advanceSystemTimeBy(ttl + 1);
      restoreNotifications();
      if (should)
         assertRestoreRan();
      else
         assertRestoreDidNotRun();
   }

   @Test
   public void doNotRestoreNotificationsPastExpireTime() {
      helperShouldRestoreNotificationsPastExpireTime(false);
   }

   @Test
   public void restoreNotificationsPastExpireTimeIfSettingIsDisabled() {
      TestOneSignalPrefs.saveBool(TestOneSignalPrefs.PREFS_ONESIGNAL, TestOneSignalPrefs.PREFS_OS_RESTORE_TTL_FILTER, false);
      helperShouldRestoreNotificationsPastExpireTime(true);
   }

   @Test
   public void badgeCountShouldNotIncludeOldNotifications() {
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, getBaseNotifBundle(), null);

      // Go forward 1 week
      advanceSystemTimeBy(604_801);

      // Should not count as a badge
      OneSignalPackagePrivateHelper.BadgeCountUpdater.update(dbHelper, blankActivity);
      assertEquals(0, ShadowBadgeCountUpdater.lastCount);
   }

   @Test
   public void shouldGenerate2BasicGroupNotifications() {
      // Make sure the notification got posted and the content is correct.
      Bundle bundle = getBaseNotifBundle();
      bundle.putString("grp", "test1");
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);

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
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);

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

      assertEquals(0, ShadowBadgeCountUpdater.lastCount);
      // 2 open calls should fire.
      assertEquals(2, ShadowOneSignalRestClient.networkCallCount);
      ShadowRoboNotificationManager.notifications.clear();

      // Send 3rd notification
      bundle = new Bundle();
      bundle.putString("alert", "Notif test 3");
      bundle.putString("custom", "{\"i\": \"UUID3\"}");
      bundle.putString("grp", "test1");
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);

      postedNotifsIterator = postedNotifs.entrySet().iterator();
      postedNotification = postedNotifsIterator.next().getValue();
      assertEquals("Notif test 3", postedNotification.getShadow().getContentText());
      assertEquals(Notification.FLAG_GROUP_SUMMARY, postedNotification.notif.flags & Notification.FLAG_GROUP_SUMMARY);
      assertEquals(1, ShadowBadgeCountUpdater.lastCount);
      cursor.close();
   }
   
   @Test
   public void shouldHandleOpeningInAppAlertWithGroupKeySet() {
      NotificationSummaryManager_updateSummaryNotificationAfterChildRemoved(blankActivity, dbHelper, "some_group", false);
   }
   
   @Test
   public void shouldNotDisplaySummaryWhenDismissingAnInAppAlertIfOneDidntAlreadyExist() throws Exception {
      // Setup - init
      OneSignal.setInFocusDisplaying(OneSignal.OSInFocusDisplayOption.InAppAlert);
      OneSignal.init(blankActivity, "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      threadAndTaskWait();
   
      // Setup1 - Display a notification with a group set
      Bundle bundle = getBaseNotifBundle("UUID1");
      bundle.putString("grp", "test1");
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);
   
      // Test1 - Manually trigger a refresh on grouped notification.
      NotificationSummaryManager_updateSummaryNotificationAfterChildRemoved(blankActivity, dbHelper, "test1", false);
      assertEquals(0, ShadowRoboNotificationManager.notifications.size());
   
   
      // Setup2 - Display a 2nd notification with the same group key
      bundle = getBaseNotifBundle("UUID2");
      bundle.putString("grp", "test1");
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);
   
      // Test2 - Manually trigger a refresh on grouped notification.
      NotificationSummaryManager_updateSummaryNotificationAfterChildRemoved(blankActivity, dbHelper, "test1", false);
      assertEquals(0, ShadowRoboNotificationManager.notifications.size());
   }
   
   
   @Test
   public void shouldCorrectlyDisplaySummaryWithMixedInAppAlertsAndNotifications() throws Exception {
      // Setup - init
      OneSignal.setInFocusDisplaying(OneSignal.OSInFocusDisplayOption.InAppAlert);
      OneSignal.init(blankActivity, "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      threadAndTaskWait();
   
      // Setup - Display a notification with a group set
      Bundle bundle = getBaseNotifBundle("UUID1");
      bundle.putString("grp", "test1");
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);
      
      assertEquals(0, ShadowRoboNotificationManager.notifications.size());
   
      // Setup - Background app
      blankActivityController.pause();
      threadAndTaskWait();
   
      // Setup - Send 2 more notifications with the same group
      bundle = getBaseNotifBundle("UUID2");
      bundle.putString("grp", "test1");
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);
      bundle = getBaseNotifBundle("UUID3");
      bundle.putString("grp", "test1");
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);
   
      // Test - equals 3 - Should be 2 notifications + 1 summary.
      //         Alert should stay as an in-app alert.
      assertEquals(3, ShadowRoboNotificationManager.notifications.size());
   }
   
   

   @Test
   @Config(shadows = {ShadowGcmBroadcastReceiver.class})
   public void shouldSetButtonsCorrectly() throws Exception {
      Intent intentGcm = new Intent();
      intentGcm.setAction("com.google.android.c2dm.intent.RECEIVE");
      intentGcm.putExtra("message_type", "gcm");
      Bundle bundle = getBaseNotifBundle();
      addButtonsToReceivedPayload(bundle);
      intentGcm.putExtras(bundle);

      GcmBroadcastReceiver gcmBroadcastReceiver = new GcmBroadcastReceiver();
      gcmBroadcastReceiver.onReceive(blankActivity, intentGcm);
      
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
   public void shouldSetAlertnessFieldsOnNormalPriority() {
      Bundle bundle = getBaseNotifBundle();
      bundle.putString("pri", "5"); // Notifications from dashboard have priority 5 by default
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);

      assertEquals(NotificationCompat.PRIORITY_DEFAULT, ShadowRoboNotificationManager.getLastNotif().priority);
      final int alertnessFlags = Notification.DEFAULT_SOUND | Notification.DEFAULT_LIGHTS | Notification.DEFAULT_VIBRATE;
      assertEquals(alertnessFlags, ShadowRoboNotificationManager.getLastNotif().defaults);
   }

   @Test
   public void shouldNotSetAlertnessFieldsOnLowPriority() {
      Bundle bundle = getBaseNotifBundle();
      bundle.putString("pri", "4");
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);

      assertEquals(NotificationCompat.PRIORITY_LOW, ShadowRoboNotificationManager.getLastNotif().priority);
      assertEquals(0, ShadowRoboNotificationManager.getLastNotif().defaults);
   }

   @Test
   public void shouldAddDefaultButtonToAlertDialog() throws Exception {
      OneSignal.setInFocusDisplaying(OneSignal.OSInFocusDisplayOption.InAppAlert);
      OneSignal.startInit(blankActivity).init();
      threadAndTaskWait();

      Bundle bundle = getBaseNotifBundle();
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);

      AlertDialog alert = ShadowAlertDialog.getLatestAlertDialog();
      Button button = alert.getButton(AlertDialog.BUTTON_NEUTRAL);
      assertEquals(button.getText(), "Ok");
   }
   
   @Test
   @Config(shadows = {ShadowGcmBroadcastReceiver.class})
   public void shouldPreventOtherGCMReceiversWhenSettingEnabled() throws Exception {
      OneSignal.setInFocusDisplaying(OneSignal.OSInFocusDisplayOption.InAppAlert);
      OneSignal.startInit(blankActivity).filterOtherGCMReceivers(true).init();
      threadAndTaskWait();
      
      
      Intent intentGcm = new Intent();
      intentGcm.setAction("com.google.android.c2dm.intent.RECEIVE");
      intentGcm.putExtra("message_type", "gcm");
      Bundle bundle = getBaseNotifBundle();
      addButtonsToReceivedPayload(bundle);
      intentGcm.putExtras(bundle);
      
      GcmBroadcastReceiver gcmBroadcastReceiver = new GcmBroadcastReceiver();
      gcmBroadcastReceiver.onReceive(blankActivity, intentGcm);
      
      assertEquals(Activity.RESULT_OK, (int)ShadowGcmBroadcastReceiver.lastResultCode);
      assertTrue(ShadowGcmBroadcastReceiver.calledAbortBroadcast);
   }


   @Test
   public void shouldSetExpireTimeCorrectlyFromGoogleTTL() {
      long sentTime = 1_553_035_338_000L;
      long ttl = 60L;

      Bundle bundle = getBaseNotifBundle();
      bundle.putLong("google.sent_time", sentTime);
      bundle.putLong("google.ttl", ttl);
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);

      HashMap<String, Object> notification = TestHelpers.getAllNotificationRecords(dbHelper).get(0);
      long expireTime = (Long)notification.get(NotificationTable.COLUMN_NAME_EXPIRE_TIME);
      assertEquals(sentTime + (ttl * 1_000), expireTime * 1_000);
   }

   @Test
   public void shouldSetExpireTimeCorrectlyWhenMissingFromPayload() {
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, getBaseNotifBundle(), null);

      long expireTime = (Long)TestHelpers.getAllNotificationRecords(dbHelper).get(0).get(NotificationTable.COLUMN_NAME_EXPIRE_TIME);
      assertEquals((SystemClock.currentThreadTimeMillis() / 1_000L) + 259_200, expireTime);
   }
   
   @Test
   @Config(shadows = {ShadowGcmBroadcastReceiver.class}, sdk = 26)
   public void shouldStartGCMServiceOnAndroidOWhenPriorityIsHighAndContainsRemoteResource() {
      
      Intent intentGcm = new Intent();
      intentGcm.setAction("com.google.android.c2dm.intent.RECEIVE");
      intentGcm.putExtra("message_type", "gcm");
      Bundle bundle = getBaseNotifBundle();
      bundle.putString("pri", "10");
      bundle.putString("licon", "http://domain.com/image.jpg");
      addButtonsToReceivedPayload(bundle);
      intentGcm.putExtras(bundle);
      
      GcmBroadcastReceiver gcmBroadcastReceiver = new GcmBroadcastReceiver();
      gcmBroadcastReceiver.onReceive(blankActivity, intentGcm);
      
      Intent intent = Shadows.shadowOf(blankActivity).getNextStartedService();
      assertEquals(GcmIntentService.class.getName(), intent.getComponent().getClassName());
   }

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
      OneSignal.init(blankActivity, "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      threadAndTaskWait();

      Intent intentGcm = new Intent();
      intentGcm.setAction("com.google.android.c2dm.intent.RECEIVE");
      intentGcm.putExtra("message_type", "gcm");
      intentGcm.putExtras(inAppPreviewMockPayloadBundle());

      new GcmBroadcastReceiver().onReceive(blankActivity, intentGcm);
      threadAndTaskWait();

      assertEquals("PGh0bWw+PC9odG1sPg==", ShadowOSWebView.lastData);
   }

   @Test
   @Config(shadows = { ShadowOneSignalRestClient.class, ShadowOSWebView.class })
   public void shouldShowInAppPreviewWhenOpeningPreviewNotification() throws Exception {
      OneSignal.init(blankActivity, "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
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
   @Config(shadows = { ShadowReceiveReceiptController.class })
   public void shouldSendReceivedReceiptWhenEnabled() throws Exception {
      String appId = "b2f7f966-d8cc-11e4-bed1-df8f05be55ba";
      OneSignal.init(blankActivity, "123456789", appId);
      threadAndTaskWait();
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, getBaseNotifBundle(), null);
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
      OneSignal.init(blankActivity, "123456789", appId);
      threadAndTaskWait();
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, getBaseNotifBundle(), null);
      threadAndTaskWait();

      assertRestCalls(2);
   }

   
   private OSNotification lastNotificationReceived;
   @Test
   public void shouldStillFireReceivedHandlerWhenNotificationExtenderServiceIsUsed() throws Exception {
      OneSignal.setInFocusDisplaying(OneSignal.OSInFocusDisplayOption.None);
      OneSignal.init(blankActivity, "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba", null, new OneSignal.NotificationReceivedHandler() {
         @Override
         public void notificationReceived(OSNotification notification) {
            lastNotificationReceived = notification;
         }
      });
      threadAndTaskWait();

      startNotificationExtender(createInternalPayloadBundle(getBaseNotifBundle()),
                                NotificationExtenderServiceTestReturnFalse.class);

      assertNotNull(lastNotificationReceived);
   }

   @Test
   public void shouldNotFailedNotificationExtenderServiceWhenAlertIsNull() throws Exception {
      OneSignal.setInFocusDisplaying(OneSignal.OSInFocusDisplayOption.Notification);
      OneSignal.init(blankActivity, "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      threadAndTaskWait();

      Bundle bundle = getBaseNotifBundle();
      bundle.remove("alert");

      startNotificationExtender(
         createInternalPayloadBundle(bundle),
         NotificationExtenderServiceTest.class
      );
      threadAndTaskWait();

      assertNotificationDbRecords(1);
   }
   
   @Test
   public void notificationExtenderServiceOverrideShouldOverrideAndroidNotificationId() {
      overrideNotificationId = 1;
      
      startNotificationExtender(createInternalPayloadBundle(getBaseNotifBundle("NewUUID1")),
          NotificationExtenderServiceTest.class);
      startNotificationExtender(createInternalPayloadBundle(getBaseNotifBundle("NewUUID2")),
          NotificationExtenderServiceTest.class);
      assertEquals(1, ShadowBadgeCountUpdater.lastCount);
   }
   
   
   @Test
   @Config(sdk = 17)
   public void notificationExtenderServiceOverridePropertiesWithSummaryApi17() throws Exception {
      testNotificationExtenderServiceOverridePropertiesWithSummary();
      
      Map<Integer, PostedNotification> postedNotifs = ShadowRoboNotificationManager.notifications;
      Iterator<Map.Entry<Integer, PostedNotification>> postedNotifsIterator = postedNotifs.entrySet().iterator();
      
      // Test - First notification should be the summary with the custom sound set.
      PostedNotification postedSummaryNotification = postedNotifsIterator.next().getValue();
      assertThat(postedSummaryNotification.notif.flags & Notification.DEFAULT_SOUND, not(Notification.DEFAULT_SOUND));
      assertEquals("content://media/internal/audio/media/32", postedSummaryNotification.notif.sound.toString());
   
      assertEquals(1, postedNotifs.size());
   }
   
   @Test
   @Config(sdk = 21)
   public void notificationExtenderServiceOverridePropertiesWithSummary() throws Exception {
      testNotificationExtenderServiceOverridePropertiesWithSummary();
      
      Map<Integer, PostedNotification> postedNotifs = ShadowRoboNotificationManager.notifications;
      Iterator<Map.Entry<Integer, PostedNotification>> postedNotifsIterator = postedNotifs.entrySet().iterator();
   
      // Test - First notification should be the summary with the custom sound set.
      PostedNotification postedSummaryNotification = postedNotifsIterator.next().getValue();
      assertThat(postedSummaryNotification.notif.flags & Notification.DEFAULT_SOUND, not(Notification.DEFAULT_SOUND));
      assertEquals("content://media/internal/audio/media/32", postedSummaryNotification.notif.sound.toString());
      
      // Test - individual notification 1 should not play a sound
      PostedNotification notification = postedNotifsIterator.next().getValue();
      assertThat(notification.notif.flags & Notification.DEFAULT_SOUND, not(Notification.DEFAULT_SOUND));
      assertNull(notification.notif.sound);
   
      // Test - individual notification 2 should not play a sound
      notification = postedNotifsIterator.next().getValue();
      assertThat(notification.notif.flags & Notification.DEFAULT_SOUND, not(Notification.DEFAULT_SOUND));
      assertNull(notification.notif.sound);
   }

   // Test to make sure changed bodies and titles are used for the summary notification.
   private void testNotificationExtenderServiceOverridePropertiesWithSummary() {
      Bundle bundle = getBaseNotifBundle("UUID1");
      bundle.putString("grp", "test1");
   
      startNotificationExtender(createInternalPayloadBundle(bundle),
          NotificationExtenderServiceOverrideProperties.class);
   
      bundle = getBaseNotifBundle("UUID2");
      bundle.putString("grp", "test1");
   
      startNotificationExtender(createInternalPayloadBundle(bundle),
          NotificationExtenderServiceOverrideProperties.class);
   
   
      Map<Integer, PostedNotification> postedNotifs = ShadowRoboNotificationManager.notifications;
      Iterator<Map.Entry<Integer, PostedNotification>> postedNotifsIterator = postedNotifs.entrySet().iterator();
   
      // Test - First notification should be the summary
      PostedNotification postedSummaryNotification = postedNotifsIterator.next().getValue();
      assertEquals("2 new messages", postedSummaryNotification.getShadow().getContentText());
      if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT)
         assertEquals(Notification.FLAG_GROUP_SUMMARY, postedSummaryNotification.notif.flags & Notification.FLAG_GROUP_SUMMARY);
   
      // Test - Make sure summary build saved and used the developer's extender settings for the body and title
      if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR2) {
         CharSequence[] lines = postedSummaryNotification.notif.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
         for (CharSequence line : lines)
            assertEquals("[Modified Tile] [Modified Body(ContentText)]", line.toString());
      }
   }
   

   private NotificationExtenderServiceTestBase startNotificationExtender(BundleCompat bundlePayload, Class serviceClass) {
      ServiceController<NotificationExtenderServiceTestBase> controller = Robolectric.buildService(serviceClass);
      NotificationExtenderServiceTestBase service = controller.create().get();
      Intent testIntent = new Intent(RuntimeEnvironment.application, NotificationExtenderServiceTestReturnFalse.class);
      testIntent.putExtras((Bundle)bundlePayload.getBundle());
      controller.withIntent(testIntent).startCommand(0, 0);

      return service;
   }

   @Test
   @Config(shadows = {ShadowOneSignal.class})
   public void shouldFireNotificationExtenderService() throws Exception {
      // Test that GCM receiver starts the NotificationExtenderServiceTest when it is in the AndroidManifest.xml
      Bundle bundle = getBaseNotifBundle();

      Intent serviceIntent = new Intent();
      serviceIntent.setPackage("com.onesignal.example");
      serviceIntent.setAction("com.onesignal.NotificationExtender");
      ResolveInfo resolveInfo = new ResolveInfo();
      resolveInfo.serviceInfo = new ServiceInfo();
      resolveInfo.serviceInfo.name = "com.onesignal.example.NotificationExtenderServiceTest";
      shadowOf(blankActivity.getPackageManager()).addResolveInfoForIntent(serviceIntent, resolveInfo);

      boolean ret = OneSignalPackagePrivateHelper.GcmBroadcastReceiver_processBundle(blankActivity, bundle);
      assertTrue(ret);
      
      // Test that all options are set.
      NotificationExtenderServiceTest service = (NotificationExtenderServiceTest)startNotificationExtender(createInternalPayloadBundle(getBundleWithAllOptionsSet()),
                                                                          NotificationExtenderServiceTest.class);

      OSNotificationReceivedResult notificationReceived = service.notification;
      OSNotificationPayload notificationPayload = notificationReceived.payload;
      assertEquals("Test H", notificationPayload.title);
      assertEquals("Test B", notificationPayload.body);
      assertEquals("9764eaeb-10ce-45b1-a66d-8f95938aaa51", notificationPayload.notificationID);

      assertEquals(0, notificationPayload.lockScreenVisibility);
      assertEquals("FF0000FF", notificationPayload.smallIconAccentColor);
      assertEquals("703322744261", notificationPayload.fromProjectNumber);
      assertEquals("FFFFFF00", notificationPayload.ledColor);
      assertEquals("big_picture", notificationPayload.bigPicture);
      assertEquals("large_icon", notificationPayload.largeIcon);
      assertEquals("small_icon", notificationPayload.smallIcon);
      assertEquals("test_sound", notificationPayload.sound);
      assertEquals("You test $[notif_count] MSGs!", notificationPayload.groupMessage);
      assertEquals("http://google.com", notificationPayload.launchURL);
      assertEquals(10, notificationPayload.priority);
      assertEquals("a_key", notificationPayload.collapseId);

      assertEquals("id1", notificationPayload.actionButtons.get(0).id);
      assertEquals("button1", notificationPayload.actionButtons.get(0).text);
      assertEquals("ic_menu_share", notificationPayload.actionButtons.get(0).icon);
      assertEquals("id2", notificationPayload.actionButtons.get(1).id);
      assertEquals("button2", notificationPayload.actionButtons.get(1).text);
      assertEquals("ic_menu_send", notificationPayload.actionButtons.get(1).icon);

      assertEquals("test_image_url", notificationPayload.backgroundImageLayout.image);
      assertEquals("FF000000", notificationPayload.backgroundImageLayout.titleTextColor);
      assertEquals("FFFFFFFF", notificationPayload.backgroundImageLayout.bodyTextColor);

      JSONObject additionalData = notificationPayload.additionalData;
      assertEquals("myValue", additionalData.getString("myKey"));
      assertEquals("nValue", additionalData.getJSONObject("nested").getString("nKey"));

      assertThat(service.notificationId, not(-1));


      // Test a basic notification without anything special.
      startNotificationExtender(createInternalPayloadBundle(getBaseNotifBundle()), NotificationExtenderServiceTest.class);
      assertFalse(ShadowOneSignal.messages.contains("Error assigning"));

      // Test that a notification is still displayed if the developer's code in onNotificationProcessing throws an Exception.
      NotificationExtenderServiceTest.throwInAppCode = true;
      startNotificationExtender(createInternalPayloadBundle(getBaseNotifBundle("NewUUID1")), NotificationExtenderServiceTest.class);

      assertTrue(ShadowOneSignal.messages.contains("onNotificationProcessing throw an exception"));
      Map<Integer, PostedNotification> postedNotifs = ShadowRoboNotificationManager.notifications;
      assertEquals(3, postedNotifs.size());
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
      bundle.putString("collapse_key", "a_key"); // GCM sets this to 'do_not_collapse' when not set.
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
   
   static abstract class NotificationExtenderServiceTestBase extends NotificationExtenderService {
      // Override onStartCommand to manually call onHandleIntent on the main thread.
      @Override
      public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
         onHandleWork(intent);
         stopSelf(startId);
         return START_REDELIVER_INTENT;
      }
   }
   
   
   static int overrideNotificationId;
   public static class NotificationExtenderServiceTest extends NotificationExtenderServiceTestBase {
      public OSNotificationReceivedResult notification;
      public int notificationId = -1;
      public static boolean throwInAppCode;
    

      @Override
      protected boolean onNotificationProcessing(OSNotificationReceivedResult notification) {
         if (throwInAppCode)
            throw new NullPointerException();

         this.notification = notification;
   
         OverrideSettings overrideSettings = new OverrideSettings();
         if (overrideNotificationId != -1)
            overrideSettings.androidNotificationId = overrideNotificationId;
         
         notificationId = displayNotification(overrideSettings).androidNotificationId;

         return true;
      }
   }
   
   public static class NotificationExtenderServiceOverrideProperties extends NotificationExtenderServiceTestBase {
      
      @Override
      protected boolean onNotificationProcessing(OSNotificationReceivedResult notification) {
         
         OverrideSettings overrideSettings = new OverrideSettings();
         overrideSettings.extender = new NotificationCompat.Extender() {
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
                   .setContentTitle("[Modified Tile]")
                   .setStyle(new NotificationCompat.BigTextStyle().bigText("[Modified Body(bigText)]"))
                   .setContentText("[Modified Body(ContentText)]");
            }
         };
         displayNotification(overrideSettings);
         
         return true;
      }
   }
   
   
   public static class NotificationExtenderServiceTestReturnFalse extends NotificationExtenderServiceTest {
      @Override
      protected boolean onNotificationProcessing(OSNotificationReceivedResult notification) {
         return false;
      }
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
