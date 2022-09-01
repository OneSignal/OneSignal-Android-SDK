package com.onesignal.onesignal.core.internal.influence.impl


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

    // Outcomes Influence params
    const val PREFS_OS_NOTIFICATION_LIMIT = "PREFS_OS_NOTIFICATION_LIMIT"
    const val PREFS_OS_IAM_LIMIT = "PREFS_OS_IAM_LIMIT"
    const val PREFS_OS_NOTIFICATION_INDIRECT_ATTRIBUTION_WINDOW = "PREFS_OS_INDIRECT_ATTRIBUTION_WINDOW"
    const val PREFS_OS_IAM_INDIRECT_ATTRIBUTION_WINDOW = "PREFS_OS_IAM_INDIRECT_ATTRIBUTION_WINDOW"

    // Outcomes Influence enable params
    const val PREFS_OS_DIRECT_ENABLED = "PREFS_OS_DIRECT_ENABLED"
    const val PREFS_OS_INDIRECT_ENABLED = "PREFS_OS_INDIRECT_ENABLED"
    const val PREFS_OS_UNATTRIBUTED_ENABLED = "PREFS_OS_UNATTRIBUTED_ENABLED"

    // Outcomes Channel Influence types
    const val PREFS_OS_OUTCOMES_CURRENT_NOTIFICATION_INFLUENCE = "PREFS_OS_OUTCOMES_CURRENT_SESSION"
    const val PREFS_OS_OUTCOMES_CURRENT_IAM_INFLUENCE = "PREFS_OS_OUTCOMES_CURRENT_IAM_INFLUENCE"
}
