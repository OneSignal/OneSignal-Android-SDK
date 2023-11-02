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
    private val textType = " TEXT"
    private val intType = " INTEGER"
    private val commaSep = ","

    private val sqlCreateEntries =
        "CREATE TABLE " + OneSignalDbContract.NotificationTable.TABLE_NAME.toString() + " (" +
            BaseColumns._ID.toString() + " INTEGER PRIMARY KEY," +
            OneSignalDbContract.NotificationTable.COLUMN_NAME_NOTIFICATION_ID + textType + commaSep +
            OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID + intType + commaSep +
            OneSignalDbContract.NotificationTable.COLUMN_NAME_GROUP_ID + textType + commaSep +
            OneSignalDbContract.NotificationTable.COLUMN_NAME_IS_SUMMARY + intType.toString() + " DEFAULT 0" + commaSep +
            OneSignalDbContract.NotificationTable.COLUMN_NAME_OPENED + intType.toString() + " DEFAULT 0" + commaSep +
            OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED + intType.toString() + " DEFAULT 0" + commaSep +
            OneSignalDbContract.NotificationTable.COLUMN_NAME_TITLE + textType + commaSep +
            OneSignalDbContract.NotificationTable.COLUMN_NAME_MESSAGE + textType + commaSep +
            OneSignalDbContract.NotificationTable.COLUMN_NAME_FULL_DATA + textType + commaSep +
            OneSignalDbContract.NotificationTable.COLUMN_NAME_CREATED_TIME.toString() + " TIMESTAMP DEFAULT (strftime('%s', 'now'))" +
            ");"

    private val sqlIndexEntries: String =
        OneSignalDbContract.NotificationTable.INDEX_CREATE_NOTIFICATION_ID +
            OneSignalDbContract.NotificationTable.INDEX_CREATE_ANDROID_NOTIFICATION_ID +
            OneSignalDbContract.NotificationTable.INDEX_CREATE_GROUP_ID +
            OneSignalDbContract.NotificationTable.INDEX_CREATE_CREATED_TIME

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(sqlCreateEntries)
        db.execSQL(sqlIndexEntries)
    }

    override fun onUpgrade(
        p0: SQLiteDatabase?,
        p1: Int,
        p2: Int,
    ) {
    }
}
