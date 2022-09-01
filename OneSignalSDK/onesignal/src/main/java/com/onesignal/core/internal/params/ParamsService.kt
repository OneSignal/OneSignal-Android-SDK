package com.onesignal.core.internal.params

import com.onesignal.core.internal.common.events.EventProducer
import org.json.JSONArray

// TODO: Implement
internal class ParamsService : IParamsService, IWriteableParamsService {
    override var appId: String? = null
    override var googleProjectNumber: String? = null
    override var enterprise: Boolean = true
    override var useSMSAuth: Boolean = false
    override var useEmailAuth: Boolean = false
    override var useUserIdAuth: Boolean = false
    override var notificationChannels: JSONArray? = null
    override var firebaseAnalytics: Boolean = false
    override var restoreTTLFilter: Boolean = false
    override var clearGroupOnSummaryClick: Boolean = false
    override var receiveReceiptEnabled: Boolean = true
    override var disableGMSMissingPrompt: Boolean? = null
    override var unsubscribeWhenNotificationsDisabled: Boolean? = null
    override var locationShared: Boolean? = null
    override var requiresUserPrivacyConsent: Boolean? = null
    override var influenceParams: IParamsService.InfluenceParams = IParamsService.InfluenceParams()
    override var fcmParams: IParamsService.FCMParams = IParamsService.FCMParams()

    private val _notifier = EventProducer<IParamsChangedHandler>()

    override fun subscribe(handler: IParamsChangedHandler) = _notifier.subscribe(handler)
    override fun unsubscribe(handler: IParamsChangedHandler) = _notifier.unsubscribe(handler)

    override fun indicateChanged() {
        _notifier.fire { it.onParamsChanged() }
    }
}
