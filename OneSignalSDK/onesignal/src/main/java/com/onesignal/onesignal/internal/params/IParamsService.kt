package com.onesignal.onesignal.internal.params

import org.json.JSONArray

/**
 * The params service gives access to the parameters managed by the back end.
 */
interface IParamsService {
    class FCMParams(
        val projectId: String? = null,
        val appId: String? = null,
        val apiKey: String? = null
    )

    class InfluenceParams(
        // In minutes
        val indirectNotificationAttributionWindow: Int = DEFAULT_INDIRECT_ATTRIBUTION_WINDOW,
        val notificationLimit: Int = DEFAULT_NOTIFICATION_LIMIT,

        // In minutes
        val indirectIAMAttributionWindow: Int = DEFAULT_INDIRECT_ATTRIBUTION_WINDOW,
        val iamLimit: Int = DEFAULT_NOTIFICATION_LIMIT,
        val isDirectEnabled: Boolean = false,
        val isIndirectEnabled: Boolean = false,
        val isUnattributedEnabled: Boolean = false,
        val outcomesV2ServiceEnabled: Boolean = false
    ) {
        companion object {
            val DEFAULT_INDIRECT_ATTRIBUTION_WINDOW = 24 * 60
            val DEFAULT_NOTIFICATION_LIMIT = 10
        }
    }

    val googleProjectNumber: String?
    val enterprise: Boolean
    val useSMSAuth: Boolean
    val useEmailAuth: Boolean
    val useUserIdAuth: Boolean
    val notificationChannels: JSONArray?
    val firebaseAnalytics : Boolean
    val restoreTTLFilter: Boolean
    val clearGroupOnSummaryClick: Boolean
    val receiveReceiptEnabled: Boolean
    val disableGMSMissingPrompt: Boolean?
    val unsubscribeWhenNotificationsDisabled: Boolean?
    val locationShared: Boolean?
    val requiresUserPrivacyConsent: Boolean?
    val influenceParams: InfluenceParams?
    val fcpParams: FCMParams
}