package com.onesignal.notifications.internal

import com.onesignal.notifications.INotificationClickHandler
import com.onesignal.notifications.INotificationWillShowInForegroundHandler
import com.onesignal.notifications.INotificationsManager
import com.onesignal.notifications.IPermissionChangedHandler

/**
 * The misconfigured NotificationsManager is an implementation of [INotificationsManager] that warns the user they
 * have not included the appropriate notifications module.
 */
internal class MisconfiguredNotificationsManager : INotificationsManager {
    override val permission: Boolean
        get() = throw EXCEPTION

    override suspend fun requestPermission(fallbackToSettings: Boolean): Boolean = throw EXCEPTION
    override fun removeNotification(id: Int) = throw EXCEPTION
    override fun removeGroupedNotifications(group: String) = throw EXCEPTION
    override fun clearAllNotifications() = throw EXCEPTION
    override fun addPermissionChangedHandler(handler: IPermissionChangedHandler) = throw EXCEPTION
    override fun removePermissionChangedHandler(handler: IPermissionChangedHandler) = throw EXCEPTION
    override fun setNotificationWillShowInForegroundHandler(handler: INotificationWillShowInForegroundHandler?) = throw EXCEPTION
    override fun setNotificationClickHandler(handler: INotificationClickHandler?) = throw EXCEPTION

    companion object {
        private val EXCEPTION = Exception("Must include gradle module com.onesignal:Notification in order to use this functionality!")
    }
}
