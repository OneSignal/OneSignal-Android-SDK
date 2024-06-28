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
        Logging.debug("NotificationWillDisplayEvent.preventDefault($discard)")

        // If preventDefault(false) has already been called and it is now called again with
        // preventDefault(true), the waiter is fired to discard this notification.
        // This is necessary for wrapper SDKs that can call preventDefault(discard) twice.
        if (isPreventDefault && discard) {
            notification.displayWaiter.wake(false)
        }
        isPreventDefault = true
        this.discard = discard
    }
}
