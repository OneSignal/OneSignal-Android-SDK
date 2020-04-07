package com.onesignal.outcomes;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import com.onesignal.influence.model.OSInfluenceChannel;
import com.onesignal.outcomes.OSOutcomesDbContract.CachedUniqueOutcomeTable;
import com.onesignal.outcomes.OSOutcomesDbContract.OutcomeEventsTable;

public class OSOutcomeTableProvider {

    private static final String INTEGER_PRIMARY_KEY_TYPE = " INTEGER PRIMARY KEY";
    private static final String TEXT_TYPE = " TEXT";
    private static final String INT_TYPE = " INTEGER";
    private static final String FLOAT_TYPE = " FLOAT";
    private static final String TIMESTAMP_TYPE = " TIMESTAMP";

    public static final String OUTCOME_EVENT_TABLE = OutcomeEventsTable.TABLE_NAME;
    public static final String CACHE_UNIQUE_OUTCOME_TABLE = CachedUniqueOutcomeTable.TABLE_NAME;
    public static final String CACHE_UNIQUE_OUTCOME_COLUMN_CHANNEL_INFLUENCE_ID = CachedUniqueOutcomeTable.COLUMN_CHANNEL_INFLUENCE_ID;
    public static final String CACHE_UNIQUE_OUTCOME_COLUMN_CHANNEL_TYPE = CachedUniqueOutcomeTable.COLUMN_CHANNEL_TYPE;

    private static final String SQL_CREATE_OUTCOME_ENTRIES =
            "CREATE TABLE " + OutcomeEventsTable.TABLE_NAME + " (" +
                    OutcomeEventsTable._ID + INTEGER_PRIMARY_KEY_TYPE + "," +
                    OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_INFLUENCE_TYPE + TEXT_TYPE + "," +
                    OutcomeEventsTable.COLUMN_NAME_IAM_INFLUENCE_TYPE + TEXT_TYPE + "," +
                    OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_IDS + TEXT_TYPE + "," +
                    OutcomeEventsTable.COLUMN_NAME_IAM_IDS + TEXT_TYPE + "," +
                    OutcomeEventsTable.COLUMN_NAME_NAME + TEXT_TYPE + "," +
                    OutcomeEventsTable.COLUMN_NAME_TIMESTAMP + TIMESTAMP_TYPE + "," +
                    // "params TEXT" Added in v4, removed in v5.
                    OutcomeEventsTable.COLUMN_NAME_WEIGHT + FLOAT_TYPE + // New in v5, missing migration added in v6
                    ");";

    private static final String SQL_CREATE_UNIQUE_OUTCOME_ENTRIES =
            "CREATE TABLE " + CachedUniqueOutcomeTable.TABLE_NAME + " (" +
                    CachedUniqueOutcomeTable._ID + INTEGER_PRIMARY_KEY_TYPE + "," +
                    CachedUniqueOutcomeTable.COLUMN_CHANNEL_INFLUENCE_ID + TEXT_TYPE + "," +
                    CachedUniqueOutcomeTable.COLUMN_CHANNEL_TYPE + TEXT_TYPE + "," +
                    CachedUniqueOutcomeTable.COLUMN_NAME_NAME + TEXT_TYPE +
                    ");";

    /**
     * On the outcome table rename session column to notification influence type
     * Add columns for iam ids and iam influence type
     *
     * @param db
     */
    public void upgradeOutcomeTableRevision2To3(SQLiteDatabase db) {
        String commonColumns = OutcomeEventsTable._ID + "," +
                OutcomeEventsTable.COLUMN_NAME_NAME + "," +
                OutcomeEventsTable.COLUMN_NAME_TIMESTAMP + "," +
                OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_IDS + "," +
                OutcomeEventsTable.COLUMN_NAME_WEIGHT;
        String commonColumnsWithSessionColumn = commonColumns + "," + OutcomeEventsTable.COLUMN_NAME_SESSION;
        String commonColumnsWithNewSessionColumn = commonColumns + "," + OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_INFLUENCE_TYPE;

        String auxOutcomeTableName = OutcomeEventsTable.TABLE_NAME + "_aux";
        try {
            // Since SQLite does not support dropping a column we need to:
            // See https://www.techonthenet.com/sqlite/tables/alter_table.php
            //   1. Alter current table
            //   2. Create new table
            //   3. Copy data to new table
            //   4. Drop altered table
            db.execSQL("BEGIN TRANSACTION;");
            db.execSQL("ALTER TABLE " + OutcomeEventsTable.TABLE_NAME + " RENAME TO " + auxOutcomeTableName + ";");
            db.execSQL(getSqlCreateOutcomeEntries());
            db.execSQL("INSERT INTO " + OutcomeEventsTable.TABLE_NAME + "(" + commonColumnsWithNewSessionColumn + ")" +
                    " SELECT " + commonColumnsWithSessionColumn + " FROM " + auxOutcomeTableName + ";");
            db.execSQL("DROP TABLE " + auxOutcomeTableName + ";");
            db.execSQL("COMMIT;");
        } catch (SQLiteException e) {
            e.printStackTrace();
        }
    }

    /**
     * On the cache unique outcome table rename table, rename column notification id to influence id
     * Add column channel type
     *
     * @param db
     */
    public void upgradeCacheOutcomeTableRevision1To2(SQLiteDatabase db) {
        String commonColumns = CachedUniqueOutcomeTable._ID + "," +
                CachedUniqueOutcomeTable.COLUMN_NAME_NAME;
        String commonColumnsWithNotificationIdColumn = commonColumns + "," + CachedUniqueOutcomeTable.COLUMN_NAME_NOTIFICATION_ID;
        String commonColumnsWithNewInfluenceIdColumn = commonColumns + "," + CachedUniqueOutcomeTable.COLUMN_CHANNEL_INFLUENCE_ID;

        String oldCacheUniqueOutcomeTable = CachedUniqueOutcomeTable.OLD_TABLE_NAME;
        try {
            // Since SQLite does not support dropping a column we need to:
            // See https://www.techonthenet.com/sqlite/tables/alter_table.php
            //   1. Alter current table
            //   2. Create new table
            //   3. Copy data to new table
            //   4. Drop altered table
            db.execSQL("BEGIN TRANSACTION;");
            db.execSQL(getSqlCreateUniqueOutcomeEntries());
            db.execSQL("INSERT INTO " + CachedUniqueOutcomeTable.TABLE_NAME + "(" + commonColumnsWithNewInfluenceIdColumn + ")" +
                    " SELECT " + commonColumnsWithNotificationIdColumn + " FROM " + oldCacheUniqueOutcomeTable + ";");
            db.execSQL("UPDATE " + CachedUniqueOutcomeTable.TABLE_NAME +
                    " SET " + CachedUniqueOutcomeTable.COLUMN_CHANNEL_TYPE + " = \'" + OSInfluenceChannel.NOTIFICATION.toString() + "\';");
            db.execSQL("DROP TABLE " + oldCacheUniqueOutcomeTable + ";");
            db.execSQL("COMMIT;");
        } catch (SQLiteException e) {
            e.printStackTrace();
        }
    }

    /**
     * Testing mock purposes
     */
    public String getSqlCreateOutcomeEntries() {
        return SQL_CREATE_OUTCOME_ENTRIES;
    }

    /**
     * Testing mock purposes
     */
    public String getSqlCreateUniqueOutcomeEntries() {
        return SQL_CREATE_UNIQUE_OUTCOME_ENTRIES;
    }
}