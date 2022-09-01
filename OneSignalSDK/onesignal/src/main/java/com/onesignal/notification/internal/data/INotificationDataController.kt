package com.onesignal.notification.internal.data

internal interface INotificationDataController {

    suspend fun createSummaryNotification(androidId: Int, groupId: String)

    /**
     * Create a new notification.
     *
     * @param id The notification's OneSignal ID.
     * @param groupId: The notification's group ID (if there is one).
     * @param collapseKey: The notification's collapse key (if there is one)
     * @param shouldDismissIdenticals: When true, all other notifications with the same androidId will be dismissed.
     * @param isOpened: Whether the notification should be marked as "opened".
     * @param androidId: The notification's Android ID.
     * @param title: The notification's title.
     * @param body: The notification's body.
     * @param expireTime: The notification's expiration dateTime.
     * @param jsonPayload: The full payload of the notification.
     */
    suspend fun createNotification(
        id: String,
        groupId: String?,
        collapseKey: String?,
        shouldDismissIdenticals: Boolean,
        isOpened: Boolean,
        androidId: Int,
        title: String?,
        body: String?,
        expireTime: Long,
        jsonPayload: String
    )

    /**
     * Determine whether a notification with the provided ID exists.
     *
     * @param id The notification's OneSignal id.
     *
     * @return true if the notification exists, false otherwise.
     */
    suspend fun doesNotificationExist(id: String?): Boolean

    /**
     * Retrieve the group ID of a notification, given a notification's Android ID.
     *
     * @param androidId The notification's Android ID.
     *
     * @return The group id of the notification, or null if it doesn't belong to a group.
     */
    suspend fun getGroupId(androidId: Int): String?

    /**
     * Retrieve the Android ID for the group of notifications provided.
     *
     * @param group The group identifier for which the Android ID is to be retrieved.
     * @param getSummaryNotification When true, the Android ID of the summary notification will be returned, otherwise the Android ID for the latest notification in the group will be returned.
     *
     * @return the Android ID, or null if there are no notifications with that group identifier.
     */
    suspend fun getAndroidIdForGroup(group: String, getSummaryNotification: Boolean): Int?

    /**
     * Retrieve the Android ID for the collapse key provided.
     *
     * @param collapseKey The collapse key for which the Android ID is to be retrieved.
     *
     * @return the Android ID, or null if there are no notifications with that group identifier.
     */
    suspend fun getAndroidIdFromCollapseKey(collapseKey: String): Int?

    /**
     * List all notifications that belong to the provided group.
     *
     * @param group The group identifier for which the notifications should be returned.
     *
     * @return The list of each notifications full payload that belongs to the group provided.
     */
    suspend fun listNotificationsForGroup(group: String): List<NotificationData>

    /**
     * List all notifications that are considered outstanding in the system.
     *
     * @param excludeAndroidIds An optional list of Android IDs that should be excluded from the list.
     *
     * @return The list of outstanding notifications.
     */
    suspend fun listNotificationsForOutstanding(excludeAndroidIds: List<Int>? = null): List<NotificationData>

    suspend fun markAsConsumed(androidId: Int, dismissed: Boolean, summaryGroup: String? = null, clearGroupOnSummaryClick: Boolean = true)

    /**
     * Mark as dismissed the notification with the Android ID provided
     *
     * @param androidId The notification's Android ID
     *
     * @return true if a notification was marked as dismissed, false otherwise.
     */
    suspend fun markAsDismissed(androidId: Int): Boolean
    suspend fun markAsDismissedForGroup(group: String)
    suspend fun markAsDismissedForOutstanding()

    suspend fun clearOldestOverLimitFallback(notificationsToMakeRoomFor: Int, maxNumberOfNotificationsInt: Int)

    /**
     * Deletes notifications in the database that have expired.  An expired notification
     * will no longer be processed by the system.  Expiration occurs after 7 days of
     * receiving the notification, regardless of whether it has been opened/dismissed.
     */
    suspend fun deleteExpiredNotifications()

    class NotificationData(
        val androidId: Int,
        val id: String,
        val fullData: String,
        val createdAt: Long,
        val title: String,
        val message: String
    )
}
