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
     * Call this to prevent OneSignal from displaying the notification automatically. The notification
     * can still be manually displayed using `notification.display()`.
     */
    fun preventDefault()
}
