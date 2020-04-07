package com.onesignal.outcomes;

import android.provider.BaseColumns;

class OSOutcomesDbContract {

    public static class OutcomeEventsTable implements BaseColumns {
        static final String TABLE_NAME = "outcome";
        // Influence ids
        static final String COLUMN_NAME_NOTIFICATION_IDS = "notification_ids"; // OneSignal Notification Ids
        static final String COLUMN_NAME_IAM_IDS = "iam_ids"; // OneSignal iam Ids
        // Influence type
        static final String COLUMN_NAME_SESSION = "session"; // Old column name
        static final String COLUMN_NAME_NOTIFICATION_INFLUENCE_TYPE = "notification_influence_type";
        static final String COLUMN_NAME_IAM_INFLUENCE_TYPE = "iam_influence_type";
        // Outcome data
        static final String COLUMN_NAME_NAME = "name";
        static final String COLUMN_NAME_WEIGHT = "weight";
        static final String COLUMN_NAME_TIMESTAMP = "timestamp";
    }

    static class CachedUniqueOutcomeTable implements BaseColumns {
        static final String OLD_TABLE_NAME = "cached_unique_outcome_notification"; // Old table name
        static final String TABLE_NAME = "cached_unique_outcome";
        static final String COLUMN_NAME_NOTIFICATION_ID = "notification_id"; // Old column name
        static final String COLUMN_CHANNEL_INFLUENCE_ID = "channel_influence_id"; // OneSignal Channel influence Id
        static final String COLUMN_CHANNEL_TYPE = "channel_type"; // OneSignal Channel Type
        static final String COLUMN_NAME_NAME = "name";
    }
}
