package com.onesignal.session.internal.session.backend.impl

import com.onesignal.common.DateUtils
import com.onesignal.core.internal.http.HttpResponse
import com.onesignal.core.internal.http.IHttpClient
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.session.internal.session.backend.CreateSessionRequest
import com.onesignal.session.internal.session.backend.ISessionsBackendService
import com.onesignal.session.internal.session.backend.SessionsApiResult
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
    ): SessionsApiResult<String> {
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
            return classifyFailure(response)
        }

        val sessionId = parseSessionId(response.payload)
        return if (sessionId.isNullOrEmpty()) {
            // Retrying with the same idempotency key lets the backend return the session it already created.
            Logging.warn("SessionsBackendService: create session response is missing data.session_id")
            SessionsApiResult.Retry(response.statusCode, response.retryAfterSeconds)
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
        request.endTime?.let { body.put("end_time", toIso8601(it)) }

        val response = httpClient.patch("apps/$appId/sessions/$sessionId", body)
        if (!response.isSuccess) {
            return classifyFailure(response)
        }
        return SessionsApiResult.Success(Unit)
    }

    private fun toIso8601(epochSeconds: Long): String = DateUtils.iso8601Format().format(Date(epochSeconds * MILLIS_PER_SECOND))

    private fun parseSessionId(payload: String?): String? =
        try {
            payload?.let { JSONObject(it) }?.optJSONObject("data")?.opt("session_id") as? String
        } catch (e: JSONException) {
            Logging.warn("SessionsBackendService: unable to parse create session response", e)
            null
        }

    private fun classifyFailure(response: HttpResponse): SessionsApiResult<Nothing> {
        val code = response.statusCode
        // Non-positive codes mean no HTTP response: HttpClient returns -1 for network errors and 0 for timeouts or missing consent.
        val retryable = code <= 0 || code == HTTP_REQUEST_TIMEOUT || code == HTTP_TOO_MANY_REQUESTS || code >= HTTP_SERVER_ERROR
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
        const val MILLIS_PER_SECOND = 1000L
    }
}
