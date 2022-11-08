package com.onesignal.notifications.internal

import com.onesignal.notifications.INotificationOpenedHandler
import com.onesignal.notifications.INotificationWillShowInForegroundHandler
import com.onesignal.notifications.INotificationsManager
import com.onesignal.notifications.IPermissionChangedHandler
import com.onesignal.notifications.IPermissionState
import org.json.JSONObject

/**
 * The misconfigured NotificationsManager is an implementation of [INotificationsManager] that warns the user they
 * have not included the appropriate notifications module.
 */
internal class MisconfiguredNotificationsManager : INotificationsManager {
    override val permissionStatus: IPermissionState
        get() = throw EXCEPTION

    override var unsubscribeWhenNotificationsAreDisabled: Boolean
        get() = throw EXCEPTION
        set(value) = throw EXCEPTION

    override suspend fun requestPermission(): Boolean = throw EXCEPTION
    override suspend fun postNotification(json: JSONObject): JSONObject = throw EXCEPTION
    override suspend fun postNotification(json: String): JSONObject = throw EXCEPTION
    override suspend fun removeNotification(id: Int) = throw EXCEPTION
    override suspend fun removeGroupedNotifications(group: String) = throw EXCEPTION
    override suspend fun clearAll() = throw EXCEPTION
    override fun addPushPermissionHandler(handler: IPermissionChangedHandler) = throw EXCEPTION
    override fun removePushPermissionHandler(handler: IPermissionChangedHandler) = throw EXCEPTION
    override fun setNotificationWillShowInForegroundHandler(handler: INotificationWillShowInForegroundHandler) = throw EXCEPTION
    override fun setNotificationOpenedHandler(handler: INotificationOpenedHandler) = throw EXCEPTION

    companion object {
        private val EXCEPTION = Exception("Must include gradle module com.onesignal:Notification in order to use this functionality!")
    }
}