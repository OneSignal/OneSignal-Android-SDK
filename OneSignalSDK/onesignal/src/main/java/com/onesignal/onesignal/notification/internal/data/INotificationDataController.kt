package com.onesignal.onesignal.notification.internal.data

interface INotificationDataController {
    /**
     * We clean outdated cache from several places within the OneSignal SDK here
     * 1. Notifications & unique outcome events linked to notification ids (1 week)
     * 2. Cached In App Messaging Sets in SharedPreferences (impressions, clicks, views) and SQL IAMs
     */
    suspend fun cleanOldCachedData()

    /**
     * Deletes notifications with created timestamps older than 7 days
     * Cleans notification tables
     * 1. NotificationTable.TABLE_NAME
     */
    suspend fun cleanNotificationCache()
    suspend fun clearOneSignalNotifications()
    suspend fun removeGroupedNotifications(group: String)
    suspend fun removeNotification(id: Int)
    suspend fun isDuplicateNotification(id: String?) : Boolean

    // Saving the notification provides the following:
    //   * Prevent duplicates
    //   * Build summary notifications
    //   * Collapse key / id support - Used to lookup the android notification id later
    //   * Redisplay notifications after reboot, upgrade of app, or cold boot after a force kill.
    //   * Future - Public API to get a list of notifications
    suspend fun saveNotification(id: String,
                                 groupId: String?,
                                 collapseKey: String?,
                                 isShown: Boolean,
                                 isOpened: Boolean,
                                 androidId: Int,
                                 title: String?,
                                 body: String?,
                                 expireTime: Long,
                                 jsonPayload: String
    )

    suspend fun markAsDismissed(androidId: Int)

    suspend fun getAndroidIdFromCollapseKey(collapseKey: String) : Int?

    suspend fun clearOldestOverLimitFallback(notificationsToMakeRoomFor: Int, maxNumberOfNotificationsInt: Int)

    suspend fun getMostRecentNotifIdFromGroup(group: String, isGroupless: Boolean): Int?
}