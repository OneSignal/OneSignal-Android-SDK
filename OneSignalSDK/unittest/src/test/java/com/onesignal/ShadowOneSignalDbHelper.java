package com.onesignal;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import org.robolectric.annotation.Implements;

@Implements(OneSignalDbHelper.class)
public class ShadowOneSignalDbHelper {

   public static int DATABASE_VERSION = 3;
   public static boolean igngoreDuplicatedFieldsOnUpgrade;

   private static OneSignalDbHelper sInstance;

   public static void restSetStaticFields() {
      igngoreDuplicatedFieldsOnUpgrade = false;
      sInstance = null;
      DATABASE_VERSION = OneSignalDbHelper.DATABASE_VERSION;
   }

   public static int getDbVersion() {
      return DATABASE_VERSION;
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
         if (!igngoreDuplicatedFieldsOnUpgrade)
            throw e;
         String causeMsg = e.getCause().getMessage();
         if (!causeMsg.contains("duplicate") && !causeMsg.contains("already exists"))
            throw e;
      }
   }
}
