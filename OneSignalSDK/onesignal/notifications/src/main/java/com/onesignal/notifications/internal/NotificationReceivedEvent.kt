package com.onesignal.notifications.internal

import android.content.Context
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.notifications.INotificationReceivedEvent

internal class NotificationReceivedEvent(
    override val context: Context,
    override val notification: Notification,
) : INotificationReceivedEvent {
    var isPreventDefault: Boolean = false
    var discard: Boolean = false

    override fun preventDefault() {
        preventDefault(false)
    }

    override fun preventDefault(discard: Boolean) {
        Logging.debug("NotificationReceivedEvent.preventDefault()")
        isPreventDefault = true
        this.discard = discard
    }
}
