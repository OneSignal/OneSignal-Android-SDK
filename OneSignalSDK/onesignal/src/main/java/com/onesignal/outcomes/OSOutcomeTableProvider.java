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

    public static final String SQL_CREATE_OUTCOME_ENTRIES_V1 =
            "CREATE TABLE " + OutcomeEventsTable.TABLE_NAME + " (" +
                    OutcomeEventsTable._ID + " INTEGER PRIMARY KEY," +
                    OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_IDS + TEXT_TYPE + "," +
                    OutcomeEventsTable.COLUMN_NAME_NAME + TEXT_TYPE + "," +
                    OutcomeEventsTable.COLUMN_NAME_SESSION + TEXT_TYPE + "," +
                    OutcomeEventsTable.COLUMN_NAME_PARAMS + TEXT_TYPE + "," +
                    OutcomeEventsTable.COLUMN_NAME_TIMESTAMP + " TIMESTAMP" +
                    ");";

    public static final String SQL_CREATE_OUTCOME_ENTRIES_V2 =
            "CREATE TABLE " + OutcomeEventsTable.TABLE_NAME + " (" +
                    OutcomeEventsTable._ID + INTEGER_PRIMARY_KEY_TYPE + "," +
                    OutcomeEventsTable.COLUMN_NAME_SESSION + TEXT_TYPE + "," +
                    OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_IDS + TEXT_TYPE + "," +
                    OutcomeEventsTable.COLUMN_NAME_NAME + TEXT_TYPE + "," +
                    OutcomeEventsTable.COLUMN_NAME_TIMESTAMP + TIMESTAMP_TYPE + "," +
                    // "params TEXT" Added in v4, removed in v5.
                    OutcomeEventsTable.COLUMN_NAME_WEIGHT + FLOAT_TYPE + // New in v5, missing migration added in v6
                    ");";


    public static final String SQL_CREATE_OUTCOME_ENTRIES_V3 =
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

    public static final String SQL_CREATE_UNIQUE_OUTCOME_ENTRIES_V1 =
            "CREATE TABLE " + CachedUniqueOutcomeTable.TABLE_NAME_V1 + " (" +
                    CachedUniqueOutcomeTable._ID + INTEGER_PRIMARY_KEY_TYPE + "," +
                    CachedUniqueOutcomeTable.COLUMN_NAME_NOTIFICATION_ID + TEXT_TYPE + "," +
                    CachedUniqueOutcomeTable.COLUMN_NAME_NAME + TEXT_TYPE +
                    ");";


    public static final String SQL_CREATE_UNIQUE_OUTCOME_ENTRIES_V2 =
            "CREATE TABLE " + CachedUniqueOutcomeTable.TABLE_NAME_V2 + " (" +
                    CachedUniqueOutcomeTable._ID + INTEGER_PRIMARY_KEY_TYPE + "," +
                    CachedUniqueOutcomeTable.COLUMN_CHANNEL_INFLUENCE_ID + TEXT_TYPE + "," +
                    CachedUniqueOutcomeTable.COLUMN_CHANNEL_TYPE + TEXT_TYPE + "," +
                    CachedUniqueOutcomeTable.COLUMN_NAME_NAME + TEXT_TYPE +
                    ");";

    /**
     * On the outcome table this adds the new weight column and drops params column.
     */
    public void upgradeOutcomeTableRevision1To2(SQLiteDatabase db) {
        String commonColumns = OutcomeEventsTable._ID + "," +
                OutcomeEventsTable.COLUMN_NAME_SESSION+ "," +
                OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_IDS + "," +
                OutcomeEventsTable.COLUMN_NAME_NAME + "," +
                OutcomeEventsTable.COLUMN_NAME_TIMESTAMP;
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
            db.execSQL(SQL_CREATE_OUTCOME_ENTRIES_V2);
            // Not converting weight from param here, just set to zero.
            //   3.12.1 quickly replaced 3.12.0 so converting cache isn't critical.
            db.execSQL("INSERT INTO outcome (" + commonColumns + ", weight) SELECT " + commonColumns + ", 0 FROM outcome_backup;");
            db.execSQL("DROP TABLE outcome_backup;");
        } catch (SQLiteException e) {
            e.printStackTrace();
        } finally {
            db.execSQL("COMMIT;");
        }
    }

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
            db.execSQL(SQL_CREATE_OUTCOME_ENTRIES_V3);
            db.execSQL("INSERT INTO " + OutcomeEventsTable.TABLE_NAME + "(" + commonColumnsWithNewSessionColumn + ")" +
                    " SELECT " + commonColumnsWithSessionColumn + " FROM " + auxOutcomeTableName + ";");
            db.execSQL("DROP TABLE " + auxOutcomeTableName + ";");
        } catch (SQLiteException e) {
            e.printStackTrace();
        } finally {
            db.execSQL("COMMIT;");
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

        String oldCacheUniqueOutcomeTable = CachedUniqueOutcomeTable.TABLE_NAME_V1;
        try {
            // Since SQLite does not support dropping a column we need to:
            // See https://www.techonthenet.com/sqlite/tables/alter_table.php
            //   1. Alter current table
            //   2. Create new table
            //   3. Copy data to new table
            //   4. Drop altered table
            db.execSQL("BEGIN TRANSACTION;");
            db.execSQL(SQL_CREATE_UNIQUE_OUTCOME_ENTRIES_V2);
            db.execSQL("INSERT INTO " + CachedUniqueOutcomeTable.TABLE_NAME_V2 + "(" + commonColumnsWithNewInfluenceIdColumn + ")" +
                    " SELECT " + commonColumnsWithNotificationIdColumn + " FROM " + oldCacheUniqueOutcomeTable + ";");
            db.execSQL("UPDATE " + CachedUniqueOutcomeTable.TABLE_NAME_V2 +
                    " SET " + CachedUniqueOutcomeTable.COLUMN_CHANNEL_TYPE + " = \'" + OSInfluenceChannel.NOTIFICATION.toString() + "\';");
            db.execSQL("DROP TABLE " + oldCacheUniqueOutcomeTable + ";");
        } catch (SQLiteException e) {
            e.printStackTrace();
        } finally {
            db.execSQL("COMMIT;");
        }
    }

}