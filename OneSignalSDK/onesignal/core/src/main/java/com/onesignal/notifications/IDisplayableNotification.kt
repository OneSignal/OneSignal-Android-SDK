package com.onesignal.notifications

/**
 * An [INotification] that has the ability to be manually displayed to the device.
 */
interface IDisplayableNotification : INotification {
    /**
     * Display the notification on the device.  Typically this is only possible within a short
     * time-frame (~30 seconds) after the notification has been received on the device.  See
     * [INotificationReceivedEvent] and [INotificationWillDisplayEvent] for more information
     * on how this might be used.
     */
    fun display()
}
