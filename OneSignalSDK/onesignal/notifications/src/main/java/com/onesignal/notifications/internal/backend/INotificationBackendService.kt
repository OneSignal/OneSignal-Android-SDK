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
     */
    suspend fun updateNotificationAsReceived(appId: String, notificationId: String, subscriptionId: String, deviceType: IDeviceService.DeviceType)

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
    suspend fun updateNotificationAsOpened(appId: String, notificationId: String, subscriptionId: String, deviceType: IDeviceService.DeviceType)
}
