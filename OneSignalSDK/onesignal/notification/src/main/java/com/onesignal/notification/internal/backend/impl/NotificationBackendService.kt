package com.onesignal.notification.internal.backend.impl

import com.onesignal.common.exceptions.BackendException
import com.onesignal.core.internal.http.IHttpClient
import com.onesignal.notification.internal.backend.INotificationBackendService
import org.json.JSONObject

internal class NotificationBackendService(
    private val _httpClient: IHttpClient
) : INotificationBackendService {

    override suspend fun updateNotificationAsReceived(appId: String, notificationId: String, subscriptionId: String, deviceType: Int) {
        val jsonBody: JSONObject = JSONObject()
            .put("app_id", appId)
            .put("player_id", subscriptionId)
            .put("device_type", deviceType)

        var response = _httpClient.put("notifications/$notificationId/report_received", jsonBody)

        if (!response.isSuccess) {
            throw BackendException(response.statusCode, response.payload)
        }
    }

    override suspend fun updateNotificationAsOpened(appId: String, notificationId: String, subscriptionId: String, deviceType: Int) {
        val jsonBody = JSONObject()
        jsonBody.put("app_id", appId)
        jsonBody.put("player_id", subscriptionId)
        jsonBody.put("opened", true)
        jsonBody.put("device_type", deviceType)

        var response = _httpClient.put("notifications/$notificationId", jsonBody)

        if (!response.isSuccess) {
            throw BackendException(response.statusCode, response.payload)
        }
    }

    override suspend fun postNotification(appId: String, json: JSONObject): JSONObject {
        if (!json.has("app_id")) {
            json.put("app_id", appId)
        }

        var response = _httpClient.post("notifications/", json)

        if (!response.isSuccess) {
            throw BackendException(response.statusCode, response.payload)
        }

        return JSONObject(response.payload)
    }
}
