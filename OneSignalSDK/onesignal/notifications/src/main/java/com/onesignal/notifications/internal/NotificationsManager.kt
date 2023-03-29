package com.onesignal.notifications.internal

import android.app.Activity
import com.onesignal.common.events.EventProducer
import com.onesignal.common.threading.suspendifyOnThread
import com.onesignal.core.internal.application.IApplicationLifecycleHandler
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.notifications.INotificationClickHandler
import com.onesignal.notifications.INotificationWillShowInForegroundHandler
import com.onesignal.notifications.INotificationsManager
import com.onesignal.notifications.IPermissionChangedHandler
import com.onesignal.notifications.internal.common.GenerateNotificationOpenIntentFromPushPayload
import com.onesignal.notifications.internal.common.NotificationHelper
import com.onesignal.notifications.internal.data.INotificationRepository
import com.onesignal.notifications.internal.lifecycle.INotificationLifecycleService
import com.onesignal.notifications.internal.permissions.INotificationPermissionChangedHandler
import com.onesignal.notifications.internal.permissions.INotificationPermissionController
import com.onesignal.notifications.internal.restoration.INotificationRestoreWorkManager
import com.onesignal.notifications.internal.summary.INotificationSummaryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException

interface INotificationActivityOpener {
    suspend fun openDestinationActivity(activity: Activity, pushPayloads: JSONArray)
}

/**
 * The notification manager is responsible for the management of notifications
 * on the current device (not the user).
 */
internal class NotificationsManager(
    private val _applicationService: IApplicationService,
    private val _notificationPermissionController: INotificationPermissionController,
    private val _notificationRestoreWorkManager: INotificationRestoreWorkManager,
    private val _notificationLifecycleService: INotificationLifecycleService,
    private val _notificationDataController: INotificationRepository,
    private val _summaryManager: INotificationSummaryManager,
) : INotificationsManager,
    INotificationActivityOpener,
    INotificationPermissionChangedHandler,
    IApplicationLifecycleHandler {

    override var permission: Boolean = NotificationHelper.areNotificationsEnabled(_applicationService.appContext)

    override val canRequestPermission: Boolean
        get() = _notificationPermissionController.canRequestPermission

    private val _permissionChangedNotifier = EventProducer<IPermissionChangedHandler>()

    init {
        _applicationService.addApplicationLifecycleHandler(this)
        _notificationPermissionController.subscribe(this)

        suspendifyOnThread {
            _notificationDataController.deleteExpiredNotifications()
        }
    }

    override fun onFocus() {
        refreshNotificationState()
    }

    override fun onUnfocused() {
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
    private fun refreshNotificationState() {
        // ensure all notifications for this app have been restored to the notification panel
        _notificationRestoreWorkManager.beginEnqueueingWork(_applicationService.appContext, false)

        val isEnabled = NotificationHelper.areNotificationsEnabled(_applicationService.appContext)
        setPermissionStatusAndFire(isEnabled)
    }

    override suspend fun requestPermission(fallbackToSettings: Boolean): Boolean {
        Logging.debug("NotificationsManager.requestPermission()")

        return withContext(Dispatchers.Main) {
            return@withContext _notificationPermissionController.prompt(fallbackToSettings)
        }
    }

    private fun setPermissionStatusAndFire(isEnabled: Boolean) {
        val oldPermissionStatus = permission
        permission = isEnabled

        if (oldPermissionStatus != isEnabled) {
            // switch over to the main thread for the firing of the event
            _permissionChangedNotifier.fireOnMain { it.onPermissionChanged(isEnabled) }
        }
    }

    /**
     * Cancels a single OneSignal notification based on its Android notification integer ID. Use
     * instead of Android's [android.app.NotificationManager.cancel}, otherwise the notification will be restored
     * when your app is restarted.
     *
     * @param id
     */
    override fun removeNotification(id: Int) {
        Logging.debug("NotificationsManager.removeNotification(id: $id)")

        suspendifyOnThread {
            if (_notificationDataController.markAsDismissed(id)) {
                _summaryManager.updatePossibleDependentSummaryOnDismiss(id)
            }
        }
    }

    override fun removeGroupedNotifications(group: String) {
        Logging.debug("NotificationsManager.removeGroupedNotifications(group: $group)")

        suspendifyOnThread {
            _notificationDataController.markAsDismissedForGroup(group)
        }
    }

    /**
     * Removes all OneSignal notifications from the Notification Shade. If you just use
     * [android.app.NotificationManager.cancelAll], OneSignal notifications will be restored when
     * your app is restarted.
     */
    override fun clearAllNotifications() {
        Logging.debug("NotificationsManager.clearAllNotifications()")

        suspendifyOnThread {
            _notificationDataController.markAsDismissedForOutstanding()
        }
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
    override fun addPermissionChangedHandler(handler: IPermissionChangedHandler) {
        Logging.debug("NotificationsManager.addPermissionChangedHandler(handler: $handler)")
        _permissionChangedNotifier.subscribe(handler)
    }

    /**
     * Remove a push permission handler that has been previously added.
     *
     * @param handler The previously added handler that should be removed.
     */
    override fun removePermissionChangedHandler(handler: IPermissionChangedHandler) {
        Logging.debug("NotificationsManager.removePermissionChangedHandler(handler: $handler)")
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
    override fun setNotificationWillShowInForegroundHandler(handler: INotificationWillShowInForegroundHandler?) {
        Logging.debug("NotificationsManager.setNotificationWillShowInForegroundHandler(handler: $handler)")
        _notificationLifecycleService.setExternalWillShowInForegroundHandler(handler)
    }

    /**
     * Sets a handler that will run whenever a notification is tapped on by the user.
     *
     * @param handler The handler that is to be called when the event occurs.
     */
    override fun setNotificationClickHandler(handler: INotificationClickHandler?) {
        Logging.debug("NotificationsManager.setNotificationClickHandler(handler: $handler)")
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
