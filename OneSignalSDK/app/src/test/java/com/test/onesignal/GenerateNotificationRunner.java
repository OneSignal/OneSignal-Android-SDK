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

package com.test.onesignal;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;

import com.onesignal.BuildConfig;
import com.onesignal.GcmBroadcastReceiver;
import com.onesignal.GcmIntentService;
import com.onesignal.NotificationExtenderService;
import com.onesignal.NotificationOpenedProcessor;
import com.onesignal.OSNotificationPayload;
import com.onesignal.OSNotificationReceivedResult;
import com.onesignal.OneSignalDbHelper;
import com.onesignal.OneSignalPackagePrivateHelper;
import com.onesignal.ShadowBadgeCountUpdater;
import com.onesignal.ShadowOneSignal;
import com.onesignal.ShadowOneSignalRestClient;
import com.onesignal.ShadowRoboNotificationManager;
import com.onesignal.ShadowRoboNotificationManager.PostedNotification;
import com.onesignal.example.BlankActivity;
import com.onesignal.OneSignalPackagePrivateHelper.NotificationTable;
import com.onesignal.OneSignalPackagePrivateHelper.NotificationRestorer;

import junit.framework.Assert;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;
import org.robolectric.shadows.ShadowSystemClock;
import org.robolectric.util.ServiceController;

import java.util.Iterator;
import java.util.Map;

import static com.onesignal.OneSignalPackagePrivateHelper.NotificationBundleProcessor_ProcessFromGCMIntentService;
import static com.onesignal.OneSignalPackagePrivateHelper.NotificationBundleProcessor_ProcessFromGCMIntentService_NoWrap;
import static com.onesignal.OneSignalPackagePrivateHelper.createInternalPayloadBundle;

@Config(packageName = "com.onesignal.example",
      constants = BuildConfig.class,
      shadows = { ShadowRoboNotificationManager.class, ShadowOneSignalRestClient.class, ShadowBadgeCountUpdater.class },
      sdk = 21)
@RunWith(CustomRobolectricTestRunner.class)
public class GenerateNotificationRunner {

   private Activity blankActivity;

   private static final String notifMessage = "Robo test message";

   @BeforeClass // Runs only once, before any tests
   public static void setUpClass() throws Exception {
      ShadowLog.stream = System.out;
   }

   @Before // Before each test
   public void beforeEachTest() throws Exception {
      // Robolectric mocks System.currentTimeMillis() to 0, we need the current real time to match our SQL records.
      ShadowSystemClock.setCurrentTimeMillis(System.currentTimeMillis());

      blankActivity = Robolectric.buildActivity(BlankActivity.class).create().get();
      blankActivity.getApplicationInfo().name = "UnitTestApp";

      ShadowBadgeCountUpdater.lastCount = 0;
      NotificationManager notificationManager = (NotificationManager)blankActivity.getSystemService(Context.NOTIFICATION_SERVICE);
      notificationManager.cancelAll();
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

   static Intent createOpenIntent(int notifId, Bundle bundle) {
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
      Assert.assertEquals("UnitTestApp", ShadowRoboNotificationManager.lastNotif.getContentTitle());
      Assert.assertEquals(1, ShadowBadgeCountUpdater.lastCount);

      // Should allow title from GCM payload.
      bundle = getBaseNotifBundle("UUID2");
      bundle.putString("title", "title123");
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);
      Assert.assertEquals("title123", ShadowRoboNotificationManager.lastNotif.getContentTitle());
      Assert.assertEquals(2, ShadowBadgeCountUpdater.lastCount);
   }

   @Test
   public void shouldProcessRestore() throws Exception {
      Bundle bundle = createInternalPayloadBundle(getBaseNotifBundle());
      bundle.putInt("android_notif_id", 0);
      bundle.putBoolean("restoring", true);

      NotificationBundleProcessor_ProcessFromGCMIntentService_NoWrap(blankActivity, bundle, null);
      Assert.assertEquals("UnitTestApp", ShadowRoboNotificationManager.lastNotif.getContentTitle());
   }

   @Test
   public void shouldHandleBasicNotifications() throws Exception {
      // Make sure the notification got posted and the content is correct.
      Bundle bundle = getBaseNotifBundle();
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);
      Assert.assertEquals(notifMessage, ShadowRoboNotificationManager.lastNotif.getContentText());
      Assert.assertEquals(1, ShadowBadgeCountUpdater.lastCount);

      // Should have 1 DB record with the correct time stamp
      SQLiteDatabase readableDb = new OneSignalDbHelper(blankActivity).getReadableDatabase();
      Cursor cursor = readableDb.query(NotificationTable.TABLE_NAME, new String[] { "created_time" }, null, null, null, null, null);
      Assert.assertEquals(1, cursor.getCount());
      // Time stamp should be set and within a small range.
      long currentTime = System.currentTimeMillis() / 1000;
      cursor.moveToFirst();
      Assert.assertTrue(cursor.getLong(0) > currentTime - 2 && cursor.getLong(0) <= currentTime);
      cursor.close();

      // Should get marked as opened.
      NotificationOpenedProcessor.processFromActivity(blankActivity, createOpenIntent(bundle));
      cursor = readableDb.query(NotificationTable.TABLE_NAME, new String[] { "opened", "android_notification_id" }, null, null, null, null, null);
      cursor.moveToFirst();
      Assert.assertEquals(1, cursor.getInt(0));
      Assert.assertEquals(0, ShadowBadgeCountUpdater.lastCount);
      int firstNotifId = cursor.getInt(1);
      cursor.close();

      // Should not display a duplicate notification, count should still be 1
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);
      cursor = readableDb.query(NotificationTable.TABLE_NAME, null, null, null, null, null, null);
      Assert.assertEquals(1, cursor.getCount());
      Assert.assertEquals(0, ShadowBadgeCountUpdater.lastCount);
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
      cursor = readableDb.query(NotificationTable.TABLE_NAME, new String[] { "android_notification_id", "created_time" }, null, null, null, null, null);

      Assert.assertEquals(1, cursor.getCount());
      Assert.assertEquals(1, ShadowBadgeCountUpdater.lastCount);

      cursor.close();
   }

   @Test
   public void shouldRestoreNotifications() throws Exception {
      NotificationRestorer.restore(blankActivity); NotificationRestorer.restored = false;

      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, getBaseNotifBundle(), null);

      NotificationRestorer.restore(blankActivity); NotificationRestorer.restored = false;
      Intent intent = Shadows.shadowOf(blankActivity).getNextStartedService();
      Assert.assertEquals(GcmIntentService.class.getName(), intent.getComponent().getClassName());

      // Go forward 1 week
      // Note: Does not effect the SQL function strftime
      ShadowSystemClock.setCurrentTimeMillis(System.currentTimeMillis() + 604801L * 1000L);

      // Restorer should not fire service since the notification is over 1 week old.
      NotificationRestorer.restore(blankActivity); NotificationRestorer.restored = false;
      Assert.assertNull(Shadows.shadowOf(blankActivity).getNextStartedService());
   }

   @Test
   public void shouldGenerate2BasicGroupNotifications() throws Exception {
      // Make sure the notification got posted and the content is correct.
      Bundle bundle = getBaseNotifBundle();
      bundle.putString("grp", "test1");
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);

      Map<Integer, PostedNotification> postedNotifs = ShadowRoboNotificationManager.notifications;
      Assert.assertEquals(2, postedNotifs.size());

      // Test summary notification
      Iterator<Map.Entry<Integer, PostedNotification>> postedNotifsIterator = postedNotifs.entrySet().iterator();
      PostedNotification postedNotification = postedNotifsIterator.next().getValue();

      Assert.assertEquals(notifMessage, postedNotification.notif.getContentText());
      Assert.assertEquals(Notification.FLAG_GROUP_SUMMARY,postedNotification.notif.getRealNotification().flags & Notification.FLAG_GROUP_SUMMARY);

      // Test Android Wear notification
      postedNotification = postedNotifsIterator.next().getValue();
      Assert.assertEquals(notifMessage, postedNotification.notif.getContentText());
      Assert.assertEquals(0, postedNotification.notif.getRealNotification().flags & Notification.FLAG_GROUP_SUMMARY);
      // Badge count should only be one as only one notification is visible in the notification area.
      Assert.assertEquals(1, ShadowBadgeCountUpdater.lastCount);


      // Should be 2 DB entries (summary and individual)
      SQLiteDatabase readableDb = new OneSignalDbHelper(blankActivity).getReadableDatabase();
      Cursor cursor = readableDb.query(NotificationTable.TABLE_NAME, null, null, null, null, null, null);
      Assert.assertEquals(2, cursor.getCount());


      // Add another notification to the group.
      ShadowRoboNotificationManager.notifications.clear();
      bundle = new Bundle();
      bundle.putString("alert", "Notif test 2");
      bundle.putString("custom", "{\"i\": \"UUID2\"}");
      bundle.putString("grp", "test1");
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);

      postedNotifs = ShadowRoboNotificationManager.notifications;
      Assert.assertEquals(2, postedNotifs.size());
      Assert.assertEquals(2, ShadowBadgeCountUpdater.lastCount);

      postedNotifsIterator = postedNotifs.entrySet().iterator();
      postedNotification = postedNotifsIterator.next().getValue();
      Assert.assertEquals("2 new messages",postedNotification.notif.getContentText());
      Assert.assertEquals(Notification.FLAG_GROUP_SUMMARY, postedNotification.notif.getRealNotification().flags & Notification.FLAG_GROUP_SUMMARY);

      // Test Android Wear notification
      postedNotification = postedNotifsIterator.next().getValue();
      Assert.assertEquals("Notif test 2", postedNotification.notif.getContentText());
      Assert.assertEquals(0, postedNotification.notif.getRealNotification().flags & Notification.FLAG_GROUP_SUMMARY);


      // Should be 3 DB entries (summary and 2 individual)
      cursor = readableDb.query(NotificationTable.TABLE_NAME, null, null, null, null, null, null);
      Assert.assertEquals(3, cursor.getCount());


      // Open summary notification
      postedNotifsIterator = postedNotifs.entrySet().iterator();
      postedNotification = postedNotifsIterator.next().getValue();
      Intent intent = createOpenIntent(postedNotification.id, bundle).putExtra("summary", "test1");
      NotificationOpenedProcessor.processFromActivity(blankActivity, intent);
      Assert.assertEquals(0, ShadowBadgeCountUpdater.lastCount);
      ShadowRoboNotificationManager.notifications.clear();

      // Send 3rd notification
      bundle = new Bundle();
      bundle.putString("alert", "Notif test 3");
      bundle.putString("custom", "{\"i\": \"UUID3\"}");
      bundle.putString("grp", "test1");
      NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);

      postedNotifsIterator = postedNotifs.entrySet().iterator();
      postedNotification = postedNotifsIterator.next().getValue();
      Assert.assertEquals("Notif test 3", postedNotification.notif.getContentText());
      Assert.assertEquals(Notification.FLAG_GROUP_SUMMARY, postedNotification.notif.getRealNotification().flags & Notification.FLAG_GROUP_SUMMARY);
      Assert.assertEquals(1, ShadowBadgeCountUpdater.lastCount);
      cursor.close();
   }



   @Test
   public void shouldSetButtonsCorrectly() throws Exception {
      Intent intentGcm = new Intent();
      intentGcm.setAction("com.google.android.c2dm.intent.RECEIVE");
      intentGcm.putExtra("message_type", "gcm");
      Bundle bundle = getBaseNotifBundle();
      bundle.putString("o", "[{\"n\": \"text1\", \"i\": \"id1\"}]");
      intentGcm.putExtras(bundle);

      GcmBroadcastReceiver gcmBroadcastReceiver = new GcmBroadcastReceiver();
      try {
         gcmBroadcastReceiver.onReceive(blankActivity, intentGcm);
      } // setResultCode throws this error due to onReceive not designed to be called manually.
      catch (java.lang.IllegalStateException e) {}

      Intent intent = Shadows.shadowOf(blankActivity).getNextStartedService();
      Assert.assertEquals("com.onesignal.GcmIntentService", intent.getComponent().getClassName());

      JSONObject jsonPayload = new JSONObject(intent.getStringExtra("json_payload"));

      Assert.assertEquals(null, jsonPayload.optString("o", null));
      JSONObject customJson = new JSONObject(jsonPayload.optString("custom"));
      JSONObject additionalData = new JSONObject((customJson.getString("a")));
      Assert.assertEquals("id1", additionalData.getJSONArray("actionButtons").getJSONObject(0).getString("id"));
   }


   @Test
   @Config(shadows = {ShadowOneSignal.class})
   public void shouldFireNotificationExtenderService() throws Exception {
      Bundle bundle = getBaseNotifBundle();

      Intent serviceIntent = new Intent();
      serviceIntent.setPackage("com.onesignal.example");
      serviceIntent.setAction("com.onesignal.NotificationExtender");
      ResolveInfo resolveInfo = new ResolveInfo();
      resolveInfo.serviceInfo = new ServiceInfo();
      resolveInfo.serviceInfo.name = "com.onesignal.example.NotificationExtenderServiceTest";
      RuntimeEnvironment.getRobolectricPackageManager().addResolveInfoForIntent(serviceIntent, resolveInfo);

      boolean ret = OneSignalPackagePrivateHelper.GcmBroadcastReceiver_processBundle(blankActivity, bundle);
      Assert.assertEquals(true, ret);

      Intent intent = Shadows.shadowOf(blankActivity).getNextStartedService();
      Assert.assertEquals("com.onesignal.NotificationExtender", intent.getAction());

      ServiceController<NotificationExtenderServiceTest> controller = Robolectric.buildService(NotificationExtenderServiceTest.class);
      NotificationExtenderServiceTest service = controller.attach().create().get();
      Intent testIntent = new Intent(RuntimeEnvironment.application, NotificationExtenderServiceTest.class);
      testIntent.putExtras(createInternalPayloadBundle(getBundleWithAllOptionsSet()));
      controller.withIntent(testIntent).startCommand(0, 0);

      OSNotificationReceivedResult notificationReceived = service.notification;
      OSNotificationPayload notificationPayload = notificationReceived.payload;
      Assert.assertEquals("Test H", notificationPayload.title);
      Assert.assertEquals("Test B", notificationPayload.body);
      Assert.assertEquals("9764eaeb-10ce-45b1-a66d-8f95938aaa51", notificationPayload.notificationId);

      Assert.assertEquals(0, notificationPayload.lockScreenVisibility);
      Assert.assertEquals("FF0000FF", notificationPayload.smallIconAccentColor);
      Assert.assertEquals("703322744261", notificationPayload.fromProjectNumber);
      Assert.assertEquals("FFFFFF00", notificationPayload.ledColor);
      Assert.assertEquals("big_picture", notificationPayload.bigPicture);
      Assert.assertEquals("large_icon", notificationPayload.largeIcon);
      Assert.assertEquals("small_icon", notificationPayload.smallIcon);
      Assert.assertEquals("test_sound", notificationPayload.sound);
      Assert.assertEquals("You test $[notif_count] MSGs!", notificationPayload.groupMessage);
      Assert.assertEquals("http://google.com", notificationPayload.launchUrl);

      Assert.assertEquals("id1", notificationPayload.actionButtons.get(0).id);
      Assert.assertEquals("button1", notificationPayload.actionButtons.get(0).text);
      Assert.assertEquals("ic_menu_share", notificationPayload.actionButtons.get(0).icon);
      Assert.assertEquals("id2", notificationPayload.actionButtons.get(1).id);
      Assert.assertEquals("button2", notificationPayload.actionButtons.get(1).text);
      Assert.assertEquals("ic_menu_send", notificationPayload.actionButtons.get(1).icon);

      Assert.assertEquals("test_image_url", notificationPayload.backgroundImageLayout.image);
      Assert.assertEquals("FF000000", notificationPayload.backgroundImageLayout.titleTextColor);
      Assert.assertEquals("FFFFFFFF", notificationPayload.backgroundImageLayout.bodyTextColor);

      JSONObject additionalData = notificationPayload.additionalData;
      Assert.assertEquals("myValue", additionalData.getString("myKey"));
      Assert.assertEquals("nValue", additionalData.getJSONObject("nested").getString("nKey"));

      Assert.assertNotSame(-1, service.notificationId);


      // Test a basic notification without anything special.
      testIntent = new Intent(RuntimeEnvironment.application, NotificationExtenderServiceTest.class);
      testIntent.putExtras(createInternalPayloadBundle(getBaseNotifBundle()));
      controller.withIntent(testIntent).startCommand(0, 0);
      Assert.assertFalse(ShadowOneSignal.messages.contains("Error assigning"));


      // Test that a notification is still displayed if the developer's code in onNotificationProcessing throws an Exception.
      NotificationExtenderServiceTest.throwInAppCode = true;
      testIntent = new Intent(RuntimeEnvironment.application, NotificationExtenderServiceTest.class);
      testIntent.putExtras(createInternalPayloadBundle(getBaseNotifBundle("NewUUID1")));
      controller.withIntent(testIntent).startCommand(0, 0);

      Assert.assertTrue(ShadowOneSignal.messages.contains("onNotificationProcessing throw an exception"));
      Map<Integer, PostedNotification> postedNotifs = ShadowRoboNotificationManager.notifications;
      Assert.assertEquals(3, postedNotifs.size());
   }

   private static Bundle getBundleWithAllOptionsSet() {
      Bundle bundle = new Bundle();

      bundle.putString("title", "Test H");
      bundle.putString("alert", "Test B");
      bundle.putString("bgn", "1");
      bundle.putString("vis", "0");
      bundle.putString("bgac", "FF0000FF");
      bundle.putString("from", "703322744261");
      bundle.putString("ledc", "FFFFFF00");
      bundle.putString("bicon", "big_picture");
      bundle.putString("licon", "large_icon");
      bundle.putString("sicon", "small_icon");
      bundle.putString("sound", "test_sound");
      bundle.putString("grp_msg", "You test $[notif_count] MSGs!");
      bundle.putString("collapse_key", "do_not_collapse");
      bundle.putString("bg_img", "{\"img\": \"test_image_url\"," +
                                  "\"tc\": \"FF000000\"," +
                                  "\"bc\": \"FFFFFFFF\"}");


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


   public static class NotificationExtenderServiceTest extends NotificationExtenderService {
      public OSNotificationReceivedResult notification;
      public int notificationId = -1;
      public static boolean throwInAppCode;

      // Override onStart to manually call onHandleIntent on the main thread.
      @Override
      public void onStart(Intent intent, int startId) {
         onHandleIntent(intent);
         stopSelf(startId);
      }

      @Override
      protected boolean onNotificationProcessing(OSNotificationReceivedResult notification) {
         if (throwInAppCode)
            throw new NullPointerException();

         this.notification = notification;
         notificationId = displayNotification(new OverrideSettings()).notificationId;

         return true;
      }
   }
}
