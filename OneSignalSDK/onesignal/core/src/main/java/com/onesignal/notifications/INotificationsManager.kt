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
     * The [IPermissionChangedHandler.onPermissionChanged] method will be fired on the passed-in
     * object when a notification permission setting changes. This happens when the user enables or
     * disables notifications for your app from the system settings outside of your app. Disable
     * detection is supported on Android 4.4+
     *
     * *Keep a reference<* - Make sure to hold a reference to your handler at the class level,
     * otherwise it may not fire.
     *
     * *Leak Safe* - OneSignal holds a weak reference to your handler so it's guaranteed not to
     * leak your `Activity`.
     *
     * @param handler the instance of [IPermissionChangedHandler] that you want to process
     *                the permission changes within
     */
    fun addPermissionChangedHandler(handler: IPermissionChangedHandler)

    /**
     * Remove a push permission handler that has been previously added.
     *
     * @param handler The previously added handler that should be removed.
     */
    fun removePermissionChangedHandler(handler: IPermissionChangedHandler)

    /**
     * Sets the handler to run before displaying a notification while the app is in focus. Use this
     * handler to read notification data or decide if the notification should show or not.
     *
     * *Note:* this runs after the Notification Service Extension [IRemoteNotificationReceivedHandler]
     * has been called (if one exists), which has the following differences:
     *
     * 1. The [IRemoteNotificationReceivedHandler] is configured within your `AndroidManifest.xml`.
     * 2. The [IRemoteNotificationReceivedHandler] will be called regardless of the state of your
     *    app, while [INotificationWillShowInForegroundHandler] is *only* called when your app is
     *    in focus.
     * 3. The [IRemoteNotificationReceivedHandler] can make changes to the notification, while
     *    [INotificationWillShowInForegroundHandler] can only indicate not to show it.
     *
     * @param handler: The handler that is to be called when the even occurs.
     */
    fun setNotificationWillShowInForegroundHandler(handler: INotificationWillShowInForegroundHandler?)

    /**
     * Sets a handler that will run whenever a notification is clicked on by the user.
     *
     * @param handler The handler that is to be called when the event occurs.
     */
    fun setNotificationClickHandler(handler: INotificationClickHandler?)
}
