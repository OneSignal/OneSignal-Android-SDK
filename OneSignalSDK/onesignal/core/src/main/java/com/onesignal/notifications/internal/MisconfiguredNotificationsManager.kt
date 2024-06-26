package com.onesignal.notifications.internal

import com.onesignal.core.internal.minification.KeepStub
import com.onesignal.notifications.INotificationClickListener
import com.onesignal.notifications.INotificationLifecycleListener
import com.onesignal.notifications.INotificationsManager
import com.onesignal.notifications.IPermissionObserver

/**
 * The misconfigured NotificationsManager is an implementation of
 * [INotificationsManager] that warns the dev they have not included the
 * appropriate notifications module.
 */
@KeepStub
internal class MisconfiguredNotificationsManager : INotificationsManager {
    override val permission
        get() = throw EXCEPTION
    override val canRequestPermission
        get() = throw EXCEPTION

    override suspend fun requestPermission(fallbackToSettings: Boolean) = throw EXCEPTION

    override fun removeNotification(id: Int) = throw EXCEPTION

    override fun removeGroupedNotifications(group: String) = throw EXCEPTION

    override fun clearAllNotifications() = throw EXCEPTION

    override fun addPermissionObserver(observer: IPermissionObserver) = throw EXCEPTION

    override fun removePermissionObserver(observer: IPermissionObserver) = throw EXCEPTION

    override fun addForegroundLifecycleListener(listener: INotificationLifecycleListener) = throw EXCEPTION

    override fun removeForegroundLifecycleListener(listener: INotificationLifecycleListener) = throw EXCEPTION

    override fun addClickListener(listener: INotificationClickListener) = throw EXCEPTION

    override fun removeClickListener(listener: INotificationClickListener) = throw EXCEPTION

    companion object {
        private val EXCEPTION get() = Exception("Must include gradle module com.onesignal:Notification in order to use this functionality!")
    }
}
