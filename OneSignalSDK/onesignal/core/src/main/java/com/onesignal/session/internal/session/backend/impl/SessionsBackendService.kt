package com.onesignal.session.internal.session.backend.impl

import com.onesignal.common.DateUtils
import com.onesignal.common.exceptions.BackendException
import com.onesignal.core.internal.http.IHttpClient
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.session.internal.session.backend.CreateSessionRequest
import com.onesignal.session.internal.session.backend.ISessionsBackendService
import com.onesignal.session.internal.session.backend.UpdateSessionRequest
import org.json.JSONException
import org.json.JSONObject
import java.util.Date

internal class SessionsBackendService(
    private val httpClient: IHttpClient,
) : ISessionsBackendService {
    override suspend fun createSession(
        appId: String,
        request: CreateSessionRequest,
    ): String? {
        val body =
            JSONObject()
                .put("onesignal_id", request.onesignalId)
                .put("subscription_id", request.subscriptionId)
                .put("device_type", request.deviceType)
                .put("start_time", toIso8601(request.startTime))
                .put("idempotency_key", request.idempotencyKey)
        request.directAttributionId?.let { body.put("direct_attribution_id", it) }

        val response = httpClient.post("apps/$appId/sessions", body)
        if (!response.isSuccess) {
            throw BackendException(response.statusCode, response.payload, response.retryAfterSeconds)
        }

        return parseSessionId(response.payload)
    }

    override suspend fun updateSession(
        appId: String,
        sessionId: String,
        request: UpdateSessionRequest,
    ) {
        val body =
            JSONObject()
                .put("onesignal_id", request.onesignalId)
                .put("subscription_id", request.subscriptionId)
                .put("duration_seconds", request.durationSeconds)
                .put("idempotency_key", request.idempotencyKey)
        request.endTime?.let { body.put("end_time", toIso8601(it)) }

        val response = httpClient.patch("apps/$appId/sessions/$sessionId", body)
        if (!response.isSuccess) {
            throw BackendException(response.statusCode, response.payload, response.retryAfterSeconds)
        }
    }

    private fun toIso8601(epochSeconds: Long): String = DateUtils.iso8601Format().format(Date(epochSeconds * MILLIS_PER_SECOND))

    private fun parseSessionId(payload: String?): String? =
        try {
            val sessionId = payload?.let { JSONObject(it) }?.optJSONObject("data")?.opt("session_id") as? String
            sessionId?.takeIf { it.isNotEmpty() }
        } catch (e: JSONException) {
            Logging.warn("SessionsBackendService: unable to parse create session response", e)
            null
        }

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}
