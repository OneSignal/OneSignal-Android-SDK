package com.onesignal.core.internal.database.impl

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.os.SystemClock
import android.provider.BaseColumns
import com.onesignal.core.internal.database.IDatabase
import com.onesignal.core.internal.logging.Logging
import com.onesignal.core.internal.outcomes.impl.OutcomeTableProvider
import com.onesignal.core.internal.outcomes.impl.OutcomesDbContract.SQL_CREATE_OUTCOME_ENTRIES_V1
import com.onesignal.core.internal.outcomes.impl.OutcomesDbContract.SQL_CREATE_OUTCOME_ENTRIES_V3
import com.onesignal.core.internal.outcomes.impl.OutcomesDbContract.SQL_CREATE_UNIQUE_OUTCOME_ENTRIES_V1
import com.onesignal.core.internal.outcomes.impl.OutcomesDbContract.SQL_CREATE_UNIQUE_OUTCOME_ENTRIES_V2
import com.onesignal.notification.internal.common.NotificationConstants
import java.lang.IllegalStateException
import java.util.ArrayList

internal class OSDatabase(
    private val _outcomeTableProvider: OutcomeTableProvider,
    context: Context?
) : SQLiteOpenHelper(context, DATABASE_NAME, null, dbVersion), IDatabase {

    /**
     * Should be used in the event that we don't want to retry getting the a [SQLiteDatabase] instance
     * Replaced all [SQLiteOpenHelper.getReadableDatabase] with [SQLiteOpenHelper.getWritableDatabase]
     * as the internals call the same method and not much of a performance benefit between them
     * <br></br><br></br>
     * [OSDatabase.getSQLiteDatabaseWithRetries] has similar logic and throws the same Exceptions
     * <br></br><br></br>
     * @see [StackOverflow | What are best practices for SQLite on Android](https://stackoverflow.com/questions/2493331/what-are-the-best-practices-for-sqlite-on-android/3689883.3689883)
     */
    private fun getSQLiteDatabase(): SQLiteDatabase {
        synchronized(LOCK) {
            return try {
                writableDatabase
            } catch (e: SQLiteCantOpenDatabaseException) {
                // SQLiteCantOpenDatabaseException
                // Retry in-case of rare device issues with opening database.
                // https://github.com/OneSignal/OneSignal-Android-SDK/issues/136
                // SQLiteDatabaseLockedException
                // Retry in-case of rare device issues with locked database.
                // https://github.com/OneSignal/OneSignal-Android-SDK/issues/988
                throw e
            } catch (e: SQLiteDatabaseLockedException) {
                throw e
            }
        }
    }

    /**
     * Retry backoff logic based attempt to call [SQLiteOpenHelper.getWritableDatabase] until too many attempts or
     * until [SQLiteCantOpenDatabaseException] or [SQLiteDatabaseLockedException] aren't thrown
     * <br></br><br></br>
     * @see OSDatabase.getSQLiteDatabase
     */
    private fun getSQLiteDatabaseWithRetries(): SQLiteDatabase {
        synchronized(LOCK) {
            var count = 0
            while (true) {
                try {
                    return getSQLiteDatabase()
                } catch (e: SQLiteCantOpenDatabaseException) {
                    if (++count >= DB_OPEN_RETRY_MAX) throw e
                    SystemClock.sleep((count * DB_OPEN_RETRY_BACKOFF).toLong())
                } catch (e: SQLiteDatabaseLockedException) {
                    if (++count >= DB_OPEN_RETRY_MAX) throw e
                    SystemClock.sleep((count * DB_OPEN_RETRY_BACKOFF).toLong())
                }
            }
        }
    }

    override fun query(
        table: String,
        columns: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        groupBy: String?,
        having: String?,
        orderBy: String?
    ): Cursor {
        synchronized(LOCK) {
            return getSQLiteDatabaseWithRetries().query(
                table,
                columns,
                selection,
                selectionArgs,
                groupBy,
                having,
                orderBy
            )
        }
    }

    override fun query(
        table: String,
        columns: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        groupBy: String?,
        having: String?,
        orderBy: String?,
        limit: String?
    ): Cursor {
        synchronized(LOCK) {
            return getSQLiteDatabaseWithRetries().query(
                table,
                columns,
                selection,
                selectionArgs,
                groupBy,
                having,
                orderBy,
                limit
            )
        }
    }

    override fun insert(table: String, nullColumnHack: String?, values: ContentValues?) {
        synchronized(LOCK) {
            val writableDb = getSQLiteDatabaseWithRetries()
            try {
                writableDb.beginTransaction()
                writableDb.insert(table, nullColumnHack, values)
                writableDb.setTransactionSuccessful()
            } catch (e: SQLiteException) {
                Logging.error(
                    "Error inserting on table: $table with nullColumnHack: $nullColumnHack and values: $values",
                    e
                )
            } catch (e: IllegalStateException) {
                Logging.error(
                    "Error under inserting transaction under table: $table with nullColumnHack: $nullColumnHack and values: $values",
                    e
                )
            } finally {
                if (writableDb != null) {
                    try {
                        writableDb.endTransaction() // May throw if transaction was never opened or DB is full.
                    } catch (e: IllegalStateException) {
                        Logging.error("Error closing transaction! ", e)
                    } catch (e: SQLiteException) {
                        Logging.error("Error closing transaction! ", e)
                    }
                }
            }
        }
    }

    @Throws(SQLException::class)
    override fun insertOrThrow(table: String, nullColumnHack: String?, values: ContentValues?) {
        synchronized(LOCK) {
            val writableDb = getSQLiteDatabaseWithRetries()
            try {
                writableDb.beginTransaction()
                writableDb.insertOrThrow(table, nullColumnHack, values)
                writableDb.setTransactionSuccessful()
            } catch (e: SQLiteException) {
                Logging.error(
                    "Error inserting or throw on table: $table with nullColumnHack: $nullColumnHack and values: $values",
                    e
                )
            } catch (e: IllegalStateException) {
                Logging.error(
                    "Error under inserting or throw transaction under table: $table with nullColumnHack: $nullColumnHack and values: $values",
                    e
                )
            } finally {
                if (writableDb != null) {
                    try {
                        writableDb.endTransaction() // May throw if transaction was never opened or DB is full.
                    } catch (e: IllegalStateException) {
                        Logging.error("Error closing transaction! ", e)
                    } catch (e: SQLiteException) {
                        Logging.error("Error closing transaction! ", e)
                    }
                }
            }
        }
    }

    override fun update(
        table: String,
        values: ContentValues,
        whereClause: String?,
        whereArgs: Array<String>?
    ): Int {
        var result = 0
        if (values == null || values.toString().isEmpty()) return result
        synchronized(LOCK) {
            val writableDb = getSQLiteDatabaseWithRetries()
            try {
                writableDb.beginTransaction()
                result = writableDb.update(table, values, whereClause, whereArgs)
                writableDb.setTransactionSuccessful()
            } catch (e: SQLiteException) {
                Logging.error(
                    "Error updating on table: $table with whereClause: $whereClause and whereArgs: $whereArgs",
                    e
                )
            } catch (e: IllegalStateException) {
                Logging.error(
                    "Error under update transaction under table: $table with whereClause: $whereClause and whereArgs: $whereArgs",
                    e
                )
            } finally {
                if (writableDb != null) {
                    try {
                        writableDb.endTransaction() // May throw if transaction was never opened or DB is full.
                    } catch (e: IllegalStateException) {
                        Logging.error("Error closing transaction! ", e)
                    } catch (e: SQLiteException) {
                        Logging.error("Error closing transaction! ", e)
                    }
                }
            }
        }
        return result
    }

    override fun delete(table: String, whereClause: String?, whereArgs: Array<String>?) {
        synchronized(LOCK) {
            val writableDb = getSQLiteDatabaseWithRetries()
            try {
                writableDb.beginTransaction()
                writableDb.delete(table, whereClause, whereArgs)
                writableDb.setTransactionSuccessful()
            } catch (e: SQLiteException) {
                Logging.error(
                    "Error deleting on table: $table with whereClause: $whereClause and whereArgs: $whereArgs",
                    e
                )
            } catch (e: IllegalStateException) {
                Logging.error(
                    "Error under delete transaction under table: $table with whereClause: $whereClause and whereArgs: $whereArgs",
                    e
                )
            } finally {
                if (writableDb != null) {
                    try {
                        writableDb.endTransaction() // May throw if transaction was never opened or DB is full.
                    } catch (e: IllegalStateException) {
                        Logging.error("Error closing transaction! ", e)
                    } catch (e: SQLiteException) {
                        Logging.error("Error closing transaction! ", e)
                    }
                }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_ENTRIES)
        db.execSQL(SQL_CREATE_OUTCOME_ENTRIES_V3)
        db.execSQL(SQL_CREATE_UNIQUE_OUTCOME_ENTRIES_V2)
        db.execSQL(SQL_CREATE_IN_APP_MESSAGE_ENTRIES)
        for (ind in SQL_INDEX_ENTRIES) {
            db.execSQL(ind)
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Logging.debug("OneSignal Database onUpgrade from: $oldVersion to: $newVersion")

        try {
            internalOnUpgrade(db, oldVersion)
        } catch (e: SQLiteException) {
            // This could throw if rolling back then forward again.
            //   However this shouldn't happen as we clearing the database on onDowngrade
            Logging.error("Error in upgrade, migration may have already run! Skipping!", e)
        }
    }

    @Synchronized
    private fun internalOnUpgrade(db: SQLiteDatabase, oldVersion: Int) {
        if (oldVersion < 2) upgradeToV2(db)
        if (oldVersion < 3) upgradeToV3(db)
        if (oldVersion < 4) upgradeToV4(db)
        if (oldVersion < 5) upgradeToV5(db)

        // Specifically running only when going from 5 to 6+ is intentional
        if (oldVersion == 5) upgradeFromV5ToV6(db)
        if (oldVersion < 7) upgradeToV7(db)
        if (oldVersion < 8) upgradeToV8(db)
    }

    // Add collapse_id field and index
    private fun upgradeToV2(db: SQLiteDatabase) {
        safeExecSQL(
            db,
            "ALTER TABLE " + OneSignalDbContract.NotificationTable.TABLE_NAME.toString() + " " +
                "ADD COLUMN " + OneSignalDbContract.NotificationTable.COLUMN_NAME_COLLAPSE_ID.toString() + TEXT_TYPE + ";"
        )
        safeExecSQL(db, OneSignalDbContract.NotificationTable.INDEX_CREATE_GROUP_ID)
    }

    // Add expire_time field and index.
    // Also backfills expire_time to create_time + 72 hours
    private fun upgradeToV3(db: SQLiteDatabase) {
        safeExecSQL(
            db,
            "ALTER TABLE " + OneSignalDbContract.NotificationTable.TABLE_NAME.toString() + " " +
                "ADD COLUMN " + OneSignalDbContract.NotificationTable.COLUMN_NAME_EXPIRE_TIME.toString() + " TIMESTAMP" + ";"
        )
        safeExecSQL(
            db,
            "UPDATE " + OneSignalDbContract.NotificationTable.TABLE_NAME.toString() + " " +
                "SET " + OneSignalDbContract.NotificationTable.COLUMN_NAME_EXPIRE_TIME.toString() + " = " +
                OneSignalDbContract.NotificationTable.COLUMN_NAME_CREATED_TIME.toString() + " + " + NotificationConstants.DEFAULT_TTL_IF_NOT_IN_PAYLOAD.toString() + ";"
        )
        safeExecSQL(db, OneSignalDbContract.NotificationTable.INDEX_CREATE_EXPIRE_TIME)
    }

    private fun upgradeToV4(db: SQLiteDatabase) {
        safeExecSQL(db, SQL_CREATE_OUTCOME_ENTRIES_V1)
    }

    private fun upgradeToV5(db: SQLiteDatabase) {
        // Added for 3.12.1
        safeExecSQL(db, SQL_CREATE_UNIQUE_OUTCOME_ENTRIES_V1)
        // Added for 3.12.2
        upgradeFromV5ToV6(db)
    }

    // We only want to run this if going from DB v5 to v6 specifically since
    // it was originally missed in upgradeToV5 in 3.12.1
    // Added for 3.12.2
    private fun upgradeFromV5ToV6(db: SQLiteDatabase) {
        _outcomeTableProvider.upgradeOutcomeTableRevision1To2(db)
    }

    private fun upgradeToV7(db: SQLiteDatabase) {
        safeExecSQL(db, SQL_CREATE_IN_APP_MESSAGE_ENTRIES)
    }

    private fun safeExecSQL(db: SQLiteDatabase, sql: String) {
        try {
            db.execSQL(sql)
        } catch (e: SQLiteException) {
            e.printStackTrace()
        }
    }

    @Synchronized
    private fun upgradeToV8(db: SQLiteDatabase) {
        _outcomeTableProvider.upgradeOutcomeTableRevision2To3(db)
        _outcomeTableProvider.upgradeCacheOutcomeTableRevision1To2(db)
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Logging.warn("SDK version rolled back! Clearing $DATABASE_NAME as it could be in an unexpected state.")

        val cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null)
        try {
            val tables: MutableList<String> = ArrayList(cursor.count)
            while (cursor.moveToNext()) tables.add(cursor.getString(0))
            for (table in tables) {
                if (table.startsWith("sqlite_")) continue
                db.execSQL("DROP TABLE IF EXISTS $table")
            }
        } finally {
            cursor.close()
        }
        onCreate(db)
    }

    companion object {
        private const val dbVersion = 8
        private val LOCK = Any()
        private const val DATABASE_NAME = "OneSignal.db"
        private const val INTEGER_PRIMARY_KEY_TYPE = " INTEGER PRIMARY KEY"
        private const val TEXT_TYPE = " TEXT"
        private const val INT_TYPE = " INTEGER"
        private const val FLOAT_TYPE = " FLOAT"
        private const val TIMESTAMP_TYPE = " TIMESTAMP"
        private const val COMMA_SEP = ","
        private const val DB_OPEN_RETRY_MAX = 5
        private const val DB_OPEN_RETRY_BACKOFF = 400
        private const val SQL_CREATE_ENTRIES =
            "CREATE TABLE " + OneSignalDbContract.NotificationTable.TABLE_NAME + " (" +
                BaseColumns._ID + INTEGER_PRIMARY_KEY_TYPE + COMMA_SEP +
                OneSignalDbContract.NotificationTable.COLUMN_NAME_NOTIFICATION_ID + TEXT_TYPE + COMMA_SEP +
                OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID + INT_TYPE + COMMA_SEP +
                OneSignalDbContract.NotificationTable.COLUMN_NAME_GROUP_ID + TEXT_TYPE + COMMA_SEP +
                OneSignalDbContract.NotificationTable.COLUMN_NAME_COLLAPSE_ID + TEXT_TYPE + COMMA_SEP +
                OneSignalDbContract.NotificationTable.COLUMN_NAME_IS_SUMMARY + INT_TYPE + " DEFAULT 0" + COMMA_SEP +
                OneSignalDbContract.NotificationTable.COLUMN_NAME_OPENED + INT_TYPE + " DEFAULT 0" + COMMA_SEP +
                OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED + INT_TYPE + " DEFAULT 0" + COMMA_SEP +
                OneSignalDbContract.NotificationTable.COLUMN_NAME_TITLE + TEXT_TYPE + COMMA_SEP +
                OneSignalDbContract.NotificationTable.COLUMN_NAME_MESSAGE + TEXT_TYPE + COMMA_SEP +
                OneSignalDbContract.NotificationTable.COLUMN_NAME_FULL_DATA + TEXT_TYPE + COMMA_SEP +
                OneSignalDbContract.NotificationTable.COLUMN_NAME_CREATED_TIME + TIMESTAMP_TYPE + " DEFAULT (strftime('%s', 'now'))" + COMMA_SEP +
                OneSignalDbContract.NotificationTable.COLUMN_NAME_EXPIRE_TIME + TIMESTAMP_TYPE +
                ");"
        private const val SQL_CREATE_IN_APP_MESSAGE_ENTRIES =
            "CREATE TABLE " + OneSignalDbContract.InAppMessageTable.TABLE_NAME + " (" +
                BaseColumns._ID + INTEGER_PRIMARY_KEY_TYPE + COMMA_SEP +
                OneSignalDbContract.InAppMessageTable.COLUMN_NAME_DISPLAY_QUANTITY + INT_TYPE + COMMA_SEP +
                OneSignalDbContract.InAppMessageTable.COLUMN_NAME_LAST_DISPLAY + INT_TYPE + COMMA_SEP +
                OneSignalDbContract.InAppMessageTable.COLUMN_NAME_MESSAGE_ID + TEXT_TYPE + COMMA_SEP +
                OneSignalDbContract.InAppMessageTable.COLUMN_DISPLAYED_IN_SESSION + INT_TYPE + COMMA_SEP +
                OneSignalDbContract.InAppMessageTable.COLUMN_CLICK_IDS + TEXT_TYPE +
                ");"
        private val SQL_INDEX_ENTRIES = arrayOf(
            OneSignalDbContract.NotificationTable.INDEX_CREATE_NOTIFICATION_ID,
            OneSignalDbContract.NotificationTable.INDEX_CREATE_ANDROID_NOTIFICATION_ID,
            OneSignalDbContract.NotificationTable.INDEX_CREATE_GROUP_ID,
            OneSignalDbContract.NotificationTable.INDEX_CREATE_COLLAPSE_ID,
            OneSignalDbContract.NotificationTable.INDEX_CREATE_CREATED_TIME,
            OneSignalDbContract.NotificationTable.INDEX_CREATE_EXPIRE_TIME
        )
    }
}
