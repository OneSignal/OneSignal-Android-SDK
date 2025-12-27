package com.onesignal.core.internal.config.impl

import com.onesignal.common.exceptions.BackendException
import com.onesignal.common.modeling.ISingletonModelStoreChangeHandler
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.common.modeling.ModelChangedArgs
import com.onesignal.common.threading.suspendifyOnIO
import com.onesignal.core.internal.backend.IParamsBackendService
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.config.ConfigModelStore
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
    private val _subscriptionManager: ISubscriptionManager,
) : IStartableService, ISingletonModelStoreChangeHandler<ConfigModel> {
    override fun start() {
        _configModelStore.subscribe(this)
        fetchParams()
    }

    override fun onModelUpdated(
        args: ModelChangedArgs,
        tag: String,
    ) {
        if (args.property != ConfigModel::appId.name) {
            return
        }

        fetchParams()
    }

    override fun onModelReplaced(
        model: ConfigModel,
        tag: String,
    ) {
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

        suspendifyOnIO {
            Logging.debug("ConfigModelListener: fetching parameters for appId: $appId")

            var androidParamsRetries = 0
            var success = false
            do {
                try {
                    val params = _paramsBackendService.fetchParams(appId, _subscriptionManager.subscriptions.push.id.ifEmpty { null })

                    // copy current model into new model, then override with what comes down.
                    val config = ConfigModel()
                    config.initializeFromModel(null, _configModelStore.model)

                    config.isInitializedWithRemote = true

                    // these are always copied from the backend params
                    config.appId = appId
                    config.notificationChannels = params.notificationChannels
                    config.googleProjectNumber = params.googleProjectNumber
                    config.fcmParams.projectId = params.fcmParams.projectId
                    config.fcmParams.appId = params.fcmParams.appId
                    config.fcmParams.apiKey = params.fcmParams.apiKey

                    // these are only copied from the backend params when the backend has set them.
                    params.enterprise?.let { config.enterprise = it }
                    params.useIdentityVerification?.let { config.useIdentityVerification = it }
                    params.firebaseAnalytics?.let { config.firebaseAnalytics = it }
                    params.restoreTTLFilter?.let { config.restoreTTLFilter = it }
                    params.clearGroupOnSummaryClick?.let { config.clearGroupOnSummaryClick = it }
                    params.receiveReceiptEnabled?.let { config.receiveReceiptEnabled = it }
                    params.disableGMSMissingPrompt?.let { config.disableGMSMissingPrompt = it }
                    params.unsubscribeWhenNotificationsDisabled?.let { config.unsubscribeWhenNotificationsDisabled = it }
                    params.locationShared?.let { config.locationShared = it }
                    params.requiresUserPrivacyConsent?.let { config.consentRequired = it }
                    params.opRepoExecutionInterval?.let { config.opRepoExecutionInterval = it }
                    params.influenceParams.notificationLimit?.let { config.influenceParams.notificationLimit = it }
                    params.influenceParams.indirectNotificationAttributionWindow?.let { config.influenceParams.indirectNotificationAttributionWindow = it }
                    params.influenceParams.iamLimit?.let { config.influenceParams.iamLimit = it }
                    params.influenceParams.indirectIAMAttributionWindow?.let { config.influenceParams.indirectIAMAttributionWindow = it }
                    params.influenceParams.isDirectEnabled?.let { config.influenceParams.isDirectEnabled = it }
                    params.influenceParams.isIndirectEnabled?.let { config.influenceParams.isIndirectEnabled = it }
                    params.influenceParams.isUnattributedEnabled?.let { config.influenceParams.isUnattributedEnabled = it }

                    params.remoteLoggingParams.enable?.let { config.remoteLoggingParams.enable = it }

                    _configModelStore.replace(config, ModelChangeTags.HYDRATE)
                    success = true
                } catch (ex: BackendException) {
                    if (ex.statusCode == HttpURLConnection.HTTP_FORBIDDEN) {
                        Logging.fatal("403 error getting OneSignal params, omitting further retries!")
                        return@suspendifyOnIO
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
