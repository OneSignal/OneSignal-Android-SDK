package com.onesignal.outcomes;

import android.provider.BaseColumns;

class OSOutcomesDbContract {

    public static class OutcomeEventsTable implements BaseColumns {
        static final String TABLE_NAME = "outcome"; // Added on DB v4 SDK v3.12.0
        // Influence ids
        static final String COLUMN_NAME_NOTIFICATION_IDS = "notification_ids"; // Added on DB v4 SDK v3.12.0
        static final String COLUMN_NAME_IAM_IDS = "iam_ids"; // Added on DB v8 SDK v3.14.0
        // Influence type
        static final String COLUMN_NAME_SESSION = "session"; // Added on DB v4 SDK v3.12.0 replaced with notification_influence_type on DB v8 SDK v3.14.0
        static final String COLUMN_NAME_NOTIFICATION_INFLUENCE_TYPE = "notification_influence_type"; // Added on DB v8 SDK v3.14.0
        static final String COLUMN_NAME_IAM_INFLUENCE_TYPE = "iam_influence_type"; // Added on DB v8 SDK v3.14.0
        // Outcome data
        static final String COLUMN_NAME_NAME = "name"; // Added on DB v4 SDK v3.12.0
        static final String COLUMN_NAME_WEIGHT = "weight"; // Added on DB v5 SDK v3.12.1, migration added on DB v6 SDK v3.12.2
        static final String COLUMN_NAME_TIMESTAMP = "timestamp"; // Added on DB v4 SDK v3.12.0
        static final String COLUMN_NAME_PARAMS = "params"; // Added on DB v4 SDK v3.12.0 replaced with weight on DB v5 SDK v3.12.1, migration added on DB v6 SDK v3.12.2
    }

    static class CachedUniqueOutcomeTable implements BaseColumns {
        static final String TABLE_NAME  = CachedUniqueOutcomeTable.TABLE_NAME_V2;
        static final String TABLE_NAME_V1 = "cached_unique_outcome_notification"; // Added on DB v5 SDK v3.12.1 until DB v8 renamed with cached_unique_outcome SDK v3.14.0
        static final String TABLE_NAME_V2 = "cached_unique_outcome"; // Added on DB v8 SDK v3.14.0
        static final String COLUMN_NAME_NOTIFICATION_ID = "notification_id"; // Added on DB v5 SDK v3.12.1 until DB v8 renamed with channel_influence_id SDK v3.14.0
        static final String COLUMN_CHANNEL_INFLUENCE_ID = "channel_influence_id"; // Added on DB v8 SDK v3.14.0
        static final String COLUMN_CHANNEL_TYPE = "channel_type"; // Added on DB v8 SDK v3.14.0
        static final String COLUMN_NAME_NAME = "name"; // Added on DB v5 SDK v3.12.1
    }
}
