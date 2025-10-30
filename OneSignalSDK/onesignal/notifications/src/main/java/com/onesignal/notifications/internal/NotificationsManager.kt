package com.onesignal.notifications.internal

import android.app.Activity
import com.onesignal.common.events.EventProducer
import com.onesignal.common.threading.suspendifyOnIO
import com.onesignal.core.internal.application.IApplicationLifecycleHandler
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.notifications.INotificationClickListener
import com.onesignal.notifications.INotificationLifecycleListener
import com.onesignal.notifications.INotificationsManager
import com.onesignal.notifications.IPermissionObserver
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

interface INotificationActivityOpener {
    suspend fun openDestinationActivity(
        activity: Activity,
        pushPayloads: JSONArray,
    )
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
    INotificationPermissionChangedHandler,
    IApplicationLifecycleHandler {
    override var permission: Boolean = NotificationHelper.areNotificationsEnabled(_applicationService.appContext)

    override val canRequestPermission: Boolean
        get() = _notificationPermissionController.canRequestPermission

    private val permissionChangedNotifier = EventProducer<IPermissionObserver>()

    init {
        _applicationService.addApplicationLifecycleHandler(this)
        _notificationPermissionController.subscribe(this)

        suspendifyOnIO {
            _notificationDataController.deleteExpiredNotifications()
        }
    }

    override fun onFocus(firedOnSubscribe: Boolean) {
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
            permissionChangedNotifier.fireOnMain { it.onNotificationPermissionChange(isEnabled) }
        }
    }

    override fun removeNotification(id: Int) {
        Logging.debug("NotificationsManager.removeNotification(id: $id)")

        suspendifyOnIO {
            if (_notificationDataController.markAsDismissed(id)) {
                _summaryManager.updatePossibleDependentSummaryOnDismiss(id)
            }
        }
    }

    override fun removeGroupedNotifications(group: String) {
        Logging.debug("NotificationsManager.removeGroupedNotifications(group: $group)")

        suspendifyOnIO {
            _notificationDataController.markAsDismissedForGroup(group)
        }
    }

    override fun clearAllNotifications() {
        Logging.debug("NotificationsManager.clearAllNotifications()")

        suspendifyOnIO {
            _notificationDataController.markAsDismissedForOutstanding()
        }
    }

    override fun addPermissionObserver(observer: IPermissionObserver) {
        Logging.debug("NotificationsManager.addPermissionObserver(observer: $observer)")
        permissionChangedNotifier.subscribe(observer)
    }

    override fun removePermissionObserver(observer: IPermissionObserver) {
        Logging.debug("NotificationsManager.removePermissionObserver(observer: $observer)")
        permissionChangedNotifier.unsubscribe(observer)
    }

    override fun addForegroundLifecycleListener(listener: INotificationLifecycleListener) {
        Logging.debug("NotificationsManager.addForegroundLifecycleListener(listener: $listener)")
        _notificationLifecycleService.addExternalForegroundLifecycleListener(listener)
    }

    override fun removeForegroundLifecycleListener(listener: INotificationLifecycleListener) {
        Logging.debug("NotificationsManager.removeForegroundLifecycleListener(listener: $listener)")
        _notificationLifecycleService.removeExternalForegroundLifecycleListener(listener)
    }

    override fun addClickListener(listener: INotificationClickListener) {
        Logging.debug("NotificationsManager.addClickListener(handler: $listener)")
        _notificationLifecycleService.addExternalClickListener(listener)
    }

    override fun removeClickListener(listener: INotificationClickListener) {
        Logging.debug("NotificationsManager.removeClickListener(listener: $listener)")
        _notificationLifecycleService.removeExternalClickListener(listener)
    }
}
