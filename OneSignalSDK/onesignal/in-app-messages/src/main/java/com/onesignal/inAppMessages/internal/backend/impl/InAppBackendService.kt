package com.onesignal.inAppMessages.internal.backend.impl

import com.onesignal.common.NetworkUtils
import com.onesignal.common.exceptions.BackendException
import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.core.internal.http.IHttpClient
import com.onesignal.core.internal.http.impl.OptionalHeaders
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.inAppMessages.internal.InAppMessage
import com.onesignal.inAppMessages.internal.InAppMessageContent
import com.onesignal.inAppMessages.internal.backend.GetIAMDataResponse
import com.onesignal.inAppMessages.internal.backend.IInAppBackendService
import com.onesignal.inAppMessages.internal.hydrators.InAppHydrator
import kotlinx.coroutines.delay
import org.json.JSONObject

private const val DEFAULT_RETRY_LIMIT = 3
private const val DEFAULT_RETRY_AFTER_SECONDS = 1

internal class InAppBackendService(
    private val _httpClient: IHttpClient,
    private val _deviceService: IDeviceService,
    private val _hydrator: InAppHydrator,
) : IInAppBackendService {
    private var htmlNetworkRequestAttemptCount = 0

    override suspend fun listInAppMessages(
        appId: String,
        subscriptionId: String,
        offset: Long,
        sessionDurationProvider: () -> Long,
    ): List<InAppMessage>? {
        val baseUrl = "apps/$appId/subscriptions/$subscriptionId/iams"
        return attemptFetchWithRetries(baseUrl, offset, sessionDurationProvider)
    }

    override suspend fun getIAMData(
        appId: String,
        messageId: String,
        variantId: String?,
    ): GetIAMDataResponse {
        val htmlPath =
            htmlPathForMessage(messageId, variantId, appId)
                ?: return GetIAMDataResponse(null, false)

        val response = _httpClient.get(htmlPath)

        if (response.isSuccess) {
            // Successful request, reset count
            htmlNetworkRequestAttemptCount = 0
            val jsonResponse = JSONObject(response.payload!!)
            return GetIAMDataResponse(_hydrator.hydrateIAMMessageContent(jsonResponse), false)
        } else {
            printHttpErrorForInAppMessageRequest("html", response.statusCode, response.payload)

            return if (NetworkUtils.getResponseStatusType(response.statusCode) != NetworkUtils.ResponseStatusType.RETRYABLE ||
                htmlNetworkRequestAttemptCount >= NetworkUtils.maxNetworkRequestAttemptCount
            ) {
                // Failure limit reached, reset
                htmlNetworkRequestAttemptCount = 0
                GetIAMDataResponse(null, false)
            } else {
                // Failure limit not reached, increment by 1
                htmlNetworkRequestAttemptCount++
                GetIAMDataResponse(null, true)
            }
        }
    }

    override suspend fun getIAMPreviewData(
        appId: String,
        previewUUID: String,
    ): InAppMessageContent? {
        val htmlPath = "in_app_messages/device_preview?preview_id=$previewUUID&app_id=$appId"

        val response = _httpClient.get(htmlPath)

        return if (response.isSuccess) {
            val jsonResponse = JSONObject(response.payload!!)
            _hydrator.hydrateIAMMessageContent(jsonResponse)
        } else {
            printHttpErrorForInAppMessageRequest("html", response.statusCode, response.payload)
            null
        }
    }

    override suspend fun sendIAMClick(
        appId: String,
        subscriptionId: String,
        variantId: String?,
        messageId: String,
        clickId: String?,
        isFirstClick: Boolean,
    ) {
        val json: JSONObject =
            object : JSONObject() {
                init {
                    put("app_id", appId)
                    put("device_type", _deviceService.deviceType.value)
                    put("player_id", subscriptionId)
                    put("click_id", clickId)
                    put("variant_id", variantId)
                    if (isFirstClick) put("first_click", true)
                }
            }

        val response = _httpClient.post("in_app_messages/$messageId/click", json)

        if (response.isSuccess) {
            printHttpSuccessForInAppMessageRequest("engagement", response.payload!!)
        } else {
            printHttpErrorForInAppMessageRequest(
                "engagement",
                response.statusCode,
                response.payload,
            )

            throw BackendException(response.statusCode, response.payload, response.retryAfterSeconds)
        }
    }

    override suspend fun sendIAMPageImpression(
        appId: String,
        subscriptionId: String,
        variantId: String?,
        messageId: String,
        pageId: String?,
    ) {
        val json: JSONObject =
            object : JSONObject() {
                init {
                    put("app_id", appId)
                    put("player_id", subscriptionId)
                    put("variant_id", variantId)
                    put("device_type", _deviceService.deviceType.value)
                    put("page_id", pageId)
                }
            }

        val response = _httpClient.post("in_app_messages/$messageId/pageImpression", json)

        if (response.isSuccess) {
            printHttpSuccessForInAppMessageRequest("page impression", response.payload!!)
        } else {
            printHttpErrorForInAppMessageRequest("page impression", response.statusCode, response.payload)
            throw BackendException(response.statusCode, response.payload, response.retryAfterSeconds)
        }
    }

    override suspend fun sendIAMImpression(
        appId: String,
        subscriptionId: String,
        variantId: String?,
        messageId: String,
    ) {
        val json: JSONObject =
            object : JSONObject() {
                init {
                    put("app_id", appId)
                    put("player_id", subscriptionId)
                    put("variant_id", variantId)
                    put("device_type", _deviceService.deviceType.value)
                    put("first_impression", true)
                }
            }

        val response = _httpClient.post("in_app_messages/$messageId/impression", json)

        if (response.isSuccess) {
            printHttpSuccessForInAppMessageRequest("impression", response.payload!!)
        } else {
            printHttpErrorForInAppMessageRequest("impression", response.statusCode, response.payload)
            throw BackendException(response.statusCode, response.payload, response.retryAfterSeconds)
        }
    }

    private fun htmlPathForMessage(
        messageId: String,
        variantId: String?,
        appId: String,
    ): String? {
        if (variantId == null) {
            Logging.error("Unable to find a variant for in-app message $messageId")
            return null
        }

        return "in_app_messages/$messageId/variants/$variantId/html?app_id=$appId"
    }

    private fun printHttpSuccessForInAppMessageRequest(
        requestType: String,
        response: String,
    ) {
        Logging.debug("Successful post for in-app message $requestType request: $response")
    }

    private fun printHttpErrorForInAppMessageRequest(
        requestType: String,
        statusCode: Int,
        response: String?,
    ) {
        Logging.error("Encountered a $statusCode error while attempting in-app message $requestType request: $response")
    }

    private suspend fun attemptFetchWithRetries(
        baseUrl: String,
        offset: Long,
        sessionDurationProvider: () -> Long,
    ): List<InAppMessage>? {
        var attempts = 1
        var delayTime = 1 // Start with a 1-second delay for exponential backoff
        var retryLimit = DEFAULT_RETRY_LIMIT

        while (attempts <= retryLimit + 1) {
            val retryCount = if (attempts > 1) attempts - 1 else null
            val values =
                OptionalHeaders(
                    offset = offset,
                    sessionDuration = sessionDurationProvider(),
                    retryCount = retryCount,
                )
            val response = _httpClient.get(baseUrl, values)

            if (response.isSuccess) {
                val jsonResponse = response.payload?.let { JSONObject(it) }
                return jsonResponse?.let { hydrateInAppMessages(it) }
            } else if (response.statusCode == 425) { // 425 Too Early
                if (response.retryLimit != null) {
                    retryLimit = response.retryLimit!!
                }
                val retryAfter = response.retryAfterSeconds ?: DEFAULT_RETRY_AFTER_SECONDS
                delay(retryAfter * 1_000L)
            } else if (response.statusCode == 429) { // 429 Too Many Requests
                val retryAfter = response.retryAfterSeconds ?: delayTime

                delay(retryAfter * 1_000L)
                delayTime *= 2 // Exponential backoff
            } else if (response.statusCode >= 500) {
                delay(delayTime * 1_000L)
                delayTime *= 2 // Exponential backoff
            } else {
                return null
            }

            attempts++
        }

        // If all retries fail, make a final attempt without the offset. This will tell the server,
        // we give up, just give me the IAMs without first ensuring data consistency
        return fetchInAppMessagesWithoutOffset(baseUrl, sessionDurationProvider)
    }

    private suspend fun fetchInAppMessagesWithoutOffset(
        url: String,
        sessionDurationProvider: () -> Long,
    ): List<InAppMessage>? {
        val response =
            _httpClient.get(
                url,
                OptionalHeaders(
                    sessionDuration = sessionDurationProvider(),
                    offset = 0,
                ),
            )

        if (response.isSuccess) {
            val jsonResponse = response.payload?.let { JSONObject(it) }
            return jsonResponse?.let { hydrateInAppMessages(it) }
        } else {
            return null
        }
    }

    private fun hydrateInAppMessages(jsonResponse: JSONObject): List<InAppMessage>? =
        if (jsonResponse.has("in_app_messages")) {
            val iamMessagesAsJSON = jsonResponse.getJSONArray("in_app_messages")
            _hydrator.hydrateIAMMessages(iamMessagesAsJSON)
        } else {
            null
        }
}
