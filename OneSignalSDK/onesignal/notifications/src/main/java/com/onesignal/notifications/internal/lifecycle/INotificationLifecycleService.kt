package com.onesignal.notifications.internal.lifecycle

import android.app.Activity
import com.onesignal.notifications.INotificationClickListener
import com.onesignal.notifications.INotificationLifecycleListener
import com.onesignal.notifications.INotificationReceivedEvent
import com.onesignal.notifications.INotificationServiceExtension
import com.onesignal.notifications.INotificationWillDisplayEvent
import com.onesignal.notifications.internal.common.NotificationGenerationJob
import org.json.JSONArray
import org.json.JSONObject

/**
 * Provides for both internal and external events and decision callbacks into the notification
 * lifecycle.  There are a total of 5 callbacks/handlers:
 *
 * Internal
 * --------
 * [INotificationLifecycleCallback]: Can be used by other modules to use push
 * notifications to drive their own processing, and prevent it from being received and/or opened by the user.
 *
 * External
 * --------
 * [INotificationServiceExtension]: Callback to indicate a notification has been received. Specified in the AndroidManifest.xml.
 * [INotificationLifecycleListener]: Callback to indicate a notification has been received while the app is in the foreground.
 * [INotificationClickListener]: Callback to indicate a notification has been opened.
 *
 * The order of events is as follows
 *  1. Notification is received by the notification module
 *  2. [INotificationLifecycleCallback.canReceiveNotification] To determine if the notification can be received by the notification module, or should be ignored.
 *  3. [INotificationServiceExtension.onNotificationReceived] To pre-process the notification, notification may be removed/changed. (Specified in AndroidManifest.xml).
 *  4. [INotificationLifecycleListener.onWillDisplay] To pre-process the notification while app in foreground, notification may be removed/changed.
 *  5. Process/Display the notification
 *  6. User "opens" or "dismisses" the notification
 *  7. [INotificationLifecycleCallback.canOpenNotification] To determine if the notification can be opened by the notification module, or should be ignored.
 *  8. [INotificationClickListener.onClick] To indicate the notification has been opened.
 */
interface INotificationLifecycleService {
    fun setInternalNotificationLifecycleCallback(callback: INotificationLifecycleCallback?)

    fun addExternalForegroundLifecycleListener(listener: INotificationLifecycleListener)

    fun removeExternalForegroundLifecycleListener(listener: INotificationLifecycleListener)

    fun addExternalClickListener(listener: INotificationClickListener)

    fun removeExternalClickListener(listener: INotificationClickListener)

    suspend fun canReceiveNotification(jsonPayload: JSONObject): Boolean

    fun externalRemoteNotificationReceived(notificationReceivedEvent: INotificationReceivedEvent)

    fun externalNotificationWillShowInForeground(willDisplayEvent: INotificationWillDisplayEvent)

    suspend fun notificationReceived(notificationJob: NotificationGenerationJob)

    suspend fun canOpenNotification(
        activity: Activity,
        data: JSONObject,
    ): Boolean

    suspend fun notificationOpened(
        activity: Activity,
        data: JSONArray,
    )
}
