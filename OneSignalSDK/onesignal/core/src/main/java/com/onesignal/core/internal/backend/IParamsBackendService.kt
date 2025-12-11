package com.onesignal.core.internal.backend

import org.json.JSONArray

interface IParamsBackendService {
    /**
     * Retrieve the configuration parameters for the [appId] and optional [subscriptionId].
     *
     * If there is a non-successful response from the backend, a [BackendException] will be thrown with response data.
     *
     * @param appId The ID of the application to retrieve the configuration parameters for.
     * @param subscriptionId The ID of the subscription to retrieve the configuration parameters
     * for. If not specified the application-generic parameters will only be retrieved.
     *
     * @return The configuration parameters.
     */
    suspend fun fetchParams(
        appId: String,
        subscriptionId: String?,
    ): ParamsObject
}

class ParamsObject(
    var googleProjectNumber: String? = null,
    var enterprise: Boolean? = null,
    var useIdentityVerification: Boolean? = null,
    var notificationChannels: JSONArray? = null,
    var firebaseAnalytics: Boolean? = null,
    var restoreTTLFilter: Boolean? = null,
    var clearGroupOnSummaryClick: Boolean? = null,
    var receiveReceiptEnabled: Boolean? = null,
    var disableGMSMissingPrompt: Boolean? = null,
    var unsubscribeWhenNotificationsDisabled: Boolean? = null,
    var locationShared: Boolean? = null,
    var requiresUserPrivacyConsent: Boolean? = null,
    var opRepoExecutionInterval: Long? = null,
    var influenceParams: InfluenceParamsObject,
    var fcmParams: FCMParamsObject,
    val remoteLoggingParams: RemoteLoggingParamsObject,
)

class InfluenceParamsObject(
    val indirectNotificationAttributionWindow: Int? = null,
    val notificationLimit: Int? = null,
    val indirectIAMAttributionWindow: Int? = null,
    val iamLimit: Int? = null,
    val isDirectEnabled: Boolean? = null,
    val isIndirectEnabled: Boolean? = null,
    val isUnattributedEnabled: Boolean? = null,
)

class FCMParamsObject(
    val projectId: String? = null,
    val appId: String? = null,
    val apiKey: String? = null,
)

class RemoteLoggingParamsObject(
    val enable: Boolean? = null,
)
