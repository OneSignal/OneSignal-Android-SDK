package com.onesignal.onesignal.core.internal.params

import org.json.JSONArray

/**
 * The params service gives access to the parameters managed by the back end.
 */
interface IWriteableParamsService : IParamsService {
    override var appId: String?
    override var googleProjectNumber: String?
    override var enterprise: Boolean
    override var useSMSAuth: Boolean
    override var useEmailAuth: Boolean
    override var useUserIdAuth: Boolean
    override var notificationChannels: JSONArray?
    override var firebaseAnalytics : Boolean
    override var restoreTTLFilter: Boolean
    override var clearGroupOnSummaryClick: Boolean
    override var receiveReceiptEnabled: Boolean
    override var disableGMSMissingPrompt: Boolean?
    override var unsubscribeWhenNotificationsDisabled: Boolean?
    override var locationShared: Boolean?
    override var requiresUserPrivacyConsent: Boolean?
    override var influenceParams: IParamsService.InfluenceParams
    override var fcmParams: IParamsService.FCMParams

    fun indicateChanged()
}