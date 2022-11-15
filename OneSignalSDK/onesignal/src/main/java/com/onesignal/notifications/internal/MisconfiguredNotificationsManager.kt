package com.onesignal.notifications.internal

import com.onesignal.notifications.INotificationOpenedHandler
import com.onesignal.notifications.INotificationWillShowInForegroundHandler
import com.onesignal.notifications.INotificationsManager
import com.onesignal.notifications.IPermissionChangedHandler
import org.json.JSONObject

/**
 * The misconfigured NotificationsManager is an implementation of [INotificationsManager] that warns the user they
 * have not included the appropriate notifications module.
 */
internal class MisconfiguredNotificationsManager : INotificationsManager {
    override val permission: Boolean
        get() = throw EXCEPTION

    override val canRequestPermission: Boolean
        get() = throw EXCEPTION

    override var unsubscribeWhenNotificationsAreDisabled: Boolean
        get() = throw EXCEPTION
        set(value) = throw EXCEPTION

    override suspend fun requestPermission(fallbackToSettings: Boolean): Boolean = throw EXCEPTION
    override suspend fun postNotification(json: JSONObject): JSONObject = throw EXCEPTION
    override suspend fun postNotification(json: String): JSONObject = throw EXCEPTION
    override suspend fun removeNotification(id: Int) = throw EXCEPTION
    override suspend fun removeGroupedNotifications(group: String) = throw EXCEPTION
    override suspend fun clearAllNotifications() = throw EXCEPTION
    override fun addPermissionChangedHandler(handler: IPermissionChangedHandler) = throw EXCEPTION
    override fun removePermissionChangedHandler(handler: IPermissionChangedHandler) = throw EXCEPTION
    override fun setNotificationWillShowInForegroundHandler(handler: INotificationWillShowInForegroundHandler) = throw EXCEPTION
    override fun setNotificationOpenedHandler(handler: INotificationOpenedHandler) = throw EXCEPTION

    companion object {
        private val EXCEPTION = Exception("Must include gradle module com.onesignal:Notification in order to use this functionality!")
    }
}
