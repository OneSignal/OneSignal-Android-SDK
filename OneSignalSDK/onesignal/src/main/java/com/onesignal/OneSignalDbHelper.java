/**
 * Modified MIT License
 *
 * Copyright 2017 OneSignal
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

package com.onesignal;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteCantOpenDatabaseException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.SystemClock;

import com.onesignal.OneSignalDbContract.NotificationTable;
import com.onesignal.OneSignalDbContract.OutcomeEventsTable;
import com.onesignal.OneSignalDbContract.CachedUniqueOutcomeNotificationTable;
import com.onesignal.OneSignalDbContract.InAppMessageTable;

import java.util.ArrayList;
import java.util.List;

public class OneSignalDbHelper extends SQLiteOpenHelper {
   static final int DATABASE_VERSION = 7;
   private static final String DATABASE_NAME = "OneSignal.db";

   private static final String INTEGER_PRIMARY_KEY_TYPE = " INTEGER PRIMARY KEY";
   private static final String TEXT_TYPE = " TEXT";
   private static final String INT_TYPE = " INTEGER";
   private static final String FLOAT_TYPE = " FLOAT";
   private static final String TIMESTAMP_TYPE = " TIMESTAMP";
   private static final String COMMA_SEP = ",";

   private static final int DB_OPEN_RETRY_MAX = 5;
   private static final int DB_OPEN_RETRY_BACKOFF = 400;

   protected static final String SQL_CREATE_ENTRIES =
           "CREATE TABLE " + NotificationTable.TABLE_NAME + " (" +
                   NotificationTable._ID + INTEGER_PRIMARY_KEY_TYPE + COMMA_SEP +
                   NotificationTable.COLUMN_NAME_NOTIFICATION_ID + TEXT_TYPE + COMMA_SEP +
                   NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID + INT_TYPE + COMMA_SEP +
                   NotificationTable.COLUMN_NAME_GROUP_ID + TEXT_TYPE + COMMA_SEP +
                   NotificationTable.COLUMN_NAME_COLLAPSE_ID + TEXT_TYPE + COMMA_SEP +
                   NotificationTable.COLUMN_NAME_IS_SUMMARY + INT_TYPE + " DEFAULT 0" + COMMA_SEP +
                   NotificationTable.COLUMN_NAME_OPENED + INT_TYPE + " DEFAULT 0" + COMMA_SEP +
                   NotificationTable.COLUMN_NAME_DISMISSED + INT_TYPE + " DEFAULT 0" + COMMA_SEP +
                   NotificationTable.COLUMN_NAME_TITLE + TEXT_TYPE + COMMA_SEP +
                   NotificationTable.COLUMN_NAME_MESSAGE + TEXT_TYPE + COMMA_SEP +
                   NotificationTable.COLUMN_NAME_FULL_DATA + TEXT_TYPE + COMMA_SEP +
                   NotificationTable.COLUMN_NAME_CREATED_TIME + TIMESTAMP_TYPE + " DEFAULT (strftime('%s', 'now'))" + COMMA_SEP +
                   NotificationTable.COLUMN_NAME_EXPIRE_TIME + TIMESTAMP_TYPE +
                   ");";

   private static final String SQL_CREATE_OUTCOME_ENTRIES =
           "CREATE TABLE " + OutcomeEventsTable.TABLE_NAME + " (" +
                   OutcomeEventsTable._ID + INTEGER_PRIMARY_KEY_TYPE + COMMA_SEP +
                   OutcomeEventsTable.COLUMN_NAME_SESSION + TEXT_TYPE + COMMA_SEP +
                   OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_IDS + TEXT_TYPE + COMMA_SEP +
                   OutcomeEventsTable.COLUMN_NAME_NAME + TEXT_TYPE + COMMA_SEP +
                   OutcomeEventsTable.COLUMN_NAME_TIMESTAMP + TIMESTAMP_TYPE + COMMA_SEP +
                   // "params TEXT" Added in v4, removed in v5.
                   OutcomeEventsTable.COLUMN_NAME_WEIGHT + FLOAT_TYPE + // New in v5, missing migration added in v6
                   ");";

   private static final String SQL_CREATE_UNIQUE_OUTCOME_NOTIFICATION_ENTRIES =
           "CREATE TABLE " + CachedUniqueOutcomeNotificationTable.TABLE_NAME + " (" +
                   CachedUniqueOutcomeNotificationTable._ID + INTEGER_PRIMARY_KEY_TYPE + COMMA_SEP +
                   CachedUniqueOutcomeNotificationTable.COLUMN_NAME_NOTIFICATION_ID + TEXT_TYPE + COMMA_SEP +
                   CachedUniqueOutcomeNotificationTable.COLUMN_NAME_NAME + TEXT_TYPE +
                   ");";

   private static final String SQL_CREATE_IN_APP_MESSAGE_ENTRIES =
           "CREATE TABLE " + InAppMessageTable.TABLE_NAME + " (" +
                   InAppMessageTable._ID + INTEGER_PRIMARY_KEY_TYPE + COMMA_SEP +
                   InAppMessageTable.COLUMN_NAME_DISPLAY_QUANTITY + INT_TYPE + COMMA_SEP +
                   InAppMessageTable.COLUMN_NAME_LAST_DISPLAY + INT_TYPE + COMMA_SEP +
                   InAppMessageTable.COLUMN_NAME_MESSAGE_ID + TEXT_TYPE + COMMA_SEP +
                   InAppMessageTable.COLUMN_DISPLAYED_IN_SESSION + INT_TYPE + COMMA_SEP +
                   InAppMessageTable.COLUMN_CLICK_IDS + TEXT_TYPE +
                   ");";

   protected static final String[] SQL_INDEX_ENTRIES = {
      NotificationTable.INDEX_CREATE_NOTIFICATION_ID,
      NotificationTable.INDEX_CREATE_ANDROID_NOTIFICATION_ID,
      NotificationTable.INDEX_CREATE_GROUP_ID,
      NotificationTable.INDEX_CREATE_COLLAPSE_ID,
      NotificationTable.INDEX_CREATE_CREATED_TIME,
      NotificationTable.INDEX_CREATE_EXPIRE_TIME
   };

   private static OneSignalDbHelper sInstance;

   private static int getDbVersion() {
      return DATABASE_VERSION;
   }

   OneSignalDbHelper(Context context) {
      super(context, DATABASE_NAME, null, getDbVersion());

   }

   public static synchronized OneSignalDbHelper getInstance(Context context) {
      if (sInstance == null)
         sInstance = new OneSignalDbHelper(context.getApplicationContext());
      return sInstance;
   }

   // Retry in-case of rare device issues with opening database.
   // https://github.com/OneSignal/OneSignal-Android-SDK/issues/136
   synchronized SQLiteDatabase getWritableDbWithRetries() {
      int count = 0;
      while(true) {
         try {
            return getWritableDatabase();
         } catch (SQLiteCantOpenDatabaseException e) {
            if (++count >= DB_OPEN_RETRY_MAX)
               throw e;
            SystemClock.sleep(count * DB_OPEN_RETRY_BACKOFF);
         }
      }
   }

   synchronized SQLiteDatabase getReadableDbWithRetries() {
      int count = 0;
      while(true) {
         try {
            return getReadableDatabase();
         } catch (SQLiteCantOpenDatabaseException e) {
            if (++count >= DB_OPEN_RETRY_MAX)
               throw e;
            SystemClock.sleep(count * DB_OPEN_RETRY_BACKOFF);
         }
      }
   }

   @Override
   public void onCreate(SQLiteDatabase db) {
      db.execSQL(SQL_CREATE_ENTRIES);
      db.execSQL(SQL_CREATE_OUTCOME_ENTRIES);
      db.execSQL(SQL_CREATE_UNIQUE_OUTCOME_NOTIFICATION_ENTRIES);
      db.execSQL(SQL_CREATE_IN_APP_MESSAGE_ENTRIES);
      for (String ind : SQL_INDEX_ENTRIES) {
         db.execSQL(ind);
      }
   }

   @Override
   public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
      try {
         internalOnUpgrade(db, oldVersion);
      } catch (SQLiteException e) {
         // This could throw if rolling back then forward again.
         //   However this shouldn't happen as we clearing the database on onDowngrade
         OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error in upgrade, migration may have already run! Skipping!" , e);
      }
   }

   private static void internalOnUpgrade(SQLiteDatabase db, int oldVersion) {
      if (oldVersion < 2)
         upgradeToV2(db);

      if (oldVersion < 3)
         upgradeToV3(db);

      if (oldVersion < 4)
          upgradeToV4(db);

      if (oldVersion < 5)
         upgradeToV5(db);

      // Specifically running only when going from 5 to 6+ is intentional
      if (oldVersion == 5)
         upgradeFromV5ToV6(db);

      if (oldVersion < 7)
         upgradeToV7(db);
   }

   // Add collapse_id field and index
   private static void upgradeToV2(SQLiteDatabase db) {
      safeExecSQL(db,
         "ALTER TABLE " + NotificationTable.TABLE_NAME + " " +
            "ADD COLUMN " + NotificationTable.COLUMN_NAME_COLLAPSE_ID + TEXT_TYPE + ";"
      );
      safeExecSQL(db, NotificationTable.INDEX_CREATE_GROUP_ID);
   }

   // Add expire_time field and index.
   // Also backfills expire_time to create_time + 72 hours
   private static void upgradeToV3(SQLiteDatabase db) {
      safeExecSQL(db,
         "ALTER TABLE " + NotificationTable.TABLE_NAME + " " +
            "ADD COLUMN " + NotificationTable.COLUMN_NAME_EXPIRE_TIME + " TIMESTAMP" + ";"
      );

      safeExecSQL(db,
         "UPDATE " + NotificationTable.TABLE_NAME + " " +
            "SET " + NotificationTable.COLUMN_NAME_EXPIRE_TIME +  " = "
                     + NotificationTable.COLUMN_NAME_CREATED_TIME + " + " + NotificationRestorer.DEFAULT_TTL_IF_NOT_IN_PAYLOAD + ";"
      );

      safeExecSQL(db, NotificationTable.INDEX_CREATE_EXPIRE_TIME);
   }

   private static void upgradeToV4(SQLiteDatabase db) {
      safeExecSQL(db, SQL_CREATE_OUTCOME_ENTRIES);
   }

   private static void upgradeToV5(SQLiteDatabase db) {
      // Added for 3.12.1
      safeExecSQL(db, SQL_CREATE_UNIQUE_OUTCOME_NOTIFICATION_ENTRIES);
      // Added for 3.12.2
      upgradeOutcomeTableRevision1To2(db);
   }

   // We only want to run this if going from DB v5 to v6 specifically since
   //   it was originally missed in upgradeToV5 in 3.12.1
   // Added for 3.12.2
   private static void upgradeFromV5ToV6(SQLiteDatabase db) {
      upgradeOutcomeTableRevision1To2(db);
   }

   private static void upgradeToV7(SQLiteDatabase db) {
      safeExecSQL(db, SQL_CREATE_IN_APP_MESSAGE_ENTRIES);
   }

   // On the outcome table this adds the new weight column and drops params column.
   private static void upgradeOutcomeTableRevision1To2(SQLiteDatabase db) {
      String commonColumns = "_id,name,session,timestamp,notification_ids";
      try {
         // Since SQLite does not support dropping a column we need to:
         //   1. Create a temptable
         //   2. Copy outcome table into it
         //   3. Drop the outcome table
         //   4. Recreate it with the correct fields
         //   5. Copy the temptable rows back into the new outcome table
         //   6. Drop the temptable.
         db.execSQL("BEGIN TRANSACTION;");
         db.execSQL("CREATE TEMPORARY TABLE outcome_backup(" + commonColumns + ");");
         db.execSQL("INSERT INTO outcome_backup SELECT " + commonColumns + " FROM outcome;");
         db.execSQL("DROP TABLE outcome;");
         db.execSQL(SQL_CREATE_OUTCOME_ENTRIES);
         // Not converting weight from param here, just set to zero.
         //   3.12.1 quickly replaced 3.12.0 so converting cache isn't critical.
         db.execSQL("INSERT INTO outcome (" + commonColumns + ", weight) SELECT " + commonColumns + ", 0 FROM outcome_backup;");
         db.execSQL("DROP TABLE outcome_backup;");
         db.execSQL("COMMIT;");
      } catch (SQLiteException e) {
         e.printStackTrace();
      }
   }

   private static void safeExecSQL(SQLiteDatabase db, String sql) {
      try {
         db.execSQL(sql);
      } catch (SQLiteException e) {
         e.printStackTrace();
      }
   }

   @Override
   public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
      OneSignal.Log(OneSignal.LOG_LEVEL.WARN, "SDK version rolled back! Clearing " + DATABASE_NAME + " as it could be in an unexpected state.");

      Cursor cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null);
      try {
         List<String> tables = new ArrayList<>(cursor.getCount());

         while (cursor.moveToNext())
            tables.add(cursor.getString(0));

         for (String table : tables) {
            if (table.startsWith("sqlite_"))
               continue;
            db.execSQL("DROP TABLE IF EXISTS " + table);
         }
      } finally {
         cursor.close();
      }

      onCreate(db);
   }

   // Could enable WAL in the future but requires Android API 11
   /*
   @Override
   public void onConfigure(SQLiteDatabase db) {
      super.onConfigure(db);
      db.enableWriteAheadLogging();
   }
   */

   static StringBuilder recentUninteractedWithNotificationsWhere() {
      long currentTimeSec = System.currentTimeMillis() / 1_000L;
      long createdAtCutoff = currentTimeSec - 604_800L; // 1 Week back

      StringBuilder where = new StringBuilder(
         NotificationTable.COLUMN_NAME_CREATED_TIME + " > " + createdAtCutoff + " AND " +
         NotificationTable.COLUMN_NAME_DISMISSED    + " = 0 AND " +
         NotificationTable.COLUMN_NAME_OPENED       + " = 0 AND " +
         NotificationTable.COLUMN_NAME_IS_SUMMARY   + " = 0"
      );

      boolean useTtl = OneSignalPrefs.getBool(OneSignalPrefs.PREFS_ONESIGNAL, OneSignalPrefs.PREFS_OS_RESTORE_TTL_FILTER,true);
      if (useTtl)
         where.append(" AND " + NotificationTable.COLUMN_NAME_EXPIRE_TIME + " > " + currentTimeSec);

      return where;
   }

   public void cleanOutcomeDatabase() {
      this.getWritableDatabase().delete(OneSignalDbContract.OutcomeEventsTable.TABLE_NAME, null, null);
   }
}
