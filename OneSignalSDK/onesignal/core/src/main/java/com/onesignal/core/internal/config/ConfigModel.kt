package com.onesignal.core.internal.config

import com.onesignal.common.modeling.Model
import com.onesignal.core.internal.http.OneSignalService.ONESIGNAL_API_BASE_URL
import org.json.JSONArray
import org.json.JSONObject

class ConfigModel : Model() {
    /**
     * Whether this config has been initialized with remote data.
     */
    var isInitializedWithRemote: Boolean
        get() = getBooleanProperty(::isInitializedWithRemote.name) { false }
        set(value) {
            setBooleanProperty(::isInitializedWithRemote.name, value)
        }

    /**
     * The current OneSignal application ID provided to the SDK.
     */
    var appId: String
        get() = getStringProperty(::appId.name)
        set(value) {
            setStringProperty(::appId.name, value)
        }

    /**
     * This device's push subscription ID.
     */
    var pushSubscriptionId: String?
        get() = getOptStringProperty(::pushSubscriptionId.name)
        set(value) {
            setOptStringProperty(::pushSubscriptionId.name, value)
        }

    /**
     * The API URL String.
     */
    var apiUrl: String
        get() = getStringProperty(::apiUrl.name) { ONESIGNAL_API_BASE_URL }
        set(value) {
            setStringProperty(::apiUrl.name, value)
        }

    /**
     * Whether the SDK requires privacy consent to send data to backend.
     */
    var consentRequired: Boolean?
        get() = getOptBooleanProperty(::consentRequired.name)
        set(value) {
            setOptBooleanProperty(::consentRequired.name, value)
        }

    /**
     * Whether the SDK has been given consent to privacy.
     */
    var consentGiven: Boolean?
        get() = getOptBooleanProperty(::consentGiven.name)
        set(value) {
            setOptBooleanProperty(::consentGiven.name, value)
        }

    /**
     * Whether location is shared.
     */
    var locationShared: Boolean
        get() = getBooleanProperty(::locationShared.name) { false }
        set(value) = setBooleanProperty(::locationShared.name, value)

    /**
     * Whether to disable the "GMS is missing" prompt to the user.
     */
    var disableGMSMissingPrompt: Boolean
        get() = getBooleanProperty(::disableGMSMissingPrompt.name) { false }
        set(value) {
            setBooleanProperty(::disableGMSMissingPrompt.name, value)
        }

    /**
     * Whether to disable the "GMS is missing" prompt to the user.
     */
    var userRejectedGMSUpdate: Boolean
        get() = getBooleanProperty(::userRejectedGMSUpdate.name) { false }
        set(value) {
            setBooleanProperty(::userRejectedGMSUpdate.name, value)
        }

    /**
     * Whether to automatically unsubscribe from OneSignal when notifications have been disabled.
     */
    var unsubscribeWhenNotificationsDisabled: Boolean
        get() = getBooleanProperty(::unsubscribeWhenNotificationsDisabled.name) { false }
        set(value) {
            setBooleanProperty(::unsubscribeWhenNotificationsDisabled.name, value)
        }

    /**
     * The timeout in milliseconds for an HTTP connection.
     */
    var httpTimeout: Int
        get() = getIntProperty(::httpTimeout.name) { 120000 }
        set(value) {
            setIntProperty(::httpTimeout.name, value)
        }

    /**
     * The timeout in milliseconds for an HTTP connection GET request.
     */
    var httpGetTimeout: Int
        get() = getIntProperty(::httpGetTimeout.name) { 60000 }
        set(value) {
            setIntProperty(::httpGetTimeout.name, value)
        }

    /**
     * The fallback Retry-After to use if the header is present, but the server
     * give us a format we can't parse.
     */
    var httpRetryAfterParseFailFallback: Int
        get() = getIntProperty(::httpRetryAfterParseFailFallback.name) { 60 }
        set(value) {
            setIntProperty(::httpRetryAfterParseFailFallback.name, value)
        }

    /**
     * Maximum time in milliseconds a user can spend out of focus before a new session is created.
     */
    var sessionFocusTimeout: Long
        get() = getLongProperty(::sessionFocusTimeout.name) { 30000 }
        set(value) {
            setLongProperty(::sessionFocusTimeout.name, value)
        }

    /**
     * The minimum number of milliseconds required to pass before executing another operation on
     * the queue.
     */
    var opRepoExecutionInterval: Long
        get() = getLongProperty(::opRepoExecutionInterval.name) { 5000 }
        set(value) {
            setLongProperty(::opRepoExecutionInterval.name, value)
        }

    /**
     * The number of milliseconds to delay after the operation repo processing has been woken. This
     * subsequent delay allow a sequence of changes to be grouped, rather than the first enqueue
     * to be executed in isolation (because that is the one doing the waking).
     */
    var opRepoPostWakeDelay: Long
        get() = getLongProperty(::opRepoPostWakeDelay.name) { 200 }
        set(value) {
            setLongProperty(::opRepoPostWakeDelay.name, value)
        }

    /**
     * The number of milliseconds to delay after an operation completes
     * that creates or changes ids.
     * This is a "cold down" period to avoid a caveat with OneSignal's backend
     * replication, where you may incorrectly get a 404 when attempting a GET
     * or PATCH REST API call on something just after it is created.
     */
    var opRepoPostCreateDelay: Long
        get() = getLongProperty(::opRepoPostCreateDelay.name) { 5_000 }
        set(value) {
            setLongProperty(::opRepoPostCreateDelay.name, value)
        }

    /**
     * The number of milliseconds to retry operations for new models.
     * This is a fallback to opRepoPostCreateDelay, where it's delay may
     * not be enough. The server may be unusually overloaded so we will
     * retry these (back-off rules apply to all retries) as we only want
     * to re-create records as a last resort.
     */
    var opRepoPostCreateRetryUpTo: Long
        get() = getLongProperty(::opRepoPostCreateRetryUpTo.name) { 60_000 }
        set(value) {
            setLongProperty(::opRepoPostCreateRetryUpTo.name, value)
        }

    /**
     * The number of milliseconds times the number of times FAIL_RETRY
     * is returned from an executor for a specific operation. AKA this
     * backoff will increase each time we retry a specific operation
     * by this value.
     */
    var opRepoDefaultFailRetryBackoff: Long
        get() = getLongProperty(::opRepoDefaultFailRetryBackoff.name) { 15_000 }
        set(value) {
            setLongProperty(::opRepoDefaultFailRetryBackoff.name, value)
        }

    /**
     * The minimum number of milliseconds required to pass to allow the fetching of IAM to occur.
     */
    var fetchIAMMinInterval: Long
        get() = getLongProperty(::fetchIAMMinInterval.name) { 30_000 }
        set(value) {
            setLongProperty(::fetchIAMMinInterval.name, value)
        }

    /**
     * The number of milliseconds between fetching the current notification permission value when the app is in focus
     */
    var foregroundFetchNotificationPermissionInterval: Long
        get() = getLongProperty(::foregroundFetchNotificationPermissionInterval.name) { 1_000 }
        set(value) {
            setLongProperty(::foregroundFetchNotificationPermissionInterval.name, value)
        }

    /**
     * The number of milliseconds between fetching the current notification permission value when the app is out of focus
     * We want this value to be very large to effectively stop polling in the background
     */
    var backgroundFetchNotificationPermissionInterval: Long
        get() = getLongProperty(::backgroundFetchNotificationPermissionInterval.name) { 86_400_000 }
        set(value) {
            setLongProperty(::backgroundFetchNotificationPermissionInterval.name, value)
        }

    /**
     * The google project number for GMS devices.
     */
    var googleProjectNumber: String?
        get() = getOptStringProperty(::googleProjectNumber.name)
        set(value) {
            setOptStringProperty(::googleProjectNumber.name, value)
        }

    /**
     * Whether the current application is an enterprise-level
     */
    var enterprise: Boolean
        get() = getBooleanProperty(::enterprise.name) { false }
        set(value) {
            setBooleanProperty(::enterprise.name, value)
        }

    /**
     * Whether SMS auth hash should be used.
     */
    var useIdentityVerification: Boolean
        get() = getBooleanProperty(::useIdentityVerification.name) { false }
        set(value) {
            setBooleanProperty(::useIdentityVerification.name, value)
        }

    /**
     * The notification channel information as a [JSONArray]
     */
    var notificationChannels: JSONArray?
        get() = JSONArray(getOptStringProperty(::notificationChannels.name) { null } ?: "[]")
        set(value) {
            setOptStringProperty(::notificationChannels.name, value?.toString())
        }

    /**
     * Whether firebase analytics should be used
     */
    var firebaseAnalytics: Boolean
        get() = getBooleanProperty(::firebaseAnalytics.name) { false }
        set(value) {
            setBooleanProperty(::firebaseAnalytics.name, value)
        }

    /**
     * Whether to honor TTL for notifications
     */
    var restoreTTLFilter: Boolean
        get() = getBooleanProperty(::restoreTTLFilter.name) { true }
        set(value) {
            setBooleanProperty(::restoreTTLFilter.name, value)
        }

    /**
     * Whether to track notification receive receipts
     */
    var receiveReceiptEnabled: Boolean
        get() = getBooleanProperty(::receiveReceiptEnabled.name) { false }
        set(value) {
            setBooleanProperty(::receiveReceiptEnabled.name, value)
        }

    /**
     * Whether to clear group on summary clicks
     */
    var clearGroupOnSummaryClick: Boolean
        get() = getBooleanProperty(::clearGroupOnSummaryClick.name) { true }
        set(value) {
            setBooleanProperty(::clearGroupOnSummaryClick.name, value)
        }

    /**
     * The outcomes parameters
     */
    val influenceParams: InfluenceConfigModel
        get() = getAnyProperty(::influenceParams.name) { InfluenceConfigModel(this, ::influenceParams.name) } as InfluenceConfigModel

    /**
     * The firebase cloud parameters
     */
    val fcmParams: FCMConfigModel
        get() = getAnyProperty(::fcmParams.name) { FCMConfigModel(this, ::fcmParams.name) } as FCMConfigModel

    val remoteLoggingParams: RemoteLoggingConfigModel
        get() = getAnyProperty(::remoteLoggingParams.name) { RemoteLoggingConfigModel(this, ::remoteLoggingParams.name) } as RemoteLoggingConfigModel

    override fun createModelForProperty(
        property: String,
        jsonObject: JSONObject,
    ): Model? {
        if (property == ::influenceParams.name) {
            val model = InfluenceConfigModel(this, ::influenceParams.name)
            model.initializeFromJson(jsonObject)
            return model
        }

        if (property == ::fcmParams.name) {
            val model = FCMConfigModel(this, ::influenceParams.name)
            model.initializeFromJson(jsonObject)
            return model
        }

        if (property == ::remoteLoggingParams.name) {
            val model = RemoteLoggingConfigModel(this, ::remoteLoggingParams.name)
            model.initializeFromJson(jsonObject)
            return model
        }

        return null
    }
}

/**
 * Configuration related to influence management.
 */
class InfluenceConfigModel(parentModel: Model, parentProperty: String) : Model(parentModel, parentProperty) {
    /**
     * The number of minutes a push notification can be considered to influence a user.
     */
    var indirectNotificationAttributionWindow: Int
        get() = getIntProperty(::indirectNotificationAttributionWindow.name) { DEFAULT_INDIRECT_ATTRIBUTION_WINDOW }
        set(value) {
            setIntProperty(::indirectNotificationAttributionWindow.name, value)
        }

    /**
     * The maximum number of push notifications that can influence at one time.
     */
    var notificationLimit: Int
        get() = getIntProperty(::notificationLimit.name) { DEFAULT_NOTIFICATION_LIMIT }
        set(value) {
            setIntProperty(::notificationLimit.name, value)
        }

    /**
     * The number of minutes an IAM can be considered to influence a user.
     */
    var indirectIAMAttributionWindow: Int
        get() = getIntProperty(::indirectIAMAttributionWindow.name) { DEFAULT_INDIRECT_ATTRIBUTION_WINDOW }
        set(value) {
            setIntProperty(::indirectIAMAttributionWindow.name, value)
        }

    /**
     * The maximum number of IAMs that can influence at one time.
     */
    var iamLimit: Int
        get() = getIntProperty(::iamLimit.name) { DEFAULT_NOTIFICATION_LIMIT }
        set(value) {
            setIntProperty(::iamLimit.name, value)
        }

    /**
     * Whether DIRECT influences are enabled.
     */
    var isDirectEnabled: Boolean
        get() = getBooleanProperty(::isDirectEnabled.name) { false }
        set(value) {
            setBooleanProperty(::isDirectEnabled.name, value)
        }

    /**
     * Whether INDIRECT influences are enabled.
     */
    var isIndirectEnabled: Boolean
        get() = getBooleanProperty(::isIndirectEnabled.name) { false }
        set(value) {
            setBooleanProperty(::isIndirectEnabled.name, value)
        }

    /**
     * Whether UNATTRIBUTED influences are enabled.
     */
    var isUnattributedEnabled: Boolean
        get() = getBooleanProperty(::isUnattributedEnabled.name) { false }
        set(value) {
            setBooleanProperty(::isUnattributedEnabled.name, value)
        }

    companion object {
        const val DEFAULT_INDIRECT_ATTRIBUTION_WINDOW = 24 * 60
        const val DEFAULT_NOTIFICATION_LIMIT = 10
    }
}

/**
 * Configuration related to Firebase Cloud Messaging.
 */
class FCMConfigModel(parentModel: Model, parentProperty: String) : Model(parentModel, parentProperty) {
    /**
     * The FCM project ID.
     */
    var projectId: String?
        get() = getOptStringProperty(::projectId.name) { null }
        set(value) {
            setOptStringProperty(::projectId.name, value)
        }

    /**
     * The FCM app ID.
     */
    var appId: String?
        get() = getOptStringProperty(::appId.name) { null }
        set(value) {
            setOptStringProperty(::appId.name, value)
        }

    /**
     * The FCM api key.
     */
    var apiKey: String?
        get() = getOptStringProperty(::apiKey.name) { null }
        set(value) {
            setOptStringProperty(::apiKey.name, value)
        }
}

/**
 * Configuration related to OneSignal's remote logging.
 */
class RemoteLoggingConfigModel(
    parentModel: Model,
    parentProperty: String,
) : Model(parentModel, parentProperty) {
    /**
     * The minimum log level to send to OneSignal's server.
     * If null, defaults to ERROR level for client-side logging.
     * If NONE, no logs (including errors) will be sent remotely.
     *
     * Log levels: NONE < FATAL < ERROR < WARN < INFO < DEBUG < VERBOSE
     */
    var logLevel: com.onesignal.debug.LogLevel?
        get() = getOptEnumProperty<com.onesignal.debug.LogLevel>(::logLevel.name)
        set(value) {
            setOptEnumProperty(::logLevel.name, value)
        }

    /**
     * Whether remote logging is enabled.
     * Set by backend config hydration — true when the server sends a valid log_level, false otherwise.
     */
    var isEnabled: Boolean
        get() = getBooleanProperty(::isEnabled.name) { false }
        set(value) {
            setBooleanProperty(::isEnabled.name, value)
        }
}
