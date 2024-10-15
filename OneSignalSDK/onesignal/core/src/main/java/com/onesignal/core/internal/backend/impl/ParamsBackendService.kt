package com.onesignal.core.internal.backend.impl

import com.onesignal.common.IDManager
import com.onesignal.common.exceptions.BackendException
import com.onesignal.common.expandJSONObject
import com.onesignal.common.safeBool
import com.onesignal.common.safeInt
import com.onesignal.common.safeLong
import com.onesignal.common.safeString
import com.onesignal.core.internal.backend.FCMParamsObject
import com.onesignal.core.internal.backend.IParamsBackendService
import com.onesignal.core.internal.backend.InfluenceParamsObject
import com.onesignal.core.internal.backend.ParamsObject
import com.onesignal.core.internal.http.CacheKeys
import com.onesignal.core.internal.http.IHttpClient
import com.onesignal.core.internal.http.impl.OptionalHeaders
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import org.json.JSONObject

internal class ParamsBackendService(
    private val _http: IHttpClient,
) : IParamsBackendService {
    override suspend fun fetchParams(
        appId: String,
        subscriptionId: String?,
    ): ParamsObject {
        Logging.log(LogLevel.DEBUG, "ParamsBackendService.fetchParams(appId: $appId, subscriptionId: $subscriptionId)")

        var paramsUrl = "apps/$appId/android_params.js"
        if (subscriptionId != null && !IDManager.isLocalId(subscriptionId)) {
            paramsUrl += "?player_id=$subscriptionId"
        }

        val response = _http.get(paramsUrl, OptionalHeaders(cacheKey = CacheKeys.REMOTE_PARAMS))

        if (!response.isSuccess) {
            throw BackendException(response.statusCode, response.payload, response.retryAfterSeconds)
        }

        val responseJson = JSONObject(response.payload!!)

        // Process outcomes params
        var influenceParams: InfluenceParamsObject? = null
        responseJson.expandJSONObject("outcomes") {
            influenceParams = processOutcomeJson(it)
        }

        // Process FCM params
        var fcmParams: FCMParamsObject? = null
        responseJson.expandJSONObject("fcm") {
            fcmParams =
                FCMParamsObject(
                    apiKey = it.safeString("api_key"),
                    appId = it.safeString("app_id"),
                    projectId = it.safeString("project_id"),
                )
        }

        return ParamsObject(
            googleProjectNumber = responseJson.safeString("android_sender_id"),
            enterprise = responseJson.safeBool("enterp"),
            // TODO: New
            useIdentityVerification = responseJson.safeBool("jwt_required"),
            notificationChannels = responseJson.optJSONArray("chnl_lst"),
            firebaseAnalytics = responseJson.safeBool("fba"),
            restoreTTLFilter = responseJson.safeBool("restore_ttl_filter"),
            clearGroupOnSummaryClick = responseJson.safeBool("clear_group_on_summary_click"),
            receiveReceiptEnabled = responseJson.safeBool("receive_receipts_enable"),
            disableGMSMissingPrompt = responseJson.safeBool("disable_gms_missing_prompt"),
            unsubscribeWhenNotificationsDisabled = responseJson.safeBool("unsubscribe_on_notifications_disabled"),
            locationShared = responseJson.safeBool("location_shared"),
            requiresUserPrivacyConsent = responseJson.safeBool("requires_user_privacy_consent"),
            // TODO: New
            opRepoExecutionInterval = responseJson.safeLong("oprepo_execution_interval"),
            influenceParams = influenceParams ?: InfluenceParamsObject(),
            fcmParams = fcmParams ?: FCMParamsObject(),
        )
    }

    private fun processOutcomeJson(outcomeJson: JSONObject): InfluenceParamsObject {
        var indirectNotificationAttributionWindow: Int? = null
        var notificationLimit: Int? = null
        var indirectIAMAttributionWindow: Int? = null
        var iamLimit: Int? = null
        var isDirectEnabled: Boolean? = null
        var isIndirectEnabled: Boolean? = null
        var isUnattributedEnabled: Boolean? = null

        // direct
        outcomeJson.expandJSONObject("direct") {
            isDirectEnabled = it.safeBool("enabled")
        }

        // indirect
        outcomeJson.expandJSONObject("indirect") { indirectJSON ->
            isIndirectEnabled = indirectJSON.safeBool("enabled")

            indirectJSON.expandJSONObject("notification_attribution") {
                indirectNotificationAttributionWindow = it.safeInt("minutes_since_displayed")
                notificationLimit = it.safeInt("limit")
            }

            indirectJSON.expandJSONObject("in_app_message_attribution") {
                indirectIAMAttributionWindow = it.safeInt("minutes_since_displayed")
                iamLimit = it.safeInt("limit")
            }
        }

        // unattributed
        outcomeJson.expandJSONObject("unattributed") {
            isUnattributedEnabled = it.safeBool("enabled")
        }

        return InfluenceParamsObject(
            indirectNotificationAttributionWindow,
            notificationLimit,
            indirectIAMAttributionWindow,
            iamLimit,
            isDirectEnabled,
            isIndirectEnabled,
            isUnattributedEnabled,
        )
    }
}
