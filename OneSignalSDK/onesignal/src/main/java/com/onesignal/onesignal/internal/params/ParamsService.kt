package com.onesignal.onesignal.internal.params

import org.json.JSONArray

// TODO: Implement
class ParamsService : IParamsService {
    override val googleProjectNumber: String? = null
    override val enterprise: Boolean = true
    override val useSMSAuth: Boolean = false
    override val useEmailAuth: Boolean = false
    override val useUserIdAuth: Boolean = false
    override val notificationChannels: JSONArray? = null
    override val firebaseAnalytics : Boolean = false
    override val restoreTTLFilter: Boolean = false
    override val clearGroupOnSummaryClick: Boolean = false
    override val receiveReceiptEnabled: Boolean = true
    override val disableGMSMissingPrompt: Boolean? = null
    override val unsubscribeWhenNotificationsDisabled: Boolean? = null
    override val locationShared: Boolean? = null
    override val requiresUserPrivacyConsent: Boolean? = null
    override val influenceParams: IParamsService.InfluenceParams? = null
    override val fcpParams: IParamsService.FCMParams = IParamsService.FCMParams(null, null, null)
}