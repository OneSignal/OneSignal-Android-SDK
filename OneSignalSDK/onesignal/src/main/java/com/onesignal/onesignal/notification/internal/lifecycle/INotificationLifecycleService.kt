package com.onesignal.onesignal.notification.internal.lifecycle

import android.app.Activity
import android.content.Context
import com.onesignal.onesignal.notification.*
import com.onesignal.onesignal.notification.internal.common.NotificationGenerationJob
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
 * [INotificationLifecycleEventHandler]: Used to hook into the received/opened events of a notification.
 *
 * External
 * --------
 * [IRemoteNotificationReceivedHandler]: Callback to indicate a notification has been received. Specified in the AndroidManifest.xml.
 * [INotificationWillShowInForegroundHandler]: Callback to indicate a notification has been received while the app is in the foreground.
 * [INotificationOpenedHandler]: Callback to indicate a notification has been opened.
 *
 * The order of events is as follows
 *  1. Notification is received by the notification module
 *  2. [INotificationLifecycleCallback.canReceiveNotification] To determine if the notification can be received by the notification module, or should be ignored.
 *  3. [IRemoteNotificationReceivedHandler.remoteNotificationReceived] To pre-process the notification, notification may be removed/changed. (Specified in AndroidManifest.xml).
 *  4. [INotificationWillShowInForegroundHandler.notificationWillShowInForeground] To pre-process the notification while app in foreground, notification may be removed/changed.
 *  5. Process/Display the notification
 *  6. [INotificationLifecycleEventHandler.onNotificationReceived] To indicate the notification has been received and processed.
 *  7. User "opens" or "dismisses" the notification
 *  8. [INotificationLifecycleCallback.canOpenNotification] To determine if the notification can be opened by the notification module, or should be ignored.
 *  9. [INotificationLifecycleEventHandler.onNotificationOpened] To indicate the notification has been opened.
 * 10. [INotificationOpenedHandler.notificationOpened] To indicate the notification has been opened.
 */
interface INotificationLifecycleService {
    fun addInternalNotificationLifecycleEventHandler(handler: INotificationLifecycleEventHandler)
    fun removeInternalNotificationLifecycleEventHandler(handler: INotificationLifecycleEventHandler)
    fun setInternalNotificationLifecycleCallback(callback: INotificationLifecycleCallback?)
    fun setExternalWillShowInForegroundHandler(handler: INotificationWillShowInForegroundHandler?)
    fun setExternalNotificationOpenedHandler(handler: INotificationOpenedHandler?)


    suspend fun canReceiveNotification(jsonPayload: JSONObject) : Boolean
    fun externalRemoteNotificationReceived(context: Context, notificationReceivedEvent: INotificationReceivedEvent)
    fun externalNotificationWillShowInForeground(notificationReceivedEvent: INotificationReceivedEvent)
    suspend fun notificationReceived(notificationJob: NotificationGenerationJob)
    suspend fun canOpenNotification(activity: Activity, data: JSONObject) : Boolean
    suspend fun notificationOpened(activity: Activity, data: JSONArray, notificationId: String)
}