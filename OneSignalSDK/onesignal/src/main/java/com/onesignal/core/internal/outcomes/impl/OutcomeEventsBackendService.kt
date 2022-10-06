package com.onesignal.core.internal.outcomes.impl

import com.onesignal.core.internal.backend.BackendException
import com.onesignal.core.internal.http.IHttpClient
import org.json.JSONObject

internal class OutcomeEventsBackendService(private val _http: IHttpClient) : IOutcomeEventsBackendService {

    override suspend fun sendOutcomeEvent(appId: String, deviceType: Int, direct: Boolean?, event: OutcomeEvent) {
        val jsonObject = JSONObject()
            .put("app_id", appId)
            .put("device_type", deviceType)

        if (direct != null) {
            jsonObject.put("direct", direct)
        }

        if (event.notificationIds != null && event.notificationIds.length() > 0) {
            jsonObject.put("notification_ids", event.notificationIds)
        }

        jsonObject.put("id", event.name)
        if (event.weight > 0) {
            jsonObject.put("weight", event.weight)
        }

        if (event.timestamp > 0) {
            jsonObject.put("timestamp", event.timestamp)
        }

        val response = _http.post("outcomes/measure", jsonObject)

        if (!response.isSuccess) {
            throw BackendException(response.statusCode, response.payload)
        }
    }
}
