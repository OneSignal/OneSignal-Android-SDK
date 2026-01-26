package com.onesignal.user.internal.customEvents.impl

import com.onesignal.common.DateUtils
import com.onesignal.common.exceptions.BackendException
import com.onesignal.core.internal.http.IHttpClient
import com.onesignal.core.internal.operations.ExecutionResponse
import com.onesignal.core.internal.operations.ExecutionResult
import com.onesignal.user.internal.customEvents.ICustomEventBackendService
import org.json.JSONArray
import org.json.JSONObject
import java.util.TimeZone

internal class CustomEventBackendService(
    private val httpClient: IHttpClient,
) : ICustomEventBackendService {
    override suspend fun sendCustomEvent(
        appId: String,
        onesignalId: String,
        externalId: String?,
        timestamp: Long,
        eventName: String,
        eventProperties: String?,
        metadata: CustomEventMetadata,
    ): ExecutionResponse {
        val body = JSONObject()
        body.put("name", eventName)
        body.put("onesignal_id", onesignalId)
        externalId?.let { body.put("external_id", it) }
        body.put(
            "timestamp",
            DateUtils.iso8601Format().apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(
                timestamp,
            ),
        )

        val payload = eventProperties?.let { JSONObject(it) } ?: JSONObject()

        payload.put("os_sdk", metadata.toJSONObject())

        body.put("payload", payload)
        val jsonObject = JSONObject().put("events", JSONArray().put(body))

        val response = httpClient.post("apps/$appId/custom_events", jsonObject)

        if (!response.isSuccess) {
            throw BackendException(response.statusCode, response.payload, response.retryAfterSeconds)
        }

        return ExecutionResponse(ExecutionResult.SUCCESS)
    }
}
