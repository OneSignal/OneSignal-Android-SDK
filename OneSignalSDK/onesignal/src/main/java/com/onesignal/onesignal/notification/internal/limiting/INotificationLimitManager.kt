package com.onesignal.onesignal.notification.internal.limiting

/**
 * The limit manager is used to ensure old notifications are cleared up to a limit before displaying
 * new ones. Android does not allow a package to have more than 49 total notifications being shown.
 *
 * This limit prevents the following error;
 *       E/NotificationService: Package has already posted 50 notifications.
 *                              Not showing more.  package=####
 *
 * Even though it says 50 in the error it is really a limit of 49. See
 * NotificationManagerService.java in the AOSP source.
 */
internal interface INotificationLimitManager {

    /**
     * Cancel the oldest notifications to make room for new notifications we are about to display
     * If we don't make this room users will NOT be alerted of new notifications for the app.
     *
     * @param notificationsToMakeRoomFor The number of notifications that are about to be displayed,
     * and the number the limit manager needs to ensure there is room for.
     */
    suspend fun clearOldestOverLimit(notificationsToMakeRoomFor: Int)

    object Constants {
        /**
         * The maximum number of notifications an app is allowed to have in the Android shade.
         */
        val maxNumberOfNotifications: Int = 49
    }
}