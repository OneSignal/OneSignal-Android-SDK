package com.onesignal.onesignal.core.internal.params

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

    val appId: String?

    val googleProjectNumber: String?

    /**
     * Whether the current application is an enterprise-level
     */
    val enterprise: Boolean

    /**
     * Whether SMS auth hash should be used.
     */
    val useSMSAuth: Boolean

    /**
     * Whether email auth hash should be used.
     */
    val useEmailAuth: Boolean

    /**
     * Whether user auth hash should be used.
     */
    val useUserIdAuth: Boolean

    /**
     * The notification channel information as a [JSONArray]
     */
    val notificationChannels: JSONArray?

    /**
     * Whether firebase analytics should be used
     */
    val firebaseAnalytics : Boolean

    /**
     * Whether to honor TTL for notifications
     */
    val restoreTTLFilter: Boolean

    /**
     * TODO: Is this used anymore?
     */
    val clearGroupOnSummaryClick: Boolean

    /**
     * Whether to track notification receive receipts
     */
    val receiveReceiptEnabled: Boolean


    /**
     * TODO: Is this ever sent down?
     */
    val disableGMSMissingPrompt: Boolean?

    /**
     * TODO: Is this ever sent down?
     */
    val unsubscribeWhenNotificationsDisabled: Boolean?

    /**
     * TODO: Is this ever sent down?
     */
    val locationShared: Boolean?

    /**
     * TODO: Is this ever sent down?
     */
    val requiresUserPrivacyConsent: Boolean?

    /**
     * The outcomes parameters
     */
    val influenceParams: InfluenceParams

    /**
     * The firebase cloud parameters
     */
    val fcmParams: FCMParams
}