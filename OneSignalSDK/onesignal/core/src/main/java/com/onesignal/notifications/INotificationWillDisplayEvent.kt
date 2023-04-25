package com.onesignal.notifications

/**
 * The event passed into [INotificationLifecycleListener.onWillDisplay], it provides access
 * to the notification to be displayed and the ability to change whether that notification will
 * be displayed to the user.
 *
 * The [notification] provided in this event is an [IDisplayableNotification], which allows the notification
 * to be displayed outside of the event callback. To display the notification outside of the event
 * callback (for instance, if you need to perform asynchronous processing to determine whether to
 * display the notification).
 *
 * ```
 * object : INotificationLifecycleListener {
 *   fun willDisplay(event: INotificationWillDisplayEvent) {
 *     event.preventDefault()
 *     thread {
 *       // do something async
 *
 *       // optionally, display the notification manually
 *       event.notification.display()
 *     }
 *   }
 * }
 * ```
 */
interface INotificationWillDisplayEvent {

    /**
     * The notification that has been received.  It is an [IDisplayableNotification] to
     * allow the user to call [IDisplayableNotification.display] in the event they also
     * call [preventDefault] but still want to display the notification outside of the
     * event callback.
     */
    val notification: IDisplayableNotification

    /**
     * Call this to prevent OneSignal from displaying the notification automatically. The
     * caller still has the option to display the notification by calling `notification.display()`.
     */
    fun preventDefault()
}
