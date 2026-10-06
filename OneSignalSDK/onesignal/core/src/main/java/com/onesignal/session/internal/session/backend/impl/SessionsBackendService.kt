package com.onesignal.session.internal.session.backend.impl

import com.onesignal.common.safeJSONObject
import com.onesignal.common.safeString
import com.onesignal.core.internal.http.HttpResponse
import com.onesignal.core.internal.http.IHttpClient
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.session.internal.session.backend.CreateSessionRequest
import com.onesignal.session.internal.session.backend.ISessionsBackendService
import com.onesignal.session.internal.session.backend.SessionsApiResult
import com.onesignal.session.internal.session.backend.UpdateSessionRequest
import org.json.JSONException
import org.json.JSONObject

internal class SessionsBackendService(
    private val httpClient: IHttpClient,
) : ISessionsBackendService {
    override suspend fun createSession(
        appId: String,
        request: CreateSessionRequest,
    ): SessionsApiResult<String> {
        val body =
            JSONObject()
                .put("onesignal_id", request.onesignalId)
                .put("subscription_id", request.subscriptionId)
                .put("device_type", request.deviceType)
                .put("start_time", request.startTime)
                .put("idempotency_key", request.idempotencyKey)
        request.directAttributionId?.let { body.put("direct_attribution_id", it) }

        val response = httpClient.post("apps/$appId/sessions", body)
        if (!response.isSuccess) {
            return classifyFailure(response)
        }

        val sessionId = parseSessionId(response.payload)
        return if (sessionId.isNullOrEmpty()) {
            Logging.warn("SessionsBackendService: create session response is missing data.session_id")
            SessionsApiResult.Drop(response.statusCode)
        } else {
            SessionsApiResult.Success(sessionId)
        }
    }

    override suspend fun updateSession(
        appId: String,
        sessionId: String,
        request: UpdateSessionRequest,
    ): SessionsApiResult<Unit> {
        val body =
            JSONObject()
                .put("onesignal_id", request.onesignalId)
                .put("subscription_id", request.subscriptionId)
                .put("duration_seconds", request.durationSeconds)
                .put("idempotency_key", request.idempotencyKey)
        request.endTime?.let { body.put("end_time", it) }

        val response = httpClient.patch("apps/$appId/sessions/$sessionId", body)
        if (!response.isSuccess) {
            return classifyFailure(response)
        }
        return SessionsApiResult.Success(Unit)
    }

    private fun parseSessionId(payload: String?): String? =
        try {
            payload?.let { JSONObject(it) }?.safeJSONObject("data")?.safeString("session_id")
        } catch (e: JSONException) {
            Logging.warn("SessionsBackendService: unable to parse create session response", e)
            null
        }

    private fun classifyFailure(response: HttpResponse): SessionsApiResult<Nothing> {
        val code = response.statusCode
        // Status 0 means the request never got a response (network error, timeout).
        val retryable = code == 0 || code == HTTP_REQUEST_TIMEOUT || code == HTTP_TOO_MANY_REQUESTS || code >= HTTP_SERVER_ERROR
        return if (retryable) {
            SessionsApiResult.Retry(code, response.retryAfterSeconds)
        } else {
            SessionsApiResult.Drop(code)
        }
    }

    private companion object {
        const val HTTP_REQUEST_TIMEOUT = 408
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HTTP_SERVER_ERROR = 500
    }
}
