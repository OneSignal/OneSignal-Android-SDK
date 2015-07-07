package com.test.onesignal;

import android.app.Activity;
import android.app.Notification;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;

import com.onesignal.BuildConfig;
import com.onesignal.NotificationBundleProcessor;
import com.onesignal.NotificationOpenedProcessor;
import com.onesignal.OneSignalDbHelper;
import com.onesignal.ShadowOneSignalRestClient;
import com.onesignal.ShadowRoboNotificationManager;
import com.onesignal.ShadowRoboNotificationManager.PostedNotification;
import com.onesignal.example.BlankActivity;
import com.onesignal.OneSignalDbContract.NotificationTable;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;
import org.robolectric.shadows.ShadowSystemClock;

import java.util.List;

import static org.robolectric.Shadows.shadowOf;

@Config(packageName = "com.onesignal.example",
      constants = BuildConfig.class,
      shadows = { ShadowRoboNotificationManager.class, ShadowOneSignalRestClient.class },
      sdk = 21)
@RunWith(CustomRobolectricTestRunner.class)
public class GenerateNotificationRunner {

   private Activity blankActiviy;

   private static final String notifMessage = "Robo test message";

   @BeforeClass // Runs only once, before any tests
   public static void setUpClass() throws Exception {
      ShadowLog.stream = System.out;
   }

   @Before // Before each test
   public void beforeEachTest() throws Exception {
      // Robolectric mocks System.currentTimeMillis() to 0, we need the current real time to match our SQL records.
      ShadowSystemClock.setCurrentTimeMillis(System.currentTimeMillis());

      blankActiviy = Robolectric.buildActivity(BlankActivity.class).create().get();

      // Add our launcher Activity to the run time to simulate a real app.
      // getRobolectricPackageManager is null if run in BeforeClass for some reason.
      Intent launchIntent = new Intent(Intent.ACTION_MAIN);
      launchIntent.setPackage("com.onesignal.example");
      launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
      ResolveInfo resolveInfo = new ResolveInfo();
      resolveInfo.activityInfo = new ActivityInfo();
      resolveInfo.activityInfo.packageName = "com.onesignal.example";
      resolveInfo.activityInfo.name = "com.onesignal.example.BlankActivity";
      RuntimeEnvironment.getRobolectricPackageManager().addResolveInfoForIntent(launchIntent, resolveInfo);
   }

   private Bundle getBaseNotifBundle() {
      Bundle bundle = new Bundle();
      bundle.putString("alert", notifMessage);
      bundle.putString("custom", "{\"i\": \"UUID\"}");

      return bundle;
   }

   private Intent createOpenIntent(int notifId, Bundle bundle) {
      return new Intent()
            .putExtra("notificationId", notifId)
            .putExtra("data", NotificationBundleProcessor.bundleAsJSONObject(bundle).toString());
   }

   private Intent createOpenIntent(Bundle bundle) {
      return createOpenIntent(ShadowRoboNotificationManager.lastNotifId, bundle);
   }

   @Test
   public void shouldHandleBasicNotifications() throws Exception {
      System.out.println("" + System.currentTimeMillis());
      // Make sure the notification got posted and the content is correct.
      Bundle bundle = getBaseNotifBundle();
      NotificationBundleProcessor.Process(blankActiviy, bundle);
      Assert.assertEquals(notifMessage, ShadowRoboNotificationManager.lastNotif.getContentText());

      // Should have 1 DB record with the correct time stamp
      SQLiteDatabase readableDb = new OneSignalDbHelper(blankActiviy).getReadableDatabase();
      Cursor cursor = readableDb.query(NotificationTable.TABLE_NAME, new String[] { "created_time" }, null, null, null, null, null);
      Assert.assertEquals(1, cursor.getCount());
      // Time stamp should be set and within a small range.
      long currentTime = System.currentTimeMillis() / 1000;
      cursor.moveToFirst();
      Assert.assertTrue(cursor.getLong(0) > currentTime - 2 && cursor.getLong(0) <= currentTime);

      // Should get marked as opened.
      NotificationOpenedProcessor.processFromActivity(blankActiviy, createOpenIntent(bundle));
      cursor = readableDb.query(NotificationTable.TABLE_NAME, new String[] { "opened", "android_notification_id" }, null, null, null, null, null);
      cursor.moveToFirst();
      Assert.assertEquals(1, cursor.getInt(0));
      int firstNotifId = cursor.getInt(1);

      // Should not display a duplicate notification, count should still be 1
      NotificationBundleProcessor.Process(blankActiviy, bundle);
      cursor = readableDb.query(NotificationTable.TABLE_NAME, null, null, null, null, null, null);
      Assert.assertEquals(1, cursor.getCount());

      // Display a second notification
      bundle = new Bundle();
      bundle.putString("alert", notifMessage);
      bundle.putString("custom", "{\"i\": \"UUID2\"}");
      NotificationBundleProcessor.Process(blankActiviy, bundle);
      cursor = readableDb.query(NotificationTable.TABLE_NAME, new String[] { "android_notification_id" }, "android_notification_id <> " + firstNotifId, null, null, null, null);
      cursor.moveToFirst();
      int secondNotifId = cursor.getInt(0);

      // Go forward 1 week.
      ShadowSystemClock.setCurrentTimeMillis(System.currentTimeMillis() + 604801 * 1000);

      // Display a 3rd notification
      // Should of been added for a total of 2 records now.
      // First opened should of been cleaned up, 1 week old non opened notification should stay, and one new record.
      bundle = new Bundle();
      bundle.putString("alert", notifMessage);
      bundle.putString("custom", "{\"i\": \"UUID3\"}");
      NotificationBundleProcessor.Process(blankActiviy, bundle);
      cursor = readableDb.query(NotificationTable.TABLE_NAME, new String[] { "android_notification_id" }, null, null, null, null, null);
      Assert.assertEquals(2, cursor.getCount());

      cursor.moveToFirst();
      boolean foundSecond = true;
      do {
         Assert.assertTrue(cursor.getInt(0) != firstNotifId);
         if (cursor.getInt(0) == secondNotifId)
            foundSecond = true;
      } while (cursor.moveToNext());

      Assert.assertTrue(foundSecond);
   }

   @Test
   public void shouldGenerate2BasicGroupNotifications() throws Exception {
      // Make sure the notification got posted and the content is correct.
      Bundle bundle = getBaseNotifBundle();
      bundle.putString("grp", "test1");
      NotificationBundleProcessor.Process(blankActiviy, bundle);

      List<PostedNotification> postedNotifs = ShadowRoboNotificationManager.notifications;
      Assert.assertEquals(2, postedNotifs.size());

      // Test summary notification
      Assert.assertEquals(notifMessage, postedNotifs.get(0).notif.getContentText());
      Assert.assertEquals(Notification.FLAG_GROUP_SUMMARY, postedNotifs.get(0).notif.getRealNotification().flags & Notification.FLAG_GROUP_SUMMARY);

      // Test Android Wear notification
      Assert.assertEquals(notifMessage, postedNotifs.get(1).notif.getContentText());
      Assert.assertEquals(0, postedNotifs.get(1).notif.getRealNotification().flags & Notification.FLAG_GROUP_SUMMARY);


      // Should be 2 DB entries (summary and individual)
      SQLiteDatabase readableDb = new OneSignalDbHelper(blankActiviy).getReadableDatabase();
      Cursor cursor = readableDb.query(NotificationTable.TABLE_NAME, null, null, null, null, null, null);
      Assert.assertEquals(2, cursor.getCount());


      // Add another notification to the group.
      ShadowRoboNotificationManager.notifications.clear();
      bundle = new Bundle();
      bundle.putString("alert", "Notif test 2");
      bundle.putString("custom", "{\"i\": \"UUID2\"}");
      bundle.putString("grp", "test1");
      NotificationBundleProcessor.Process(blankActiviy, bundle);

      postedNotifs = ShadowRoboNotificationManager.notifications;
      Assert.assertEquals(2, postedNotifs.size());

      Assert.assertEquals("2 new messages", postedNotifs.get(0).notif.getContentText());
      Assert.assertEquals(Notification.FLAG_GROUP_SUMMARY, postedNotifs.get(0).notif.getRealNotification().flags & Notification.FLAG_GROUP_SUMMARY);

      // Test Android Wear notification
      Assert.assertEquals("Notif test 2", postedNotifs.get(1).notif.getContentText());
      Assert.assertEquals(0, postedNotifs.get(1).notif.getRealNotification().flags & Notification.FLAG_GROUP_SUMMARY);


      // Should be 3 DB entries (summary and 2 individual)
      cursor = readableDb.query(NotificationTable.TABLE_NAME, null, null, null, null, null, null);
      Assert.assertEquals(3, cursor.getCount());


      // Open summary notification
      Intent intent = createOpenIntent(postedNotifs.get(0).id, bundle).putExtra("summary", "test1");
      NotificationOpenedProcessor.processFromActivity(blankActiviy, intent);

      // Send 3rd notification
      ShadowRoboNotificationManager.notifications.clear();
      bundle = new Bundle();
      bundle.putString("alert", "Notif test 3");
      bundle.putString("custom", "{\"i\": \"UUID3\"}");
      bundle.putString("grp", "test1");
      NotificationBundleProcessor.Process(blankActiviy, bundle);

      Assert.assertEquals("Notif test 3", postedNotifs.get(0).notif.getContentText());
      Assert.assertEquals(Notification.FLAG_GROUP_SUMMARY, postedNotifs.get(0).notif.getRealNotification().flags & Notification.FLAG_GROUP_SUMMARY);
   }
}
