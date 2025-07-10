package com.onesignal.user.internal.customEvents.impl

import com.onesignal.common.exceptions.BackendException
import com.onesignal.core.internal.http.IHttpClient
import com.onesignal.core.internal.operations.ExecutionResponse
import com.onesignal.core.internal.operations.ExecutionResult

internal class CustomEventBackendService(private val _http: IHttpClient) :
    ICustomEventBackendService {
    override suspend fun sendCustomEvent(customEvent: CustomEvent): ExecutionResponse {
        val jsonObject = customEvent.toJSONObject()

        // TODO: include auth header when identity verification is on

        val response = _http.post("apps/${customEvent.appId}/integrations/sdk/custom_events", jsonObject)

        // TODO: handling failed response
        if (!response.isSuccess) {
            throw BackendException(response.statusCode, response.payload, response.retryAfterSeconds)
        }

        return ExecutionResponse(ExecutionResult.SUCCESS)
    }
}
