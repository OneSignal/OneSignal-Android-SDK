package com.onesignal.onesignal.iam.internal.backend

import org.json.JSONException
import com.onesignal.onesignal.core.internal.backend.http.HttpResponse
import com.onesignal.onesignal.core.internal.backend.http.IHttpClient
import com.onesignal.onesignal.core.internal.common.NetworkUtils
import com.onesignal.onesignal.core.internal.logging.Logging
import com.onesignal.onesignal.iam.internal.preferences.InAppPreferencesController
import org.json.JSONObject

internal class InAppBackendController(
    private val _httpClient: IHttpClient,
    private val _prefs: InAppPreferencesController
) {
    private var htmlNetworkRequestAttemptCount = 0

    suspend fun sendIAMClick(
        appId: String?,
        userId: String?,
        variantId: String?,
        deviceType: Int,
        messageId: String,
        clickId: String?,
        isFirstClick: Boolean,
        clickedMessagesId: Set<String>
    ) : HttpResponse {

        val json: JSONObject = object : JSONObject() {
            init {
                put("app_id", appId)
                put("device_type", deviceType)
                put("player_id", userId)
                put("click_id", clickId)
                put("variant_id", variantId)
                if (isFirstClick) put("first_click", true)
            }
        }

        val response = _httpClient.post("in_app_messages/$messageId/click", json)

        if (response.isSuccess) {
            printHttpSuccessForInAppMessageRequest("engagement", response.payload!!)
            // Persist success click to disk. Id already added to set before making the network call
            _prefs.clickedMessagesId = clickedMessagesId
        } else {
            printHttpErrorForInAppMessageRequest(
                "engagement",
                response.statusCode,
                response.payload
            )
        }

        return response
    }

    suspend fun sendIAMPageImpression(
        appId: String?,
        userId: String?,
        variantId: String?,
        deviceType: Int,
        messageId: String,
        pageId: String?,
        viewedPageIds: Set<String>?
    ) : HttpResponse {
        val json: JSONObject = object : JSONObject() {
            init {
                put("app_id", appId)
                put("player_id", userId)
                put("variant_id", variantId)
                put("device_type", deviceType)
                put("page_id", pageId)
            }
        }

        val response = _httpClient.post("in_app_messages/$messageId/pageImpression", json)

        if(response.isSuccess) {
            printHttpSuccessForInAppMessageRequest("page impression", response.payload!!)
            _prefs.viewPageImpressionedIds = viewedPageIds
        }
        else {
            printHttpErrorForInAppMessageRequest("page impression", response.statusCode, response.payload)
        }

        return response
    }

    suspend fun sendIAMImpression(
        appId: String?,
        subscriptionId: String?,
        variantId: String?,
        deviceType: Int,
        messageId: String,
        impressionedMessages: Set<String>
    ) : HttpResponse {
        val json: JSONObject = object : JSONObject() {
            init {
                put("app_id", appId)
                put("player_id", subscriptionId)
                put("variant_id", variantId)
                put("device_type", deviceType)
                put("first_impression", true)
            }
        }

        val response = _httpClient.post("in_app_messages/$messageId/impression", json)

        if(response.isSuccess) {
            printHttpSuccessForInAppMessageRequest("impression", response.payload!!)
            _prefs.impressionesMessagesId = impressionedMessages
        }
        else {
            printHttpErrorForInAppMessageRequest("impression", response.statusCode, response.payload)
        }

        return response
    }

    suspend fun getIAMPreviewData(appId: String, previewUUID: String) : HttpResponse {
        val htmlPath = "in_app_messages/device_preview?preview_id=$previewUUID&app_id=$appId"

        val response = _httpClient.get(htmlPath, null)

        if(!response.isSuccess) {
            printHttpErrorForInAppMessageRequest("html", response.statusCode, response.payload)
        }

        return response
    }

    suspend fun getIAMData(appId: String, messageId: String, variantId: String?) : HttpResponse {

        val htmlPath = htmlPathForMessage(messageId, variantId, appId)
            ?: throw Exception("variantId not valid: $variantId")

        val response = _httpClient.get(htmlPath, null)

        if(response.isSuccess) {
            // Successful request, reset count
            htmlNetworkRequestAttemptCount = 0
        }
        else {
            printHttpErrorForInAppMessageRequest("html", response.statusCode, response.payload)
            val jsonObject = JSONObject()
            if (!NetworkUtils.shouldRetryNetworkRequest(response.statusCode) || htmlNetworkRequestAttemptCount >= NetworkUtils.MAX_NETWORK_REQUEST_ATTEMPT_COUNT) {
                // Failure limit reached, reset
                htmlNetworkRequestAttemptCount = 0
                try {
                    jsonObject.put(IAM_DATA_RESPONSE_RETRY_KEY, false)
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            } else {
                // Failure limit not reached, increment by 1
                htmlNetworkRequestAttemptCount++
                try {
                    jsonObject.put(IAM_DATA_RESPONSE_RETRY_KEY, true)
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
        }

        return response
    }

    private fun htmlPathForMessage(messageId: String, variantId: String?, appId: String): String? {
        if (variantId == null) {
            Logging.error("Unable to find a variant for in-app message $messageId")
            return null
        }

        return "in_app_messages/$messageId/variants/$variantId/html?app_id=$appId"
    }

    private fun printHttpSuccessForInAppMessageRequest(requestType: String, response: String) {
        Logging.debug("Successful post for in-app message $requestType request: $response")
    }

    private fun printHttpErrorForInAppMessageRequest(
        requestType: String,
        statusCode: Int,
        response: String?
    ) {
        Logging.error("Encountered a $statusCode error while attempting in-app message $requestType request: $response")
    }

    companion object {
        const val IAM_DATA_RESPONSE_RETRY_KEY = "retry"
    }
}