package com.onesignal.onesignal.notification

import androidx.core.app.NotificationCompat

interface IMutableNotification : INotification {
    /**
     * If a developer wants to override the data within a received notification, they can do so by
     * creating a [NotificationCompat.Extender] within the [OneSignal.OSRemoteNotificationReceivedHandler]
     * and override any notification data desired
     * <br></br><br></br>
     * @see OneSignal.OSRemoteNotificationReceivedHandler
     */
    fun setExtender(extender: NotificationCompat.Extender?)
}