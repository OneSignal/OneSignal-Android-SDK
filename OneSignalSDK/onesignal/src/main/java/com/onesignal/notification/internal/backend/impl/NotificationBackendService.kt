package com.onesignal.notification.internal.backend.impl

import com.onesignal.core.internal.backend.http.HttpResponse
import com.onesignal.core.internal.backend.http.IHttpClient
import com.onesignal.notification.internal.backend.INotificationBackendService
import org.json.JSONObject

internal class NotificationBackendService(
    private val _httpClient: IHttpClient
) : INotificationBackendService {

    override suspend fun updateNotificationAsReceived(appId: String, notificationId: String, subscriptionId: String, deviceType: Int): HttpResponse {
        val jsonBody: JSONObject = JSONObject()
            .put("app_id", appId)
            .put("player_id", subscriptionId)
            .put("device_type", deviceType)

        return _httpClient.put("notifications/$notificationId/report_received", jsonBody)
    }

    override suspend fun updateNotificationAsOpened(appId: String, notificationId: String, subscriptionId: String, deviceType: Int): HttpResponse {
        val jsonBody = JSONObject()
        jsonBody.put("app_id", appId)
        jsonBody.put("player_id", subscriptionId)
        jsonBody.put("opened", true)
        jsonBody.put("device_type", deviceType)
        return _httpClient.put("notifications/$notificationId", jsonBody)
    }

    override suspend fun postNotification(appId: String, json: JSONObject): HttpResponse {
        val jsonBody = JSONObject()
        jsonBody.put("app_id", appId)
        return _httpClient.post("notifications/", jsonBody)
    }
}
