package com.test.onesignal;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.onesignal.InAppMessagingHelpers;
import com.onesignal.OneSignalDbHelper;
import com.onesignal.OneSignalPackagePrivateHelper;
import com.onesignal.OneSignalPackagePrivateHelper.CachedUniqueOutcomeNotification;
import com.onesignal.OneSignalPackagePrivateHelper.CachedUniqueOutcomeNotificationTable;
import com.onesignal.OneSignalPackagePrivateHelper.InAppMessageTable;
import com.onesignal.OneSignalPackagePrivateHelper.NotificationTable;
import com.onesignal.OneSignalPackagePrivateHelper.OSSessionManager;
import com.onesignal.OneSignalPackagePrivateHelper.OSTestInAppMessage;
import com.onesignal.OneSignalPackagePrivateHelper.OutcomeEventsTable;
import com.onesignal.OutcomeEvent;
import com.onesignal.ShadowOneSignalDbHelper;
import com.onesignal.StaticResetHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import java.util.HashMap;
import java.util.List;

import static com.onesignal.OneSignalPackagePrivateHelper.OSTestTrigger.OSTriggerKind;
import static com.test.onesignal.TestHelpers.getAllNotificationRecords;
import static com.test.onesignal.TestHelpers.getAllOutcomesRecords;
import static com.test.onesignal.TestHelpers.getAllUniqueOutcomeNotificationRecords;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

@Config(packageName = "com.onesignal.example",
        instrumentedPackages = { "com.onesignal" },
        shadows = {
            ShadowOneSignalDbHelper.class
        },
        sdk = 26
)

@RunWith(RobolectricTestRunner.class)
public class DatabaseRunner {
   @BeforeClass // Runs only once, before any tests
   public static void setUpClass() throws Exception {
      ShadowLog.stream = System.out;
      TestHelpers.beforeTestSuite();
      StaticResetHelper.saveStaticValues();
   }

   @Before
   public void beforeEachTest() throws Exception {
      TestHelpers.beforeTestInitAndCleanup();
   }

   @After
   public void afterEachTest() throws Exception {
      TestHelpers.afterTestCleanup();
   }

   @AfterClass
   public static void afterEverything() throws Exception {
      TestHelpers.beforeTestInitAndCleanup();
   }

   @Test
   public void shouldUpgradeDbFromV2ToV3() {
      // 1. Init DB as version 2 and add one notification record
      ShadowOneSignalDbHelper.DATABASE_VERSION = 2;
      SQLiteDatabase writableDatabase = OneSignalDbHelper.getInstance(RuntimeEnvironment.application).getWritableDatabase();
      writableDatabase.beginTransaction();
      ContentValues values = new ContentValues();
      values.put(NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID, 1);
      writableDatabase.insertOrThrow(NotificationTable.TABLE_NAME, null, values);
      writableDatabase.setTransactionSuccessful();
      writableDatabase.endTransaction();
      writableDatabase.close();

      // 2. Clear the cache of the DB so it reloads the file.
      ShadowOneSignalDbHelper.restSetStaticFields();
      ShadowOneSignalDbHelper.ignoreDuplicatedFieldsOnUpgrade = true;

      // 3. Opening the DB will auto trigger the update.
      HashMap<String, Object> notif = getAllNotificationRecords().get(0);

      long createdTime = (Long)notif.get(NotificationTable.COLUMN_NAME_CREATED_TIME);
      long expireTime = (Long)notif.get(NotificationTable.COLUMN_NAME_EXPIRE_TIME);
      assertEquals(createdTime + (72L * (60 * 60)), expireTime);
   }

   @Test
   public void shouldUpgradeDbFromV3ToV4() throws Exception {
      // 1. Init DB as version 3
      ShadowOneSignalDbHelper.DATABASE_VERSION = 3;
      SQLiteDatabase readableDatabase = OneSignalDbHelper.getInstance(RuntimeEnvironment.application).getReadableDatabase();

      Cursor cursor = readableDatabase.rawQuery("SELECT name FROM sqlite_master WHERE type ='table' AND name='" + OutcomeEventsTable.TABLE_NAME + "'", null);

      boolean exist = false;
      if (cursor != null) {
          exist = cursor.getCount() > 0;
          cursor.close();
      }
      // 2. Table must not exist
      assertFalse(exist);

      readableDatabase.close();

      OutcomeEvent event = new OutcomeEvent(OSSessionManager.Session.UNATTRIBUTED, new JSONArray().put("notificationId"), "name", 0, 0);
      ContentValues values = new ContentValues();
      values.put(OutcomeEventsTable.COLUMN_NAME_SESSION, event.getSession().toString().toLowerCase());
      values.put(OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_IDS, event.getNotificationIds().toString());
      values.put(OutcomeEventsTable.COLUMN_NAME_NAME, event.getName());
      values.put(OutcomeEventsTable.COLUMN_NAME_TIMESTAMP, event.getTimestamp());
      values.put(OutcomeEventsTable.COLUMN_NAME_NAME, event.getWeight());

      // 3. Clear the cache of the DB so it reloads the file.
      ShadowOneSignalDbHelper.restSetStaticFields();
      ShadowOneSignalDbHelper.ignoreDuplicatedFieldsOnUpgrade = true;

      // 4. Opening the DB will auto trigger the update.
      List<OutcomeEvent> events = getAllOutcomesRecords();

      assertEquals(events.size(), 0);

      SQLiteDatabase writableDatabase = OneSignalDbHelper.getInstance(RuntimeEnvironment.application).getWritableDatabase();
      // 5. Table now must exist
      writableDatabase.insert(OutcomeEventsTable.TABLE_NAME, null, values);
      writableDatabase.close();

      List<OutcomeEvent> outcomeEvents = getAllOutcomesRecords();

      assertEquals(outcomeEvents.size(), 1);
   }


   private static final String SQL_CREATE_OUTCOME_REVISION1_ENTRIES =
      "CREATE TABLE outcome (" +
         "_id INTEGER PRIMARY KEY, " +
         "session TEXT," +
         "notification_ids TEXT, " +
         "name TEXT, " +
         "timestamp TIMESTAMP, " +
         "params TEXT " +
      ")";

   @Test
   public void shouldUpgradeDbFromV4ToV5() {
      // 1. Init DB as version 4
      ShadowOneSignalDbHelper.DATABASE_VERSION = 4;
      SQLiteDatabase writableDatabase = OneSignalDbHelper.getInstance(RuntimeEnvironment.application).getWritableDatabase();
      writableDatabase.execSQL(SQL_CREATE_OUTCOME_REVISION1_ENTRIES);

      Cursor cursor = writableDatabase.rawQuery("SELECT name FROM sqlite_master WHERE type ='table' AND name='" + CachedUniqueOutcomeNotificationTable.TABLE_NAME + "'", null);

      boolean exist = false;
      if (cursor != null) {
         exist = cursor.getCount() > 0;
         cursor.close();
      }
      // 2. Table must not exist
      assertFalse(exist);

      writableDatabase.close();

      CachedUniqueOutcomeNotification notification = new CachedUniqueOutcomeNotification("notificationId", "outcome");
      ContentValues values = new ContentValues();
      values.put(CachedUniqueOutcomeNotificationTable.COLUMN_NAME_NOTIFICATION_ID, notification.getNotificationId());
      values.put(CachedUniqueOutcomeNotificationTable.COLUMN_NAME_NAME, notification.getName());

      // 3. Clear the cache of the DB so it reloads the file.
      ShadowOneSignalDbHelper.restSetStaticFields();
      ShadowOneSignalDbHelper.ignoreDuplicatedFieldsOnUpgrade = true;

      // 4. Opening the DB will auto trigger the update.
      List<CachedUniqueOutcomeNotification> notifications = getAllUniqueOutcomeNotificationRecords();

      assertEquals(notifications.size(), 0);

      writableDatabase = OneSignalDbHelper.getInstance(RuntimeEnvironment.application).getWritableDatabase();
      // 5. Table now must exist
      writableDatabase.insert(CachedUniqueOutcomeNotificationTable.TABLE_NAME, null, values);
      writableDatabase.close();

      List<CachedUniqueOutcomeNotification> uniqueOutcomeNotifications = getAllUniqueOutcomeNotificationRecords();

      assertEquals(uniqueOutcomeNotifications.size(), 1);
   }

   @Test
   public void shouldUpgradeDbFromV5ToV6() {
      // 1. Init outcome table as version 5
      ShadowOneSignalDbHelper.DATABASE_VERSION = 5;
      SQLiteDatabase writableDatabase = OneSignalDbHelper.getInstance(RuntimeEnvironment.application).getWritableDatabase();

      // Create table with the schema we had in DB v4
      writableDatabase.execSQL(SQL_CREATE_OUTCOME_REVISION1_ENTRIES);

      // Insert one outcome record so we can test migration keeps it later on
      ContentValues values = new ContentValues();
      values.put("name", "a");
      writableDatabase.insertOrThrow("outcome", null, values);
      writableDatabase.setVersion(5);
      writableDatabase.close();

      // 2. restSetStaticFields so the db reloads and upgrade is done to version 6
      ShadowOneSignalDbHelper.restSetStaticFields();
      writableDatabase = OneSignalDbHelper.getInstance(RuntimeEnvironment.application).getWritableDatabase();

      // 3. Ensure the upgrade kept our existing record
      Cursor cursor = writableDatabase.query(
         "outcome",
        null,
         null,
         null,
         null,
         null,
         null
      );
      assertTrue(cursor.moveToFirst());
      assertEquals("a", cursor.getString(cursor.getColumnIndex("name")));

      // 4. Ensure new weight column exists
      values = new ContentValues();
      values.put("weight", 1);
      long successful = writableDatabase.insert("outcome", null, values);
      assertFalse(successful == -1);

      // 5. Ensure params column does NOT exists
      values = new ContentValues();
      values.put("params", 1);
      successful = writableDatabase.insert("outcome", null, values);
      writableDatabase.close();
      assertEquals(-1, successful);
   }

   @Test
   public void shouldUpgradeDbFromV6ToV7() throws JSONException {
      // 1. Init DB as version 6
      ShadowOneSignalDbHelper.DATABASE_VERSION = 6;
      SQLiteDatabase writableDatabase = OneSignalDbHelper.getInstance(RuntimeEnvironment.application).getWritableDatabase();
      writableDatabase.execSQL(SQL_CREATE_OUTCOME_REVISION1_ENTRIES);

      Cursor cursor = writableDatabase.rawQuery("SELECT name FROM sqlite_master WHERE type ='table' AND name='" + InAppMessageTable.TABLE_NAME + "'", null);

      boolean exist = false;
      if (cursor != null) {
         exist = cursor.getCount() > 0;
         cursor.close();
      }
      // 2. Table must not exist
      assertFalse(exist);

      writableDatabase.close();

      // Create an IAM
      final OSTestInAppMessage inAppMessage = InAppMessagingHelpers.buildTestMessageWithSingleTrigger(
              OSTriggerKind.SESSION_TIME,
              null,
              OneSignalPackagePrivateHelper.OSTestTrigger.OSTriggerOperator.NOT_EXISTS.toString(),
              null);

      inAppMessage.setDisplayedInSession(true);
      
      ContentValues values = new ContentValues();
      values.put(InAppMessageTable.COLUMN_NAME_MESSAGE_ID, inAppMessage.messageId);
      values.put(InAppMessageTable.COLUMN_NAME_DISPLAY_QUANTITY, inAppMessage.getDisplayStats().getDisplayQuantity());
      values.put(InAppMessageTable.COLUMN_NAME_LAST_DISPLAY, inAppMessage.getDisplayStats().getLastDisplayTime());
      values.put(InAppMessageTable.COLUMN_CLICK_IDS, inAppMessage.getClickedClickIds().toString());
      values.put(InAppMessageTable.COLUMN_DISPLAYED_IN_SESSION, inAppMessage.isDisplayedInSession());

      // 3. Clear the cache of the DB so it reloads the file.
      ShadowOneSignalDbHelper.restSetStaticFields();
      ShadowOneSignalDbHelper.ignoreDuplicatedFieldsOnUpgrade = true;

      // 4. Opening the DB will auto trigger the update.
      List<OSTestInAppMessage> savedInAppMessages = TestHelpers.getAllInAppMessages();

      assertEquals(savedInAppMessages.size(), 0);

      writableDatabase = OneSignalDbHelper.getInstance(RuntimeEnvironment.application).getWritableDatabase();
      // 5. Table now must exist
      writableDatabase.insert(InAppMessageTable.TABLE_NAME, null, values);
      writableDatabase.close();

      List<OSTestInAppMessage> savedInAppMessagesAfterCreation = TestHelpers.getAllInAppMessages();

      assertEquals(savedInAppMessagesAfterCreation.size(), 1);
      OSTestInAppMessage savedInAppMessage = savedInAppMessagesAfterCreation.get(0);
      assertEquals(savedInAppMessage.getDisplayStats().getDisplayQuantity(), inAppMessage.getDisplayStats().getDisplayQuantity());
      assertEquals(savedInAppMessage.getDisplayStats().getLastDisplayTime(), inAppMessage.getDisplayStats().getLastDisplayTime());
      assertEquals(savedInAppMessage.getClickedClickIds().toString(), inAppMessage.getClickedClickIds().toString());
      assertEquals(savedInAppMessage.isDisplayedInSession(), inAppMessage.isDisplayedInSession());
   }
}
