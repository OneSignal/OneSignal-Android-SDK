package com.onesignal.notifications.internal.backend

import com.onesignal.common.exceptions.BackendException
import org.json.JSONObject

/**
 * This backend service provides access to the Notification endpoints
 */
internal interface INotificationBackendService {

    /**
     * Update the provided notification as received by a specific subscription.
     *
     * If there is a non-successful response from the backend, a [BackendException] will be thrown with response data.
     *
     * @param appId The ID of the application that the notification was generated/received under.
     * @param notificationId The ID of the notification within the [appId] that has been received.
     * @param subscriptionId The specific subscription within the [appId] the notification has been received for.
     * @param deviceType The type of device the notification was received at.
     */
    suspend fun updateNotificationAsReceived(appId: String, notificationId: String, subscriptionId: String, deviceType: Int)

    /**
     * Update the provided notification as opened by a specific subscription.
     *
     * If there is a non-successful response from the backend, a [BackendException] will be thrown with response data.
     *
     * @param appId The ID of the application that the notification was generated/received under.
     * @param notificationId The ID of the notification within the [appId] that has been received.
     * @param subscriptionId The specific subscription within the [appId] the notification has been received for.
     * @param deviceType The type of device the notification was received at.
     */
    suspend fun updateNotificationAsOpened(appId: String, notificationId: String, subscriptionId: String, deviceType: Int)

    /**
     * Send a notification using the provided payload.
     *
     * If there is a non-successful response from the backend, a [BackendException] will be thrown with response data.
     *
     * @param json The [JSONObject] payload containing the send notification details.
     *
     * @return The response to the request as a [JSONObject].
     */
    suspend fun postNotification(appId: String, json: JSONObject): JSONObject
}
