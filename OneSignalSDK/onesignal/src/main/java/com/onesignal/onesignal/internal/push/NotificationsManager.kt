package com.onesignal.onesignal.internal.push

import com.onesignal.OSPermissionObserver
import com.onesignal.OneSignal
import com.onesignal.onesignal.notification.INotificationsManager
import org.json.JSONObject

/**
 * The notification manager is responsible for the management of notifications
 * on the current device (not the user).
 */
class NotificationsManager : INotificationsManager {
    override var permissionStatus: String = ""

    /** TODO: ASYNC **/
    override fun promptForPushPermissionStatus() {

    }

    /**
     * Allows you to send notifications from user to user or schedule ones in the future to be delivered
     * to the current device.
     *
     *
     * *Note:* You can only use `include_player_ids` as a targeting parameter from your app.
     * Other target options such as `tags` and `included_segments` require your OneSignal
     * App REST API key which can only be used from your server.
     *
     * @param json Contains notification options, see <a href="https://documentation.onesignal.com/reference#create-notification">OneSignal | Create Notification</a>
     *              POST call for all options.
     * @param callback a {@link PostNotificationResponseHandler} object to receive the request result
     *
     * TODO: ASYNC
     * TODO: CAN WE SUPPLY A NOTIFICATION OBJECT
     */
    override fun postNotification(json: JSONObject, callback: com.onesignal.OneSignal.PostNotificationResponseHandler) {
        // Should this be in the java API library only?
    }

    override fun postNotification(json: String, callback: com.onesignal.OneSignal.PostNotificationResponseHandler) {
        // Should this be in the java API library only?
    }

    /**
     * Cancels a single OneSignal notification based on its Android notification integer ID. Use
     * instead of Android's [android.app.NotificationManager.cancel}, otherwise the notification will be restored
     * when your app is restarted.
     *
     * @param id
     */
    override fun removeNotification(id: Int) {

    }

    override fun removeGroupedNotifications(group: String) {

    }

    /**
     * Removes all OneSignal notifications from the Notification Shade. If you just use
     * [android.app.NotificationManager.cancelAll], OneSignal notifications will be restored when
     * your app is restarted.
     */
    override fun clearAll() {

    }

    /**
     * The [com.onesignal.OSPermissionObserver.onOSPermissionChanged]
     * method will be fired on the passed-in object when a notification permission setting changes.
     * This happens when the user enables or disables notifications for your app from the system
     * settings outside of your app. Disable detection is supported on Android 4.4+
     *
     *
     * *Keep a reference<* - Make sure to hold a reference to your observable at the class level,
     * otherwise it may not fire
     *
     * *Leak Safe* - OneSignal holds a weak reference to your observer so it's guaranteed not to
     * leak your `Activity`
     *
     * @param handler the instance of [com.onesignal.OSPermissionObserver] that you want to process
     *                 the permission changes within
     */
    override fun addPushPermissionHandler(handler: OSPermissionObserver) {

    }

    /**
     * Remove a push permission handler that has been previously added.
     *
     * @param handler The previously added handler that should be removed.
     */
    override fun removePushPermissionHandler(handler: OSPermissionObserver) {

    }

    /**
     * Sets a handler to run before displaying a notification while the app is in focus. Use this handler to read
     * notification data and change it or decide if the notification should show or not.
     *
     * *Note:* this runs after the Notification Service Extension which can be used to modify the notification
     * before showing it.
     *
     * @param handler: The handler that is to be called when the even occurs.
     */
    override fun setNotificationWillShowInForegroundHandler(handler: OneSignal.OSNotificationWillShowInForegroundHandler) {

    }

    /**
     * Sets a handler that will run whenever a notification is tapped on by the user.
     *
     * @param handler The handler that is to be called when the event occurs.
     */
    override fun setNotificationOpenedHandler(handler: OneSignal.OSNotificationOpenedHandler) {

    }
}
