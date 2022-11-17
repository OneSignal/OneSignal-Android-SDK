package com.onesignal.session.internal.outcomes.impl

import com.onesignal.common.exceptions.BackendException
import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.core.internal.http.IHttpClient
import org.json.JSONObject

internal class OutcomeEventsBackendService(private val _http: IHttpClient) :
    IOutcomeEventsBackendService {

    override suspend fun sendOutcomeEvent(appId: String, deviceType: IDeviceService.DeviceType, direct: Boolean?, event: OutcomeEvent) {
        val jsonObject = JSONObject()
            .put("app_id", appId)
            .put("device_type", deviceType.value)

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
