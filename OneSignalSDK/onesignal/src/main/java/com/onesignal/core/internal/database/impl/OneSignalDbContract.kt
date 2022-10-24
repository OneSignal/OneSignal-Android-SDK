package com.onesignal.core.internal.database.impl

import android.provider.BaseColumns

class OneSignalDbContract {
    object NotificationTable : BaseColumns {
        const val TABLE_NAME = "notification"
        const val COLUMN_NAME_NOTIFICATION_ID = "notification_id" // OneSignal Notification Id
        const val COLUMN_NAME_ANDROID_NOTIFICATION_ID = "android_notification_id"
        const val COLUMN_NAME_GROUP_ID = "group_id"
        const val COLUMN_NAME_COLLAPSE_ID = "collapse_id"
        const val COLUMN_NAME_IS_SUMMARY = "is_summary"
        const val COLUMN_NAME_OPENED = "opened"
        const val COLUMN_NAME_DISMISSED = "dismissed"
        const val COLUMN_NAME_TITLE = "title"
        const val COLUMN_NAME_MESSAGE = "message"
        const val COLUMN_NAME_CREATED_TIME = "created_time"
        const val COLUMN_NAME_EXPIRE_TIME = "expire_time" // created_time + TTL

        // JSON formatted string of the full FCM bundle
        const val COLUMN_NAME_FULL_DATA = "full_data"
        const val INDEX_CREATE_NOTIFICATION_ID =
            "CREATE INDEX notification_notification_id_idx ON notification(notification_id); "
        const val INDEX_CREATE_ANDROID_NOTIFICATION_ID =
            "CREATE INDEX notification_android_notification_id_idx ON notification(android_notification_id); "
        const val INDEX_CREATE_GROUP_ID =
            "CREATE INDEX notification_group_id_idx ON notification(group_id); "
        const val INDEX_CREATE_COLLAPSE_ID =
            "CREATE INDEX notification_collapse_id_idx ON notification(collapse_id); "
        const val INDEX_CREATE_CREATED_TIME =
            "CREATE INDEX notification_created_time_idx ON notification(created_time); "
        const val INDEX_CREATE_EXPIRE_TIME =
            "CREATE INDEX notification_expire_time_idx ON notification(expire_time); "
    }

    object InAppMessageTable : BaseColumns {
        const val TABLE_NAME = "in_app_message"
        const val COLUMN_NAME_MESSAGE_ID = "message_id" // OneSignal IAM Ids
        const val COLUMN_NAME_DISPLAY_QUANTITY = "display_quantity"
        const val COLUMN_NAME_LAST_DISPLAY = "last_display"
        const val COLUMN_CLICK_IDS = "click_ids"
        const val COLUMN_DISPLAYED_IN_SESSION = "displayed_in_session"
    }
}
