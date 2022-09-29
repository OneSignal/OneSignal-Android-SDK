package com.onesignal.core.internal.outcomes.impl

import com.onesignal.core.internal.http.HttpResponse
import com.onesignal.core.internal.http.IHttpClient
import org.json.JSONObject

internal class OutcomeEventsBackend(private val _http: IHttpClient) : IOutcomeEventsBackend {

    override suspend fun sendOutcomeEvent(appId: String, deviceType: Int, direct: Boolean?, event: OutcomeEvent): HttpResponse {
        val jsonObject = JSONObject()
            .put("app_id", appId)
            .put("device_type", deviceType)

        if (direct != null) {
            jsonObject.put("direct", direct!!)
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

        return _http.post("outcomes/measure", jsonObject)
    }
}
