package com.onesignal.onesignal.core.internal.params.impl

import com.onesignal.onesignal.core.internal.params.IParamsService
import com.onesignal.onesignal.core.internal.params.IWriteableParamsService
import org.json.JSONArray

// TODO: Implement
class ParamsService : IParamsService, IWriteableParamsService {
    override var appId: String? = null
    override var googleProjectNumber: String? = null
    override var enterprise: Boolean = true
    override var useSMSAuth: Boolean = false
    override var useEmailAuth: Boolean = false
    override var useUserIdAuth: Boolean = false
    override var notificationChannels: JSONArray? = null
    override var firebaseAnalytics : Boolean = false
    override var restoreTTLFilter: Boolean = false
    override var clearGroupOnSummaryClick: Boolean = false
    override var receiveReceiptEnabled: Boolean = true
    override var disableGMSMissingPrompt: Boolean? = null
    override var unsubscribeWhenNotificationsDisabled: Boolean? = null
    override var locationShared: Boolean? = null
    override var requiresUserPrivacyConsent: Boolean? = null
    override var influenceParams: IParamsService.InfluenceParams = IParamsService.InfluenceParams()
    override var fcmParams: IParamsService.FCMParams = IParamsService.FCMParams()
}