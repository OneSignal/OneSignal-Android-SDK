package com.onesignal.core.internal.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns
import com.onesignal.core.internal.database.impl.OneSignalDbContract

/**
 * This class setups up the database in it's initial form to test database upgrade paths.
 */
internal class InitialOSDatabase(context: Context?) : SQLiteOpenHelper(context, "OneSignal.db", null, 1) {
    private val TEXT_TYPE = " TEXT"
    private val INT_TYPE = " INTEGER"
    private val COMMA_SEP = ","

    private val SQL_CREATE_ENTRIES =
        "CREATE TABLE " + OneSignalDbContract.NotificationTable.TABLE_NAME.toString() + " (" +
            BaseColumns._ID.toString() + " INTEGER PRIMARY KEY," +
            OneSignalDbContract.NotificationTable.COLUMN_NAME_NOTIFICATION_ID + TEXT_TYPE + COMMA_SEP +
            OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID + INT_TYPE + COMMA_SEP +
            OneSignalDbContract.NotificationTable.COLUMN_NAME_GROUP_ID + TEXT_TYPE + COMMA_SEP +
            OneSignalDbContract.NotificationTable.COLUMN_NAME_IS_SUMMARY + INT_TYPE.toString() + " DEFAULT 0" + COMMA_SEP +
            OneSignalDbContract.NotificationTable.COLUMN_NAME_OPENED + INT_TYPE.toString() + " DEFAULT 0" + COMMA_SEP +
            OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED + INT_TYPE.toString() + " DEFAULT 0" + COMMA_SEP +
            OneSignalDbContract.NotificationTable.COLUMN_NAME_TITLE + TEXT_TYPE + COMMA_SEP +
            OneSignalDbContract.NotificationTable.COLUMN_NAME_MESSAGE + TEXT_TYPE + COMMA_SEP +
            OneSignalDbContract.NotificationTable.COLUMN_NAME_FULL_DATA + TEXT_TYPE + COMMA_SEP +
            OneSignalDbContract.NotificationTable.COLUMN_NAME_CREATED_TIME.toString() + " TIMESTAMP DEFAULT (strftime('%s', 'now'))" +
            ");"

    private val SQL_INDEX_ENTRIES: String =
        OneSignalDbContract.NotificationTable.INDEX_CREATE_NOTIFICATION_ID +
            OneSignalDbContract.NotificationTable.INDEX_CREATE_ANDROID_NOTIFICATION_ID +
            OneSignalDbContract.NotificationTable.INDEX_CREATE_GROUP_ID +
            OneSignalDbContract.NotificationTable.INDEX_CREATE_CREATED_TIME

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_ENTRIES)
        db.execSQL(SQL_INDEX_ENTRIES)
    }

    override fun onUpgrade(
        p0: SQLiteDatabase?,
        p1: Int,
        p2: Int,
    ) {
    }
}
