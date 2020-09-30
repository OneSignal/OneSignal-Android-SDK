package com.onesignal.outcomes.data

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.onesignal.influence.domain.OSInfluenceChannel
import com.onesignal.outcomes.data.OutcomesDbContract.SQL_CREATE_OUTCOME_ENTRIES_V2
import com.onesignal.outcomes.data.OutcomesDbContract.SQL_CREATE_OUTCOME_ENTRIES_V3
import com.onesignal.outcomes.data.OutcomesDbContract.SQL_CREATE_UNIQUE_OUTCOME_ENTRIES_V2

class OSOutcomeTableProvider {
    /**
     * On the outcome table this adds the new weight column and drops params column.
     */
    fun upgradeOutcomeTableRevision1To2(db: SQLiteDatabase) {
        val commonColumns: String = OutcomeEventsTable.ID.toString() + "," +
                OutcomeEventsTable.COLUMN_NAME_SESSION + "," +
                OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_IDS + "," +
                OutcomeEventsTable.COLUMN_NAME_NAME + "," +
                OutcomeEventsTable.COLUMN_NAME_TIMESTAMP
        try {
            // Since SQLite does not support dropping a column we need to:
            //   1. Create a temptable
            //   2. Copy outcome table into it
            //   3. Drop the outcome table
            //   4. Recreate it with the correct fields
            //   5. Copy the temptable rows back into the new outcome table
            //   6. Drop the temptable.
            db.execSQL("BEGIN TRANSACTION;")
            db.execSQL("CREATE TEMPORARY TABLE outcome_backup($commonColumns);")
            db.execSQL("INSERT INTO outcome_backup SELECT $commonColumns FROM outcome;")
            db.execSQL("DROP TABLE outcome;")
            db.execSQL(SQL_CREATE_OUTCOME_ENTRIES_V2)
            // Not converting weight from param here, just set to zero.
            //   3.12.1 quickly replaced 3.12.0 so converting cache isn't critical.
            db.execSQL("INSERT INTO outcome ($commonColumns, weight) SELECT $commonColumns, 0 FROM outcome_backup;")
            db.execSQL("DROP TABLE outcome_backup;")
        } catch (e: SQLiteException) {
            e.printStackTrace()
        } finally {
            db.execSQL("COMMIT;")
        }
    }

    /**
     * On the outcome table rename session column to notification influence type
     * Add columns for iam ids and iam influence type
     *
     * @param db
     */
    fun upgradeOutcomeTableRevision2To3(db: SQLiteDatabase) {
        val commonColumns: String = OutcomeEventsTable.ID + "," +
                OutcomeEventsTable.COLUMN_NAME_NAME + "," +
                OutcomeEventsTable.COLUMN_NAME_TIMESTAMP + "," +
                OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_IDS + "," +
                OutcomeEventsTable.COLUMN_NAME_WEIGHT
        val commonColumnsWithSessionColumn = commonColumns + "," + OutcomeEventsTable.COLUMN_NAME_SESSION
        val commonColumnsWithNewSessionColumn = commonColumns + "," + OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_INFLUENCE_TYPE
        val auxOutcomeTableName: String = OutcomeEventsTable.TABLE_NAME + "_aux"
        try {
            // Since SQLite does not support dropping a column we need to:
            // See https://www.techonthenet.com/sqlite/tables/alter_table.php
            //   1. Alter current table
            //   2. Create new table
            //   3. Copy data to new table
            //   4. Drop altered table
            db.execSQL("BEGIN TRANSACTION;")
            db.execSQL("ALTER TABLE " + OutcomeEventsTable.TABLE_NAME + " RENAME TO " + auxOutcomeTableName + ";")
            db.execSQL(SQL_CREATE_OUTCOME_ENTRIES_V3)
            db.execSQL("INSERT INTO " + OutcomeEventsTable.TABLE_NAME + "(" + commonColumnsWithNewSessionColumn + ")" +
                    " SELECT " + commonColumnsWithSessionColumn + " FROM " + auxOutcomeTableName + ";")
            db.execSQL("DROP TABLE $auxOutcomeTableName;")
        } catch (e: SQLiteException) {
            e.printStackTrace()
        } finally {
            db.execSQL("COMMIT;")
        }
    }

    /**
     * On the cache unique outcome table rename table, rename column notification id to influence id
     * Add column channel type
     *
     * @param db
     */
    fun upgradeCacheOutcomeTableRevision1To2(db: SQLiteDatabase) {
        val commonColumns: String = CachedUniqueOutcomeTable.ID + "," +
                CachedUniqueOutcomeTable.COLUMN_NAME_NAME
        val commonColumnsWithNotificationIdColumn = commonColumns + "," + CachedUniqueOutcomeTable.COLUMN_NAME_NOTIFICATION_ID
        val commonColumnsWithNewInfluenceIdColumn = commonColumns + "," + CachedUniqueOutcomeTable.COLUMN_CHANNEL_INFLUENCE_ID
        val oldCacheUniqueOutcomeTable: String = CachedUniqueOutcomeTable.TABLE_NAME_V1
        try {
            // Since SQLite does not support dropping a column we need to:
            // See https://www.techonthenet.com/sqlite/tables/alter_table.php
            //   1. Alter current table
            //   2. Create new table
            //   3. Copy data to new table
            //   4. Drop altered table
            db.execSQL("BEGIN TRANSACTION;")
            db.execSQL(SQL_CREATE_UNIQUE_OUTCOME_ENTRIES_V2)
            db.execSQL("INSERT INTO " + CachedUniqueOutcomeTable.TABLE_NAME_V2 + "(" + commonColumnsWithNewInfluenceIdColumn + ")" +
                    " SELECT " + commonColumnsWithNotificationIdColumn + " FROM " + oldCacheUniqueOutcomeTable + ";")
            db.execSQL("UPDATE " + CachedUniqueOutcomeTable.TABLE_NAME_V2 +
                    " SET " + CachedUniqueOutcomeTable.COLUMN_CHANNEL_TYPE + " = \'" + OSInfluenceChannel.NOTIFICATION.toString() + "\';")
            db.execSQL("DROP TABLE $oldCacheUniqueOutcomeTable;")
        } catch (e: SQLiteException) {
            e.printStackTrace()
        } finally {
            db.execSQL("COMMIT;")
        }
    }
}