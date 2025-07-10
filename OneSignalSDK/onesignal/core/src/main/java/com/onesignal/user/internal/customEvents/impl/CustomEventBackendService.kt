package com.onesignal.user.internal.customEvents.impl

import com.onesignal.common.DateUtils
import com.onesignal.common.exceptions.BackendException
import com.onesignal.core.internal.http.IHttpClient
import com.onesignal.core.internal.operations.ExecutionResponse
import com.onesignal.core.internal.operations.ExecutionResult
import com.onesignal.user.internal.customEvents.ICustomEventBackendService
import org.json.JSONArray
import org.json.JSONObject

internal class CustomEventBackendService(
    private val _httpClient: IHttpClient,
) : ICustomEventBackendService {
    override suspend fun sendCustomEvent(
        appId: String,
        onesignalId: String,
        externalId: String?,
        timestamp: Long,
        customEvent: CustomEvent,
        metadata: CustomEventMetadata,
    ): ExecutionResponse {
        val body = JSONObject()
        body.put("name", customEvent.name)
        body.put("app_id", appId)
        body.put("onesignal_id", onesignalId)
        externalId?.let { body.put("external_id", it) }
        body.put("timestamp", DateUtils.iso8601Format().format(timestamp))

        val payload = customEvent.propertiesJson
        payload.put("os_sdk", metadata.toJSONObject())

        body.put("payload", payload)
        val jsonObject = JSONObject().put("events", JSONArray().put(body))

        // TODO: include auth header when identity verification is on

        val response = _httpClient.post("apps/$appId/custom_events", jsonObject)

        if (!response.isSuccess) {
            throw BackendException(response.statusCode, response.payload, response.retryAfterSeconds)
        }

        return ExecutionResponse(ExecutionResult.SUCCESS)
    }
}
