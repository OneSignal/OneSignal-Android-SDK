package com.onesignal.notifications

import android.content.Context

/**
 * The event passed into both [INotificationServiceExtension.onNotificationReceived].
 * It provides access to the received notification and the ability to change how (or whether) that
 * notification will be displayed to the user.
 *
 * The [notification] provided in this event is both an [IMutableNotification], which allows
 * the notification to be altered, and [IDisplayableNotification], which allows the notification
 * to be displayed outside of the event callback.
 *
 * To display the notification outside of the event callback (for instance, if you need to perform
 * asynchronous processing to determine whether to display the notification).
 *
 * ```
 * object : IRemoteNotificationReceivedHandler {
 *   fun remoteNotificationReceived(event: IRemoteNotificationReceivedEvent) {
 *     event.preventDefault()
 *     thread {
 *       // do something async
 *
 *       // optionally, change the display using a native Android Extender
 *       event.notification.setExtender(androidx.core.app.NotificationCompat.Extender {
 *         // alter the notification, for example to change the title
 *         it.setContentTitle("CUSTOM TITLE")
 *       })
 *
 *       // optionally, display the notification manually
 *       event.notification.display()
 *     }
 *   }
 * }
 * ```
 */
interface INotificationReceivedEvent {
    /**
     * The Android context the notification was received under.
     */
    val context: Context

    /**
     * The notification that has been received.
     */
    val notification: IDisplayableMutableNotification

    /**
     * Whether your app already received this notification and OneSignal is handing it back. The
     * usual cause is Android clearing the notification shade, such as after a reboot or an app
     * update. It also happens when a group of notifications drops to a single one, which OneSignal
     * rebuilds so it no longer renders inside a summary.
     *
     * Use it to skip work that should only happen once, such as counting the notification in your
     * analytics. Keep calling `notification.setExtender(...)` even when this is true, otherwise a
     * rebuilt notification loses your customizations.
     *
     * To drop the notification instead of showing it again, call `preventDefault(true)`. That marks
     * it dismissed so OneSignal stops restoring it. The no-argument [preventDefault] is for the
     * asynchronous `notification.display()` flow and waits up to 30 seconds before giving up.
     *
     * Defaulted rather than abstract so that existing implementations outside this SDK still compile.
     */
    val restoring: Boolean
        get() = false

    /**
     * Call this to prevent OneSignal from displaying the notification automatically. The notification
     * can still be manually displayed using `notification.display()`.
     */
    fun preventDefault()

    /**
     * Call this to prevent OneSignal from displaying the notification automatically.
     * This method can be called up to two times with false and then true, if processing time is needed.
     * Typically this is only possible within a short
     * time-frame (~30 seconds) after the notification is received on the device.
     * @param discard an [preventDefault] set to true to dismiss the notification with no
     * possibility of displaying it in the future.
     */
    fun preventDefault(discard: Boolean)
}
