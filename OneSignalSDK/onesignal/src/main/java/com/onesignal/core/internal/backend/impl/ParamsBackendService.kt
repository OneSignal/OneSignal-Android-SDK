package com.onesignal.core.internal.backend.impl

import com.onesignal.core.debug.LogLevel
import com.onesignal.core.internal.backend.BackendException
import com.onesignal.core.internal.backend.FCMParamsObject
import com.onesignal.core.internal.backend.IParamsBackendService
import com.onesignal.core.internal.backend.InfluenceParamsObject
import com.onesignal.core.internal.backend.ParamsObject
import com.onesignal.core.internal.common.expand
import com.onesignal.core.internal.common.safeBool
import com.onesignal.core.internal.common.safeInt
import com.onesignal.core.internal.common.safeString
import com.onesignal.core.internal.http.CacheKeys
import com.onesignal.core.internal.http.IHttpClient
import com.onesignal.core.internal.logging.Logging
import org.json.JSONObject

internal class ParamsBackendService(
    private val _http: IHttpClient
) : IParamsBackendService {

    override suspend fun fetchParams(appId: String, subscriptionId: String?): ParamsObject {
        Logging.log(LogLevel.DEBUG, "ParamsBackendService.fetchParams(appId: $appId, subscriptionId: $subscriptionId)")

        var paramsUrl = "apps/$appId/android_params.js"
        if (subscriptionId != null) {
            paramsUrl += "?player_id=$subscriptionId"
        }

        val response = _http.get(paramsUrl, CacheKeys.REMOTE_PARAMS)

        if (!response.isSuccess) {
            throw BackendException(response.statusCode, response.payload)
        }

        val responseJson = JSONObject(response.payload!!)

        // Process outcomes params
        var influenceParams: InfluenceParamsObject? = null
        responseJson.expand("outcomes") {
            influenceParams = processOutcomeJson(it)
        }

        // Process FCM params
        var fcmParams: FCMParamsObject? = null
        responseJson.expand("fcm") {
            fcmParams = FCMParamsObject(
                apiKey = it.safeString("api_key"),
                appId = it.safeString("app_id"),
                projectId = it.safeString("project_id")
            )
        }

        return ParamsObject(
            googleProjectNumber = responseJson.safeString("android_sender_id"),
            enterprise = responseJson.safeBool("enterp"),
            useIdentityVerification = responseJson.safeBool("require_ident_auth"), // TODO: New
            notificationChannels = responseJson.optJSONArray("chnl_lst"),
            firebaseAnalytics = responseJson.safeBool("fba"),
            restoreTTLFilter = responseJson.safeBool("restore_ttl_filter"),
            clearGroupOnSummaryClick = responseJson.safeBool("clear_group_on_summary_click"),
            receiveReceiptEnabled = responseJson.safeBool("receive_receipts_enable"),
            disableGMSMissingPrompt = responseJson.safeBool("disable_gms_missing_prompt"),
            unsubscribeWhenNotificationsDisabled = responseJson.safeBool("unsubscribe_on_notifications_disabled"),
            locationShared = responseJson.safeBool("location_shared"),
            requiresUserPrivacyConsent = responseJson.safeBool("requires_user_privacy_consent"),
            influenceParams = influenceParams ?: InfluenceParamsObject(),
            fcmParams = fcmParams ?: FCMParamsObject()
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
        outcomeJson.expand("direct") {
            isDirectEnabled = it.safeBool("enabled")
        }

        // indirect
        outcomeJson.expand("indirect") { indirectJSON ->
            isIndirectEnabled = indirectJSON.safeBool("enabled")

            indirectJSON.expand("notification_attribution") {
                indirectNotificationAttributionWindow = it.safeInt("minutes_since_displayed")
                notificationLimit = it.safeInt("limit")
            }

            indirectJSON.expand("in_app_message_attribution") {
                indirectIAMAttributionWindow = it.safeInt("minutes_since_displayed")
                iamLimit = it.safeInt("limit")
            }
        }

        // unattributed
        outcomeJson.expand("unattributed") {
            isUnattributedEnabled = it.safeBool("enabled")
        }

        return InfluenceParamsObject(
            indirectNotificationAttributionWindow,
            notificationLimit,
            indirectIAMAttributionWindow,
            iamLimit,
            isDirectEnabled,
            isIndirectEnabled,
            isUnattributedEnabled
        )
    }
}
