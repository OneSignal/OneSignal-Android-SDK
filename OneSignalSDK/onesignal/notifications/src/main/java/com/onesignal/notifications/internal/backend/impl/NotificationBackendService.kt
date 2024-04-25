package com.onesignal.notifications.internal.backend.impl

import com.onesignal.common.exceptions.BackendException
import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.core.internal.http.IHttpClient
import com.onesignal.notifications.internal.backend.INotificationBackendService
import org.json.JSONObject

internal class NotificationBackendService(
    private val _httpClient: IHttpClient,
) : INotificationBackendService {
    override suspend fun updateNotificationAsReceived(
        appId: String,
        notificationId: String,
        subscriptionId: String,
        deviceType: IDeviceService.DeviceType,
    ) {
        val jsonBody: JSONObject =
            JSONObject()
                .put("app_id", appId)
                .put("player_id", subscriptionId)
                .put("device_type", deviceType.value)

        var response = _httpClient.put("notifications/$notificationId/report_received", jsonBody)

        if (!response.isSuccess) {
            throw BackendException(response.statusCode, response.payload, response.retryAfterSeconds)
        }
    }

    override suspend fun updateNotificationAsOpened(
        appId: String,
        notificationId: String,
        subscriptionId: String,
        deviceType: IDeviceService.DeviceType,
    ) {
        val jsonBody = JSONObject()
        jsonBody.put("app_id", appId)
        jsonBody.put("player_id", subscriptionId)
        jsonBody.put("opened", true)
        jsonBody.put("device_type", deviceType.value)

        var response = _httpClient.put("notifications/$notificationId", jsonBody)

        if (!response.isSuccess) {
            throw BackendException(response.statusCode, response.payload, response.retryAfterSeconds)
        }
    }
}
