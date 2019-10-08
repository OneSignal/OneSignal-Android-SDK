package com.test.onesignal;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.onesignal.BuildConfig;
import com.onesignal.OneSignalPackagePrivateHelper.OSSessionManager;
import com.onesignal.OneSignalDbHelper;
import com.onesignal.OneSignalPackagePrivateHelper.NotificationTable;
import com.onesignal.OneSignalPackagePrivateHelper.OutcomeEventsTable;
import com.onesignal.OutcomeEvent;
import com.onesignal.OneSignalDbHelper;
import com.onesignal.OneSignalPackagePrivateHelper.NotificationTable;
import com.onesignal.ShadowOneSignalDbHelper;
import com.onesignal.StaticResetHelper;

import org.json.JSONArray;
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

import static com.test.onesignal.TestHelpers.getAllNotificationRecords;
import static com.test.onesignal.TestHelpers.getAllOutcomesRecords;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;

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

   @Before // Before each test
   public void beforeEachTest() throws Exception {
      TestHelpers.beforeTestInitAndCleanup();
   }

   @AfterClass
   public static void afterEverything() throws Exception {
      StaticResetHelper.restSetStaticFields();
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
      ShadowOneSignalDbHelper.igngoreDuplicatedFieldsOnUpgrade = true;

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

      OutcomeEvent event = new OutcomeEvent(OSSessionManager.Session.UNATTRIBUTED, new JSONArray().put("notificationId"), "name", 0, null);
      ContentValues values = new ContentValues();
      values.put(OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_IDS, event.getNotificationIds().toString());
      values.put(OutcomeEventsTable.COLUMN_NAME_SESSION, event.getSession().toString().toLowerCase());
      values.put(OutcomeEventsTable.COLUMN_NAME_TIMESTAMP, event.getTimestamp());
      values.put(OutcomeEventsTable.COLUMN_NAME, event.getName());

      // 3. Clear the cache of the DB so it reloads the file.
      ShadowOneSignalDbHelper.restSetStaticFields();
      ShadowOneSignalDbHelper.igngoreDuplicatedFieldsOnUpgrade = true;

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
}
