package com.onesignal.inAppMessages.internal.backend.impl

import com.onesignal.common.NetworkUtils
import com.onesignal.common.exceptions.BackendException
import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.core.internal.http.IHttpClient
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.inAppMessages.internal.InAppMessage
import com.onesignal.inAppMessages.internal.InAppMessageContent
import com.onesignal.inAppMessages.internal.backend.GetIAMDataResponse
import com.onesignal.inAppMessages.internal.backend.IInAppBackendService
import com.onesignal.inAppMessages.internal.hydrators.InAppHydrator
import kotlinx.coroutines.delay
import org.json.JSONObject

internal class InAppBackendService(
    private val _httpClient: IHttpClient,
    private val _deviceService: IDeviceService,
    private val _hydrator: InAppHydrator,
) : IInAppBackendService {
    private var htmlNetworkRequestAttemptCount = 0

    override suspend fun listInAppMessages(
        appId: String,
        subscriptionId: String,
        offset: Long?,
    ): List<InAppMessage>? {
        val baseUrl = "apps/$appId/subscriptions/$subscriptionId/iams"

        return if (offset != null) {
            attemptFetchWithRetries(baseUrl, offset)
        } else {
            fetchInAppMessages(baseUrl)
        }
    }

    override suspend fun getIAMData(
        appId: String,
        messageId: String,
        variantId: String?,
    ): GetIAMDataResponse {
        val htmlPath =
            htmlPathForMessage(messageId, variantId, appId)
                ?: return GetIAMDataResponse(null, false)

        val response = _httpClient.get(htmlPath, null)

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

        val response = _httpClient.get(htmlPath, null)

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

    private suspend fun attemptFetchWithRetries(baseUrl: String, offset: Long): List<InAppMessage>? {
        var attempts = 1

        while (true) {
            val urlWithOffset = "$baseUrl?offset=$offset"
            val response = _httpClient.get(urlWithOffset)

            if (response.isSuccess) {
                val jsonResponse = response.payload?.let { JSONObject(it) }
                return jsonResponse?.let { hydrateInAppMessages(it) }
            } else if (response.statusCode == 429) { // 429 Too Many Requests
                val retryLimit = response.retryLimit ?: 3
                val retryAfter = response.retryAfterSeconds ?: 1

                if (attempts >= retryLimit) {
                    break
                }

                delay(retryAfter * 1000L) // Convert seconds to milliseconds
            } else {
                return null
            }

            attempts++
        }

        // If all retries fail, make a final attempt without the offset. This will tell the server,
        // we give up, just give me the IAMs without first ensuring data consistency
        return fetchInAppMessages(baseUrl)
    }

    private suspend fun fetchInAppMessages(url: String): List<InAppMessage>? {
        val response = _httpClient.get(url)

        if (response.isSuccess) {
            val jsonResponse = response.payload?.let { JSONObject(it) }
            return jsonResponse?.let { hydrateInAppMessages(it) }
        } else {
            return null
        }
    }

    private fun hydrateInAppMessages(jsonResponse: JSONObject): List<InAppMessage>? {
        return if (jsonResponse.has("in_app_messages")) {
            val iamMessagesAsJSON = jsonResponse.getJSONArray("in_app_messages")
            // TODO: Outstanding question on whether we still want to cache this.  Only used when
            //       hard start of the app, but within 30 seconds of it being killed (i.e. same session startup).
            // Cache copy for quick cold starts
            // _prefs.savedIAMs = iamMessagesAsJSON.toString()
            _hydrator.hydrateIAMMessages(iamMessagesAsJSON)
        } else {
            null
        }
    }
}
