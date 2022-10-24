package com.onesignal.core.internal.config.impl

import com.onesignal.common.exceptions.BackendException
import com.onesignal.common.modeling.ISingletonModelStoreChangeHandler
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.common.modeling.ModelChangedArgs
import com.onesignal.common.threading.suspendifyOnThread
import com.onesignal.core.internal.backend.IParamsBackendService
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.config.InfluenceConfigModel
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.user.internal.subscriptions.ISubscriptionManager
import kotlinx.coroutines.delay
import java.net.HttpURLConnection

/**
 * This is responsible for refreshing the parameters of the current OneSignal appId, whenever
 * required.  A refresh is required when:
 *
 * 1. The SDK has been started and an appId first assigned.
 * 2. The SDK has had it's appId changed.
 */
internal class ConfigModelStoreListener(
    private val _configModelStore: ConfigModelStore,
    private val _paramsBackendService: IParamsBackendService,
    private val _subscriptionManager: ISubscriptionManager
) : IStartableService, ISingletonModelStoreChangeHandler<ConfigModel> {

    override fun start() {
        _configModelStore.subscribe(this)
        fetchParams()
    }

    override fun onModelUpdated(args: ModelChangedArgs, tag: String) {
        if (args.property != ConfigModel::appId.name) {
            return
        }

        fetchParams()
    }

    override fun onModelReplaced(model: ConfigModel, tag: String) {
        if (tag != ModelChangeTags.NORMAL) {
            return
        }

        fetchParams()
    }

    private fun fetchParams() {
        val appId = _configModelStore.model.appId

        if (appId.isEmpty()) {
            return
        }

        suspendifyOnThread {
            Logging.debug("ConfigModelListener: fetching parameters for appId: $appId")

            var androidParamsRetries = 0
            var success = false
            do {
                try {
                    val params = _paramsBackendService.fetchParams(appId, _subscriptionManager.subscriptions.push?.id)

                    val config = ConfigModel()
                    config.appId = appId
                    config.enterprise = params.enterprise ?: false
                    config.useIdentityVerification = params.useIdentityVerification ?: false
                    config.notificationChannels = params.notificationChannels
                    config.firebaseAnalytics = params.firebaseAnalytics ?: false
                    config.restoreTTLFilter = params.restoreTTLFilter ?: true
                    config.googleProjectNumber = params.googleProjectNumber
                    config.clearGroupOnSummaryClick = params.clearGroupOnSummaryClick ?: true
                    config.receiveReceiptEnabled = params.receiveReceiptEnabled ?: false
                    config.disableGMSMissingPrompt = params.disableGMSMissingPrompt ?: _configModelStore.model.disableGMSMissingPrompt
                    config.unsubscribeWhenNotificationsDisabled = params.unsubscribeWhenNotificationsDisabled ?: _configModelStore.model.unsubscribeWhenNotificationsDisabled
                    config.locationShared = params.locationShared ?: _configModelStore.model.locationShared
                    config.requiresPrivacyConsent = params.requiresUserPrivacyConsent ?: _configModelStore.model.requiresPrivacyConsent
                    config.givenPrivacyConsent = _configModelStore.model.givenPrivacyConsent

                    config.influenceParams.notificationLimit = params.influenceParams.notificationLimit ?: InfluenceConfigModel.DEFAULT_NOTIFICATION_LIMIT
                    config.influenceParams.indirectNotificationAttributionWindow = params.influenceParams.indirectNotificationAttributionWindow ?: InfluenceConfigModel.DEFAULT_INDIRECT_ATTRIBUTION_WINDOW
                    config.influenceParams.iamLimit = params.influenceParams.iamLimit ?: InfluenceConfigModel.DEFAULT_NOTIFICATION_LIMIT
                    config.influenceParams.indirectIAMAttributionWindow = params.influenceParams.indirectIAMAttributionWindow ?: InfluenceConfigModel.DEFAULT_INDIRECT_ATTRIBUTION_WINDOW

                    config.influenceParams.isDirectEnabled = params.influenceParams.isDirectEnabled ?: false
                    config.influenceParams.isIndirectEnabled = params.influenceParams.isIndirectEnabled ?: false
                    config.influenceParams.isUnattributedEnabled = params.influenceParams.isUnattributedEnabled ?: false

                    config.fcmParams.projectId = params.fcmParams.projectId
                    config.fcmParams.appId = params.fcmParams.appId
                    config.fcmParams.apiKey = params.fcmParams.apiKey

                    _configModelStore.replace(config, ModelChangeTags.HYDRATE)
                    success = true
                } catch (ex: BackendException) {
                    if (ex.statusCode == HttpURLConnection.HTTP_FORBIDDEN) {
                        Logging.fatal("403 error getting OneSignal params, omitting further retries!")
                        return@suspendifyOnThread
                    } else {
                        var sleepTime = MIN_WAIT_BETWEEN_RETRIES + androidParamsRetries * INCREASE_BETWEEN_RETRIES
                        if (sleepTime > MAX_WAIT_BETWEEN_RETRIES) {
                            sleepTime = MAX_WAIT_BETWEEN_RETRIES
                        }

                        Logging.info("Failed to get Android parameters, trying again in " + sleepTime / 1000 + " seconds.")

                        delay(sleepTime.toLong())
                        androidParamsRetries++
                    }
                }
            } while (!success)
        }
    }

    companion object {
        private const val INCREASE_BETWEEN_RETRIES = 10000
        private const val MIN_WAIT_BETWEEN_RETRIES = 30000
        private const val MAX_WAIT_BETWEEN_RETRIES = 90000
    }
}
