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
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.widget.Button;

import com.onesignal.BuildConfig;
import com.onesignal.BundleCompat;
import com.onesignal.GcmBroadcastReceiver;
import com.onesignal.GcmIntentService;
import com.onesignal.NotificationExtenderService;
import com.onesignal.OSNotification;
import com.onesignal.OSNotificationOpenResult;
import com.onesignal.OSNotificationPayload;
import com.onesignal.OSNotificationReceivedResult;
import com.onesignal.OneSignal;
import com.onesignal.OneSignalDbHelper;
import com.onesignal.OneSignalPackagePrivateHelper;
import com.onesignal.RestoreJobService;
import com.onesignal.ShadowBadgeCountUpdater;
import com.onesignal.ShadowGcmBroadcastReceiver;
import com.onesignal.ShadowNotificationManagerCompat;
import com.onesignal.ShadowOSUtils;
import com.onesignal.ShadowOneSignal;
import com.onesignal.ShadowOneSignalRestClient;
import com.onesignal.ShadowRoboNotificationManager;
import com.onesignal.ShadowRoboNotificationManager.PostedNotification;
import com.onesignal.StaticResetHelper;
import com.onesignal.example.BlankActivity;
import com.onesignal.OneSignalPackagePrivateHelper.NotificationTable;
import com.onesignal.OneSignalPackagePrivateHelper.NotificationRestorer;

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
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowLog;
import org.robolectric.shadows.ShadowSystemClock;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.android.controller.ServiceController;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.Map;

import static com.onesignal.OneSignalPackagePrivateHelper.NotificationBundleProcessor_ProcessFromGCMIntentService;
import static com.onesignal.OneSignalPackagePrivateHelper.NotificationBundleProcessor_ProcessFromGCMIntentService_NoWrap;
import static com.onesignal.OneSignalPackagePrivateHelper.NotificationOpenedProcessor_processFromContext;
import static com.onesignal.OneSignalPackagePrivateHelper.NotificationSummaryManager_updateSummaryNotificationAfterChildRemoved;
import static com.onesignal.OneSignalPackagePrivateHelper.createInternalPayloadBundle;

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
      constants = BuildConfig.class,
      instrumentedPackages = {"com.onesignal"},
      shadows = {
         ShadowRoboNotificationManager.class,
         ShadowOneSignalRestClient.class,
         ShadowBadgeCountUpdater.class,
         ShadowNotificationManagerCompat.class,
         ShadowOSUtils.class,
      },
      sdk = 21)
@RunWith(RobolectricTestRunner.class)
public class GenerateNotificationRunner {
   
   private Activity blankActivity;
   private static ActivityController<BlankActivity> blankActivityController;
   
   private static final String notifMessage = "Robo test message";
   
   @BeforeClass // Runs only once, before any tests
   public static void setUpClass() throws Exception {
      ShadowLog.stream = System.out;
      TestHelpers.beforeTestSuite();
      StaticResetHelper.saveStaticValues();
   }
   
   @Before // Before each test
   public void beforeEachTest() throws Exception {
      // Robolectric mocks System.currentTimeMillis() to 0, we need the current real time to match our SQL records.
      ShadowSystemClock.setCurrentTimeMillis(System.currentTimeMillis());
   
      blankActivityController = Robolectric.buildActivity(BlankActivity.class).create();
      blankActivity = blankActivityController.get();
      blankActivity.getApplicationInfo().name = "UnitTestApp";
   
      overrideNotificationId = -1;
      
      TestHelpers.beforeTestInitAndCleanup();

      NotificationManager notificationManager = (NotificationManager) blankActivity.getSystemService(Context.NOTIFICATION_SERVICE);
      notificationManager.cancelAll();
   }
   
   @AfterClass
   public static void afterEverything() {
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
          .putExtra("notificationId", notifId)
          .putExtra("onesignal_data", OneSignalPackagePrivateHelper.bundleAsJSONObject(bundle).toString());
   }
   
   private Intent createOpenIntent(Bundle bundle) {
      return createOpenIntent(ShadowRoboNotificationManager.lastNotifId, bundle);
   }
   
   @Test
   public void shouldSetTitleCorrectly() throws Exception {
      // Should use app's Title by default
      Bundle bundle = getBaseNotifBundle();
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);
      assertEquals("UnitTestApp", ShadowRoboNotificationManager.getLastShadowNotif().getContentTitle());
      assertEquals(1, ShadowBadgeCountUpdater.lastCount);
      
      // Should allow title from GCM payload.
      bundle = getBaseNotifBundle("UUID2");
      bundle.putString("title", "title123");
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);
      assertEquals("title123", ShadowRoboNotificationManager.getLastShadowNotif().getContentTitle());
      assertEquals(2, ShadowBadgeCountUpdater.lastCount);
   }
   
   @Test
   public void shouldProcessRestore() throws Exception {
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
   public void shouldContainPayloadWhenOldSummaryNotificationIsOpened() throws Exception {
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
      ShadowSystemClock.setCurrentTimeMillis(System.currentTimeMillis() + 2419202L * 1000L);
      
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
   public void shouldNotShowNotificationWhenAlertIsBlankOrNull() throws Exception {
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
      SQLiteDatabase readableDb = OneSignalDbHelper.getInstance(blankActivity).getReadableDatabase();
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
      readableDb = OneSignalDbHelper.getInstance(blankActivity).getReadableDatabase();
      cursor = readableDb.query(NotificationTable.TABLE_NAME, null, null, null, null, null, null);
      assertEquals(1, cursor.getCount());
      assertEquals(0, ShadowBadgeCountUpdater.lastCount);
      cursor.close();

      // Display a second notification
      bundle = getBaseNotifBundle("UUID2");
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);

      // Go forward 4 weeks
      // Note: Does not effect the SQL function strftime
      ShadowSystemClock.setCurrentTimeMillis(System.currentTimeMillis() + 2419201L * 1000L);

      // Display a 3rd notification
      // Should of been added for a total of 2 records now.
      // First opened should of been cleaned up, 1 week old non opened notification should stay, and one new record.
      bundle = getBaseNotifBundle("UUID3");
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);
      readableDb = OneSignalDbHelper.getInstance(blankActivity).getReadableDatabase();
      cursor = readableDb.query(NotificationTable.TABLE_NAME, new String[] { "android_notification_id", "created_time" }, null, null, null, null, null);

      assertEquals(1, cursor.getCount());
      assertEquals(1, ShadowBadgeCountUpdater.lastCount);

      cursor.close();
   }

   @Test
   public void shouldRestoreNotifications() throws Exception {
      NotificationRestorer.restore(blankActivity); NotificationRestorer.restored = false;

      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, getBaseNotifBundle(), null);

      NotificationRestorer.restore(blankActivity); NotificationRestorer.restored = false;
      Intent intent = Shadows.shadowOf(blankActivity).getNextStartedService();
      assertEquals(RestoreJobService.class.getName(), intent.getComponent().getClassName());

      // Go forward 1 week
      // Note: Does not effect the SQL function strftime
      ShadowSystemClock.setCurrentTimeMillis(System.currentTimeMillis() + 604801L * 1000L);

      // Restorer should not fire service since the notification is over 1 week old.
      NotificationRestorer.restore(blankActivity); NotificationRestorer.restored = false;
      assertNull(Shadows.shadowOf(blankActivity).getNextStartedService());
   }

   @Test
   public void shouldGenerate2BasicGroupNotifications() throws Exception {
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
      SQLiteDatabase readableDb = OneSignalDbHelper.getInstance(blankActivity).getReadableDatabase();
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
      readableDb = OneSignalDbHelper.getInstance(blankActivity).getReadableDatabase();
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
   public void shouldHandleOpeningInAppAlertWithGroupKeySet() throws Exception {
      SQLiteDatabase writableDb = OneSignalDbHelper.getInstance(RuntimeEnvironment.application).getWritableDatabase();
      NotificationSummaryManager_updateSummaryNotificationAfterChildRemoved(blankActivity, writableDb, "some_group", false);
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
      SQLiteDatabase writableDb = OneSignalDbHelper.getInstance(RuntimeEnvironment.application).getWritableDatabase();
      NotificationSummaryManager_updateSummaryNotificationAfterChildRemoved(blankActivity, writableDb, "test1", false);
      assertEquals(0, ShadowRoboNotificationManager.notifications.size());
   
   
      // Setup2 - Display a 2nd notification with the same group key
      bundle = getBaseNotifBundle("UUID2");
      bundle.putString("grp", "test1");
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);
   
      // Test2 - Manually trigger a refresh on grouped notification.
      writableDb = OneSignalDbHelper.getInstance(RuntimeEnvironment.application).getWritableDatabase();
      NotificationSummaryManager_updateSummaryNotificationAfterChildRemoved(blankActivity, writableDb, "test1", false);
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
      bundle.putString("o", "[{\"n\": \"text1\", \"i\": \"id1\"}]");
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
      String json_data = shadowOf(lastNotification.notif.actions[0].actionIntent).getSavedIntent().getStringExtra("onesignal_data");
      assertEquals("id1", new JSONObject(json_data).optString("actionSelected"));
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
      bundle.putString("o", "[{\"n\": \"text1\", \"i\": \"id1\"}]");
      intentGcm.putExtras(bundle);
      
      GcmBroadcastReceiver gcmBroadcastReceiver = new GcmBroadcastReceiver();
      gcmBroadcastReceiver.onReceive(blankActivity, intentGcm);
      
      assertEquals(Activity.RESULT_OK, (int)ShadowGcmBroadcastReceiver.lastResultCode);
      assertTrue(ShadowGcmBroadcastReceiver.calledAbortBroadcast);
   }
   
   @Test
   @Config(shadows = {ShadowGcmBroadcastReceiver.class}, sdk = 26)
   public void shouldStartGCMServiceOnAndroidOWhenPriorityIsHighAndContainsRemoteResource() throws Exception {
      
      Intent intentGcm = new Intent();
      intentGcm.setAction("com.google.android.c2dm.intent.RECEIVE");
      intentGcm.putExtra("message_type", "gcm");
      Bundle bundle = getBaseNotifBundle();
      bundle.putString("pri", "10");
      bundle.putString("licon", "http://domain.com/image.jpg");
      bundle.putString("o", "[{\"n\": \"text1\", \"i\": \"id1\"}]");
      intentGcm.putExtras(bundle);
      
      GcmBroadcastReceiver gcmBroadcastReceiver = new GcmBroadcastReceiver();
      gcmBroadcastReceiver.onReceive(blankActivity, intentGcm);
      
      Intent intent = Shadows.shadowOf(blankActivity).getNextStartedService();
      assertEquals(GcmIntentService.class.getName(), intent.getComponent().getClassName());
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
   public void notificationExtenderServiceOverrideShouldOverrideAndroidNotificationId() throws Exception {
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
   private void testNotificationExtenderServiceOverridePropertiesWithSummary() throws Exception {
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
                     "         \"actionSelected\": \"__DEFAULT__\"" +
                     "      }," +
                     "\"u\":\"http://google.com\"," +
                     "\"i\":\"9764eaeb-10ce-45b1-a66d-8f95938aaa51\"" +
                     "}");

      return bundle;
   }

   private void assertNotificationDbRecords(int expected) {
      SQLiteDatabase readableDb = OneSignalDbHelper.getInstance(blankActivity).getReadableDatabase();
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
}
