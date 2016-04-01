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

package com.onesignal;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.onesignal.OneSignalDbContract.NotificationTable;

public class OneSignalDbHelper extends SQLiteOpenHelper {
   public static final int DATABASE_VERSION = 1;
   public static final String DATABASE_NAME = "OneSignal.db";

   private static final String TEXT_TYPE = " TEXT";
   private static final String INT_TYPE = " INTEGER";
   private static final String COMMA_SEP = ",";

   private static final String SQL_CREATE_ENTRIES =
       "CREATE TABLE " + NotificationTable.TABLE_NAME + " (" +
           NotificationTable._ID + " INTEGER PRIMARY KEY," +
           NotificationTable.COLUMN_NAME_NOTIFICATION_ID + TEXT_TYPE + COMMA_SEP +
           NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID + INT_TYPE + COMMA_SEP +
           NotificationTable.COLUMN_NAME_GROUP_ID + TEXT_TYPE + COMMA_SEP +
           NotificationTable.COLUMN_NAME_IS_SUMMARY + INT_TYPE + " DEFAULT 0" + COMMA_SEP +
           NotificationTable.COLUMN_NAME_OPENED + INT_TYPE + " DEFAULT 0" + COMMA_SEP +
           NotificationTable.COLUMN_NAME_DISMISSED + INT_TYPE + " DEFAULT 0" + COMMA_SEP +
           NotificationTable.COLUMN_NAME_TITLE + TEXT_TYPE + COMMA_SEP +
           NotificationTable.COLUMN_NAME_MESSAGE + TEXT_TYPE + COMMA_SEP +
           NotificationTable.COLUMN_NAME_FULL_DATA + TEXT_TYPE + COMMA_SEP +
           NotificationTable.COLUMN_NAME_CREATED_TIME + " TIMESTAMP DEFAULT (strftime('%s', 'now'))" +
           ");";

   private static final String SQL_INDEX_ENTRIES =
       NotificationTable.INDEX_CREATE_NOTIFICATION_ID +
           NotificationTable.INDEX_CREATE_ANDROID_NOTIFICATION_ID +
           NotificationTable.INDEX_CREATE_GROUP_ID +
           NotificationTable.INDEX_CREATE_CREATED_TIME;

   public OneSignalDbHelper(Context context) {
      super(context, DATABASE_NAME, null, DATABASE_VERSION);
   }
   public void onCreate(SQLiteDatabase db) {
      db.execSQL(SQL_CREATE_ENTRIES);
      db.execSQL(SQL_INDEX_ENTRIES);
   }

   @Override
   public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
      // Only 1 db version so far.
   }
}