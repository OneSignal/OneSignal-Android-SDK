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
        Logging.debug("NotificationReceivedEvent.preventDefault($discard)")

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
