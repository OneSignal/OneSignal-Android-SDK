package com.onesignal;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import org.robolectric.annotation.Implements;

@Implements(OneSignalDbHelper.class)
public class ShadowOneSignalDbHelper {
   public static int DATABASE_VERSION;
   public static boolean ignoreDuplicatedFieldsOnUpgrade;

   private static OneSignalDbHelper sInstance;

   public static void restSetStaticFields() {
      ignoreDuplicatedFieldsOnUpgrade = false;
      sInstance = null;
      DATABASE_VERSION = OneSignalDbHelper.DATABASE_VERSION;
   }

   public static int getDbVersion() {
      return DATABASE_VERSION;
   }

   public void onCreate(SQLiteDatabase db) {
      db.execSQL(OneSignalDbHelper.SQL_CREATE_ENTRIES);
      for (String ind : OneSignalDbHelper.SQL_INDEX_ENTRIES) {
         db.execSQL(ind);
      }
   }

   public static synchronized OneSignalDbHelper getInstance(Context context) {
      if (sInstance == null)
         sInstance = new OneSignalDbHelper(context.getApplicationContext());
      return sInstance;
   }

   // Suppress errors related to duplicates when testing DB data migrations
   public static void safeExecSQL(SQLiteDatabase db, String sql) {
      try {
         db.execSQL(sql);
      } catch (SQLiteException e) {
         if (!ignoreDuplicatedFieldsOnUpgrade)
            throw e;
         String causeMsg = e.getCause().getMessage();
         if (!causeMsg.contains("duplicate") && !causeMsg.contains("already exists"))
            throw e;
      }
   }
}
