package com.onesignal.notifications.internal

import com.onesignal.debug.internal.logging.Logging
import com.onesignal.notifications.INotificationWillDisplayEvent

internal class NotificationWillDisplayEvent(
    override val notification: Notification,
) : INotificationWillDisplayEvent {
    var isPreventDefault: Boolean = false
    var discard: Boolean = false

    override fun preventDefault() {
        preventDefault(false)
    }

    override fun preventDefault(discard: Boolean) {
        Logging.debug("NotificationWillDisplayEvent.preventDefault()")
        isPreventDefault = true
        this.discard = discard
    }
}
