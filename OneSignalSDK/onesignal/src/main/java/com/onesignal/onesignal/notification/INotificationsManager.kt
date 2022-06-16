package com.onesignal.onesignal.notification

import org.json.JSONObject

interface INotificationsManager {
    /** The current permission state **/
    val permissionStatus: IPermissionState

    /**
     * Whether the current device push subscription should be automatically unsubscribed (deleted)
     * if the user disables notifications on the device/app this subscription is for.
     */
    var unsubscribeWhenNotificationsAreDisabled: Boolean

    /**
     * Prompt the user for permission to push notifications.  This will display the native
     * OS prompt to request push notification permission.  If the user enables, a push
     * subscription to this device will be automatically added to the user.
     *
     * @return true if the user is opted in to push notifications (Android 13 and higher, user affirmed or already enabled. < Android 13, already enabled)
     *         false if the user is opted out of push notifications (Android 13 and higher, user rejected. < Android 13, this cannot happen)
     *         null if the intent could not be determined (notification disabled and user sent to settings to hopefully enable.)
     */
    suspend fun promptForPushPermissionStatus() : Boolean?

    /**
     * Allows you to send notifications from user to user or schedule ones in the future to be delivered
     * to the current device.
     *
     * *Note:* You can only use `include_player_ids` as a targeting parameter from your app.
     * Other target options such as `tags` and `included_segments` require your OneSignal
     * App REST API key which can only be used from your server.
     *
     * @param json Contains notification options, see [OneSignal | Create Notification](https://documentation.onesignal.com/reference#create-notification)
     *             POST call for all options.
     *
     * TODO: CAN WE SUPPLY A NOTIFICATION OBJECT??
     * TODO: SHOULD DEV BRING IN THE API CLIENT LIBRARY IF THEY WANT TO DO THIS?
     * TODO: WHAT SHOULD THIS RETURN?
     * TODO: SHOULD THERE BE A DISTINCTION BETWEEN A "PUSHMANAGER" AND A "NOTIFICATIONMANAGER", THIS IS THE ONLY THING THAT
     *       IS NOTIFICATION GENERIC, EVERYTHING ELSE IS PUSH/DEVICE BASED
     */
    suspend fun postNotification(json: JSONObject) : JSONObject
    suspend fun postNotification(json: String) : JSONObject

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
    fun clearAll()

    /**
     * The [IPermissionChangedHandler.onOSPermissionChanged] method will be fired on the passed-in
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
    fun addPushPermissionHandler(handler: IPermissionChangedHandler)
    fun addPushPermissionHandler(handler: (IPermissionStateChanges?) -> Unit) // TODO: SHOULD INCLUDE?

    /**
     * Remove a push permission handler that has been previously added.
     *
     * @param handler The previously added handler that should be removed.
     */
    fun removePushPermissionHandler(handler: IPermissionChangedHandler)
    fun removePushPermissionHandler(handler: (IPermissionStateChanges?) -> Unit) // TODO: SHOULD INCLUDE?

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
    fun setNotificationWillShowInForegroundHandler(handler: INotificationWillShowInForegroundHandler)

    /**
     * Sets a handler that will run whenever a notification is tapped on by the user.
     *
     * @param handler The handler that is to be called when the event occurs.
     */
    fun setNotificationOpenedHandler(handler: INotificationOpenedHandler)
}
