package com.onesignal.notifications

import androidx.core.app.NotificationCompat

/**
 * An [INotification] that can be altered via a [NotificationCompat.Extender].
 */
interface IMutableNotification : INotification {
    /**
     * If a developer wants to override the data within a received notification, they can do so by
     * creating a [NotificationCompat.Extender] and override any low-level notification data desired.
     */
    fun setExtender(extender: NotificationCompat.Extender?)
}
