package com.onesignal.onesignal.notification.internal.backend

import com.onesignal.onesignal.core.internal.backend.http.HttpResponse
import org.json.JSONObject

/**
 * This backend service provides access to the Notification endpoints
 */
internal interface INotificationBackendService {

    /**
     * Update the provided notification as received by a specific subscription.
     *
     * @param appId The ID of the application that the notification was generated/received under.
     * @param notificationId The ID of the notification within the [appId] that has been received.
     * @param subscriptionId The specific subscription within the [appId] the notification has been received for.
     * @param deviceType The type of device the notification was received at.
     */
    suspend fun updateNotificationAsReceived(appId: String, notificationId: String, subscriptionId: String, deviceType: Int): HttpResponse

    suspend fun updateNotificationAsOpened(appId: String, notificationId: String, subscriptionId: String, deviceType: Int): HttpResponse

    suspend fun postNotification(appId: String, json: JSONObject): HttpResponse
}
