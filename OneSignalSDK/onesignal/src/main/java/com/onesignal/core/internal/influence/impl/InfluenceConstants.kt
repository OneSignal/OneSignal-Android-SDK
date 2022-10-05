package com.onesignal.core.internal.influence.impl

internal object InfluenceConstants {
    const val TIME = "time"

    // OSInAppMessageTracker Constants
    val IAM_TAG: String = InAppMessageTracker::class.java.canonicalName as String
    const val IAM_ID_TAG = "iam_id"

    // OSNotificationTracker Constants
    val NOTIFICATION_TAG: String = NotificationTracker::class.java.canonicalName as String
    const val DIRECT_TAG = "direct"
    const val NOTIFICATIONS_IDS = "notification_ids"
    const val NOTIFICATION_ID_TAG = "notification_id"

    // OUTCOMES KEYS
    // Outcomes Influence Ids
    const val PREFS_OS_LAST_ATTRIBUTED_NOTIFICATION_OPEN = "PREFS_OS_LAST_ATTRIBUTED_NOTIFICATION_OPEN"
    const val PREFS_OS_LAST_NOTIFICATIONS_RECEIVED = "PREFS_OS_LAST_NOTIFICATIONS_RECEIVED"
    const val PREFS_OS_LAST_IAMS_RECEIVED = "PREFS_OS_LAST_IAMS_RECEIVED"

    // Outcomes Channel Influence types
    const val PREFS_OS_OUTCOMES_CURRENT_NOTIFICATION_INFLUENCE = "PREFS_OS_OUTCOMES_CURRENT_SESSION"
    const val PREFS_OS_OUTCOMES_CURRENT_IAM_INFLUENCE = "PREFS_OS_OUTCOMES_CURRENT_IAM_INFLUENCE"
}
