package com.onesignal.notifications.internal.backend

import com.onesignal.common.exceptions.BackendException
import com.onesignal.core.internal.device.IDeviceService

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
     * @param onesignalId The backend-assigned OneSignal ID of the user the subscription belonged to
     * when the notification was received, or null when the SDK did not have one. Omitted from the
     * request when null.
     */
    suspend fun updateNotificationAsReceived(
        appId: String,
        notificationId: String,
        subscriptionId: String,
        deviceType: IDeviceService.DeviceType,
        onesignalId: String?,
    )

    /**
     * Update the provided notification as opened by a specific subscription.
     *
     * If there is a non-successful response from the backend, a [BackendException] will be thrown with response data.
     *
     * @param appId The ID of the application that the notification was generated/received under.
     * @param notificationId The ID of the notification within the [appId] that has been received.
     * @param subscriptionId The specific subscription within the [appId] the notification has been received for.
     * @param deviceType The type of device the notification was received at.
     * @param onesignalId The backend-assigned OneSignal ID of the user the subscription belongs to,
     * or null when the SDK does not have one. Omitted from the request when null.
     */
    suspend fun updateNotificationAsOpened(
        appId: String,
        notificationId: String,
        subscriptionId: String,
        deviceType: IDeviceService.DeviceType,
        onesignalId: String?,
    )
}
