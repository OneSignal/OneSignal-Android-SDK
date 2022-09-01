package com.onesignal.notification

import androidx.core.app.NotificationCompat

/**
 * An [INotification] that can have it's displayed altered via a [NotificationCompat.Extender].
 */
interface IMutableNotification : INotification {
    /**
     * If a developer wants to override the data within a received notification, they can do so by
     * creating a [NotificationCompat.Extender] within their [IRemoteNotificationReceivedHandler]
     * or [INotificationWillShowInForegroundHandler] and override any notification data desired
     */
    fun setExtender(extender: NotificationCompat.Extender?)
}
