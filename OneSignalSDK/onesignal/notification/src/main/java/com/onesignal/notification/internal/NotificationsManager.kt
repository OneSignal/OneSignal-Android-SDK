package com.onesignal.notification.internal

import android.app.Activity
import com.onesignal.common.events.EventProducer
import com.onesignal.common.exceptions.BackendException
import com.onesignal.common.threading.suspendifyOnMain
import com.onesignal.common.threading.suspendifyOnThread
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.notification.INotificationOpenedHandler
import com.onesignal.notification.INotificationWillShowInForegroundHandler
import com.onesignal.notification.INotificationsManager
import com.onesignal.notification.IPermissionChangedHandler
import com.onesignal.notification.IPermissionState
import com.onesignal.notification.PostNotificationException
import com.onesignal.notification.internal.backend.INotificationBackendService
import com.onesignal.notification.internal.common.GenerateNotificationOpenIntentFromPushPayload
import com.onesignal.notification.internal.common.NotificationHelper
import com.onesignal.notification.internal.data.INotificationRepository
import com.onesignal.notification.internal.lifecycle.INotificationLifecycleService
import com.onesignal.notification.internal.permissions.INotificationPermissionChangedHandler
import com.onesignal.notification.internal.permissions.INotificationPermissionController
import com.onesignal.notification.internal.restoration.INotificationRestoreWorkManager
import com.onesignal.notification.internal.summary.INotificationSummaryManager
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

interface INotificationStateRefresher {
    fun refreshNotificationState()
}

interface INotificationActivityOpener {
    suspend fun openDestinationActivity(activity: Activity, pushPayloads: JSONArray)
}

/**
 * The notification manager is responsible for the management of notifications
 * on the current device (not the user).
 */
internal class NotificationsManager(
    private val _applicationService: IApplicationService,
    private val _configModelStore: ConfigModelStore,
    private val _backend: INotificationBackendService,
    private val _notificationPermissionController: INotificationPermissionController,
    private val _notificationRestoreWorkManager: INotificationRestoreWorkManager,
    private val _notificationLifecycleService: INotificationLifecycleService,
    private val _notificationDataController: INotificationRepository,
    private val _summaryManager: INotificationSummaryManager
) : INotificationsManager,
    INotificationActivityOpener,
    INotificationStateRefresher,
    INotificationPermissionChangedHandler {

    override var permissionStatus: IPermissionState = PermissionState(false)
    override var unsubscribeWhenNotificationsAreDisabled: Boolean = false

    private val _permissionChangedNotifier = EventProducer<IPermissionChangedHandler>()

    init {
        _notificationPermissionController.subscribe(this)
        suspendifyOnThread {
            _notificationDataController.deleteExpiredNotifications()
        }
    }

    /**
     * Called when app calls [requestPermission] and the user accepts/denies permission.
     */
    override fun onNotificationPermissionChanged(enabled: Boolean) {
        setPermissionStatusAndFire(enabled)
    }

    /**
     * Called when app has gained focus and the notification state should be refreshed.
     */
    override fun refreshNotificationState() {
        // ensure all notifications for this app have been restored to the notification panel
        _notificationRestoreWorkManager.beginEnqueueingWork(_applicationService.appContext, false)

        val isEnabled = NotificationHelper.areNotificationsEnabled(_applicationService.appContext)
        setPermissionStatusAndFire(isEnabled)
    }

    override suspend fun requestPermission(): Boolean {
        Logging.debug("NotificationsManager.requestPermission()")
        return _notificationPermissionController.prompt(true)
    }

    private fun setPermissionStatusAndFire(isEnabled: Boolean) {
        val oldPermissionStatus = permissionStatus
        permissionStatus = PermissionState(isEnabled)

        if (oldPermissionStatus.notificationsEnabled != isEnabled) {
            // switch over to the main thread for the firing of the event
            suspendifyOnMain {
                val changes = PermissionStateChanges(oldPermissionStatus, permissionStatus)
                _permissionChangedNotifier.fire { it.onPermissionChanged(changes) }
            }
        }
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
     */
    override suspend fun postNotification(json: JSONObject): JSONObject {
        Logging.debug("NotificationsManager.postNotification(json: $json)")

        // app_id will not be set if init was never called.
        val appId = if (json.has("app_id")) {
            json.getString("app_id")
        } else {
            _configModelStore.model.appId
        }

        try {
            var jsonObject = _backend.postNotification(appId, json)

            if (!jsonObject.has("errors")) {
                return jsonObject
            } else {
                throw PostNotificationException(jsonObject)
            }
        } catch (ex: BackendException) {
            val jsonObject = if (ex.response != null) {
                JSONObject(ex.response)
            } else {
                JSONObject("{\"error\": \"HTTP no response error\"}")
            }

            throw PostNotificationException(jsonObject)
        }
    }

    override suspend fun postNotification(json: String): JSONObject = postNotification(JSONObject(json))

    /**
     * Cancels a single OneSignal notification based on its Android notification integer ID. Use
     * instead of Android's [android.app.NotificationManager.cancel}, otherwise the notification will be restored
     * when your app is restarted.
     *
     * @param id
     */
    override suspend fun removeNotification(id: Int) {
        Logging.debug("NotificationsManager.removeNotification(id: $id)")
        if (_notificationDataController.markAsDismissed(id)) {
            _summaryManager.updatePossibleDependentSummaryOnDismiss(id)
        }
    }

    override suspend fun removeGroupedNotifications(group: String) {
        Logging.debug("NotificationsManager.removeGroupedNotifications(group: $group)")
        _notificationDataController.markAsDismissedForGroup(group)
    }

    /**
     * Removes all OneSignal notifications from the Notification Shade. If you just use
     * [android.app.NotificationManager.cancelAll], OneSignal notifications will be restored when
     * your app is restarted.
     */
    override suspend fun clearAll() {
        Logging.debug("NotificationsManager.clearAll()")
        _notificationDataController.markAsDismissedForOutstanding()
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
    override fun addPushPermissionHandler(handler: IPermissionChangedHandler) {
        Logging.debug("NotificationsManager.addPushPermissionHandler(handler: $handler)")
        _permissionChangedNotifier.subscribe(handler)
    }

    /**
     * Remove a push permission handler that has been previously added.
     *
     * @param handler The previously added handler that should be removed.
     */
    override fun removePushPermissionHandler(handler: IPermissionChangedHandler) {
        Logging.debug("NotificationsManager.removePushPermissionHandler(handler: $handler)")
        _permissionChangedNotifier.unsubscribe(handler)
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
    override fun setNotificationWillShowInForegroundHandler(handler: INotificationWillShowInForegroundHandler) {
        Logging.debug("NotificationsManager.setNotificationWillShowInForegroundHandler(handler: $handler)")
        _notificationLifecycleService.setExternalWillShowInForegroundHandler(handler)
    }

    /**
     * Sets a handler that will run whenever a notification is tapped on by the user.
     *
     * @param handler The handler that is to be called when the event occurs.
     */
    override fun setNotificationOpenedHandler(handler: INotificationOpenedHandler) {
        Logging.debug("NotificationsManager.setNotificationOpenedHandler(handler: $handler)")
        _notificationLifecycleService.setExternalNotificationOpenedHandler(handler)
    }

    override suspend fun openDestinationActivity(activity: Activity, pushPayloads: JSONArray) {
        try {
            // Always use the top most notification if user tapped on the summary notification
            val firstPayloadItem = pushPayloads.getJSONObject(0)
            // val isHandled = _notificationLifecycleService.canOpenNotification(activity, firstPayloadItem)
            val intentGenerator = GenerateNotificationOpenIntentFromPushPayload.create(activity, firstPayloadItem)

            val intent = intentGenerator.getIntentVisible()
            if (intent != null) {
                Logging.info("SDK running startActivity with Intent: $intent")
                activity.startActivity(intent)
            } else {
                Logging.info("SDK not showing an Activity automatically due to it's settings.")
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }
}
