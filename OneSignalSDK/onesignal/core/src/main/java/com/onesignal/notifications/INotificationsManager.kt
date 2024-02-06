package com.onesignal.notifications

/**
 * The entry point to the notification SDK for OneSignal.
 */
interface INotificationsManager {
    /**
     * Whether this app has push notification permission.
     */
    val permission: Boolean

    /**
     * Whether this app can request push notification permission.
     */
    val canRequestPermission: Boolean

    /**
     * Prompt the user for permission to push notifications.  This will display the native
     * OS prompt to request push notification permission.  If the user enables, a push
     * subscription to this device will be automatically added to the user.
     *
     * Be aware of best practices regarding asking permissions on Android:
     * [Requesting Permissions | Android Developers] (https://developer.android.com/guide/topics/permissions/requesting.html)
     *
     * @param fallbackToSettings Whether to direct the user to this app's settings to drive
     * enabling of notifications, when the in-app prompting is not possible.
     *
     * @return true if the user is opted in to push notifications (Android 13 and higher, user affirmed or already enabled. < Android 13, already enabled)
     *         false if the user is opted out of push notifications (user rejected)
     */
    suspend fun requestPermission(fallbackToSettings: Boolean): Boolean

    /**
     * Cancels a single OneSignal notification based on its Android notification integer ID. Use
     * instead of Android's [android.app.NotificationManager.cancel], otherwise the notification will be restored
     * when your app is restarted.
     *
     * @param id The android ID of the notification to be removed. See [INotification.androidNotificationId].
     */
    fun removeNotification(id: Int)

    /**
     * Cancels a group of OneSignal notifications with the provided group key. Grouping notifications
     * is a OneSignal concept, there is no [android.app.NotificationManager] equivalent.
     *
     * @param group The group key which all notifications fall under will be removed. See [INotification.groupKey].
     */
    fun removeGroupedNotifications(group: String)

    /**
     * Removes all OneSignal notifications from the Notification Shade. If you just use
     * [android.app.NotificationManager.cancelAll], OneSignal notifications will be restored when
     * your app is restarted.
     */
    fun clearAllNotifications()

    /**
     * Add a permission observer that will run when the notification permission changes. This happens
     * when the user enables or disables notifications for your app either within the app or from the
     * system settings outside of your app. Disable detection is supported on Android 4.4+.
     *
     * *Keep a reference* - Make sure to hold a reference to your handler at the class level,
     * otherwise it may not fire.
     *
     * *Leak Safe* - OneSignal holds a weak reference to your handler so it's guaranteed not to
     * leak your `Activity`.
     *
     * @param observer the instance of [IPermissionObserver] that you want to process
     *                 the permission changes within
     */
    fun addPermissionObserver(observer: IPermissionObserver)

    /**
     * Remove a permission observer that has been previously added.
     *
     * @param observer The previously added observer that should be removed.
     */
    fun removePermissionObserver(observer: IPermissionObserver)

    /**
     * Add a foreground lifecycle listener that will run whenever a notification lifecycle
     * event occurs.
     *
     * @param listener: The listener that is to be called when the event occurs.
     */
    fun addForegroundLifecycleListener(listener: INotificationLifecycleListener)

    /**
     * Remove a foreground lifecycle listener that has been previously added.
     *
     * @param listener The previously added listener that should be removed.
     */
    fun removeForegroundLifecycleListener(listener: INotificationLifecycleListener)

    /**
     * Add a click listener that will run whenever a notification is clicked on by the user.
     *
     * @param listener The listener that is to be called when the event occurs.
     */
    fun addClickListener(listener: INotificationClickListener)

    /**
     * Remove a click listener that has been previously added.
     *
     * @param listener The previously added listener that should be removed.
     */
    fun removeClickListener(listener: INotificationClickListener)
}
