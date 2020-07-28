package com.onesignal.outcomes.data

import android.provider.BaseColumns

internal object OutcomeEventsTable {
    const val ID = BaseColumns._ID
    const val TABLE_NAME = "outcome" // Added on DB v4 SDK v3.12.0

    // Influence ids
    const val COLUMN_NAME_NOTIFICATION_IDS = "notification_ids" // Added on DB v4 SDK v3.12.0
    const val COLUMN_NAME_IAM_IDS = "iam_ids" // Added on DB v8 SDK v3.14.0

    // Influence type
    const val COLUMN_NAME_SESSION = "session" // Added on DB v4 SDK v3.12.0 replaced with notification_influence_type on DB v8 SDK v3.14.0
    const val COLUMN_NAME_NOTIFICATION_INFLUENCE_TYPE = "notification_influence_type" // Added on DB v8 SDK v3.14.0
    const val COLUMN_NAME_IAM_INFLUENCE_TYPE = "iam_influence_type" // Added on DB v8 SDK v3.14.0

    // Outcome data
    const val COLUMN_NAME_NAME = "name" // Added on DB v4 SDK v3.12.0
    const val COLUMN_NAME_WEIGHT = "weight" // Added on DB v5 SDK v3.12.1, migration added on DB v6 SDK v3.12.2
    const val COLUMN_NAME_TIMESTAMP = "timestamp" // Added on DB v4 SDK v3.12.0
    const val COLUMN_NAME_PARAMS = "params" // Added on DB v4 SDK v3.12.0 replaced with weight on DB v5 SDK v3.12.1, migration added on DB v6 SDK v3.12.2
}

internal object CachedUniqueOutcomeTable {
    const val ID = BaseColumns._ID
    const val TABLE_NAME_V2 = "cached_unique_outcome" // Added on DB v8 SDK v3.14.0
    const val TABLE_NAME = TABLE_NAME_V2
    const val TABLE_NAME_V1 = "cached_unique_outcome_notification" // Added on DB v5 SDK v3.12.1 until DB v8 renamed with cached_unique_outcome SDK v3.14.0
    const val COLUMN_NAME_NOTIFICATION_ID = "notification_id" // Added on DB v5 SDK v3.12.1 until DB v8 renamed with channel_influence_id SDK v3.14.0
    const val COLUMN_CHANNEL_INFLUENCE_ID = "channel_influence_id" // Added on DB v8 SDK v3.14.0
    const val COLUMN_CHANNEL_TYPE = "channel_type" // Added on DB v8 SDK v3.14.0
    const val COLUMN_NAME_NAME = "name" // Added on DB v5 SDK v3.12.1
}

internal object OutcomesDbContract {
    private const val INTEGER_PRIMARY_KEY_TYPE = " INTEGER PRIMARY KEY"
    private const val TEXT_TYPE = " TEXT"
    private const val INT_TYPE = " INTEGER"
    private const val FLOAT_TYPE = " FLOAT"
    private const val TIMESTAMP_TYPE = " TIMESTAMP"

    const val OUTCOME_EVENT_TABLE: String = OutcomeEventsTable.TABLE_NAME
    const val CACHE_UNIQUE_OUTCOME_TABLE: String = CachedUniqueOutcomeTable.TABLE_NAME
    const val CACHE_UNIQUE_OUTCOME_COLUMN_CHANNEL_INFLUENCE_ID: String = CachedUniqueOutcomeTable.COLUMN_CHANNEL_INFLUENCE_ID
    const val CACHE_UNIQUE_OUTCOME_COLUMN_CHANNEL_TYPE: String = CachedUniqueOutcomeTable.COLUMN_CHANNEL_TYPE
    const val SQL_CREATE_OUTCOME_ENTRIES_V1 = "CREATE TABLE " + OutcomeEventsTable.TABLE_NAME + " (" +
            OutcomeEventsTable.ID + " INTEGER PRIMARY KEY," +
            OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_IDS + TEXT_TYPE + "," +
            OutcomeEventsTable.COLUMN_NAME_NAME + TEXT_TYPE + "," +
            OutcomeEventsTable.COLUMN_NAME_SESSION + TEXT_TYPE + "," +
            OutcomeEventsTable.COLUMN_NAME_PARAMS + TEXT_TYPE + "," +
            OutcomeEventsTable.COLUMN_NAME_TIMESTAMP + " TIMESTAMP" +
            ");"
    const val SQL_CREATE_OUTCOME_ENTRIES_V2 = "CREATE TABLE " + OutcomeEventsTable.TABLE_NAME + " (" +
            OutcomeEventsTable.ID + INTEGER_PRIMARY_KEY_TYPE + "," +
            OutcomeEventsTable.COLUMN_NAME_SESSION + TEXT_TYPE + "," +
            OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_IDS + TEXT_TYPE + "," +
            OutcomeEventsTable.COLUMN_NAME_NAME + TEXT_TYPE + "," +
            OutcomeEventsTable.COLUMN_NAME_TIMESTAMP + TIMESTAMP_TYPE + "," +  // "params TEXT" Added in v4, removed in v5.
            OutcomeEventsTable.COLUMN_NAME_WEIGHT + FLOAT_TYPE +  // New in v5, missing migration added in v6
            ");"
    const val SQL_CREATE_OUTCOME_ENTRIES_V3 = "CREATE TABLE " + OutcomeEventsTable.TABLE_NAME + " (" +
            OutcomeEventsTable.ID + INTEGER_PRIMARY_KEY_TYPE + "," +
            OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_INFLUENCE_TYPE + TEXT_TYPE + "," +
            OutcomeEventsTable.COLUMN_NAME_IAM_INFLUENCE_TYPE + TEXT_TYPE + "," +
            OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_IDS + TEXT_TYPE + "," +
            OutcomeEventsTable.COLUMN_NAME_IAM_IDS + TEXT_TYPE + "," +
            OutcomeEventsTable.COLUMN_NAME_NAME + TEXT_TYPE + "," +
            OutcomeEventsTable.COLUMN_NAME_TIMESTAMP + TIMESTAMP_TYPE + "," +  // "params TEXT" Added in v4, removed in v5.
            OutcomeEventsTable.COLUMN_NAME_WEIGHT + FLOAT_TYPE +  // New in v5, missing migration added in v6
            ");"
    const val SQL_CREATE_UNIQUE_OUTCOME_ENTRIES_V1 = "CREATE TABLE " + CachedUniqueOutcomeTable.TABLE_NAME_V1 + " (" +
            CachedUniqueOutcomeTable.ID + INTEGER_PRIMARY_KEY_TYPE + "," +
            CachedUniqueOutcomeTable.COLUMN_NAME_NOTIFICATION_ID + TEXT_TYPE + "," +
            CachedUniqueOutcomeTable.COLUMN_NAME_NAME + TEXT_TYPE +
            ");"
    const val SQL_CREATE_UNIQUE_OUTCOME_ENTRIES_V2 = "CREATE TABLE " + CachedUniqueOutcomeTable.TABLE_NAME_V2 + " (" +
            CachedUniqueOutcomeTable.ID + INTEGER_PRIMARY_KEY_TYPE + "," +
            CachedUniqueOutcomeTable.COLUMN_CHANNEL_INFLUENCE_ID + TEXT_TYPE + "," +
            CachedUniqueOutcomeTable.COLUMN_CHANNEL_TYPE + TEXT_TYPE + "," +
            CachedUniqueOutcomeTable.COLUMN_NAME_NAME + TEXT_TYPE +
            ");"
}