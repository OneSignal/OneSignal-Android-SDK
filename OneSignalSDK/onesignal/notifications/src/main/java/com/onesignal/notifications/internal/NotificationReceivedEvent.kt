package com.onesignal.notifications.internal

import android.content.Context
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.notifications.INotificationReceivedEvent

internal class NotificationReceivedEvent(
    override val context: Context,
    override val notification: Notification,
) : INotificationReceivedEvent {

    var isPreventDefault: Boolean = false

    override fun preventDefault() {
        Logging.debug("NotificationReceivedEvent.preventDefault()")
        isPreventDefault = true
    }
}
