package com.onesignal.core.internal.config

import com.onesignal.common.modeling.Model
import org.json.JSONArray
import org.json.JSONObject

class ConfigModel : Model() {
    /**
     * The current OneSignal application ID provided to the SDK.
     */
    var appId: String
        get() = getProperty(::appId.name)
        set(value) { setProperty(::appId.name, value) }

    /**
     * Whether the SDK requires privacy consent to send data to backend.
     */
    var requiresPrivacyConsent: Boolean?
        get() = getProperty(::requiresPrivacyConsent.name)
        set(value) { setProperty(::requiresPrivacyConsent.name, value) }

    /**
     * Whether the SDK has been given consent to privacy.
     */
    var givenPrivacyConsent: Boolean?
        get() = getProperty(::givenPrivacyConsent.name)
        set(value) { setProperty(::givenPrivacyConsent.name, value) }

    /**
     * Whether location is shared.
     */
    var locationShared: Boolean
        get() = getProperty(::locationShared.name) { false }
        set(value) = setProperty(::locationShared.name, value)

    /**
     * Whether to disable the "GMS is missing" prompt to the user.
     */
    var disableGMSMissingPrompt: Boolean
        get() = getProperty(::disableGMSMissingPrompt.name) { false }
        set(value) { setProperty(::disableGMSMissingPrompt.name, value) }

    /**
     * Whether to disable the "GMS is missing" prompt to the user.
     */
    var userRejectedGMSUpdate: Boolean
        get() = getProperty(::userRejectedGMSUpdate.name) { false }
        set(value) { setProperty(::userRejectedGMSUpdate.name, value) }

    /**
     * Whether to automatically unsubscribe from OneSignal when notifications have been disabled.
     */
    var unsubscribeWhenNotificationsDisabled: Boolean
        get() = getProperty(::unsubscribeWhenNotificationsDisabled.name) { false }
        set(value) { setProperty(::unsubscribeWhenNotificationsDisabled.name, value) }

    /**
     * The timeout in milliseconds for an HTTP connection.
     */
    var httpTimeout: Int
        get() = getProperty(::httpTimeout.name) { 120000 }
        set(value) { setProperty(::httpTimeout.name, value) }

    /**
     * The timeout in milliseconds for an HTTP connection GET request.
     */
    var httpGetTimeout: Int
        get() = getProperty(::httpGetTimeout.name) { 60000 }
        set(value) { setProperty(::httpGetTimeout.name, value) }

    /**
     * Maximum time in milliseconds a user can spend out of focus before a new session is created.
     */
    var sessionFocusTimeout: Long
        get() = getProperty(::sessionFocusTimeout.name) { 30000 }
        set(value) { setProperty(::sessionFocusTimeout.name, value) }

    /**
     * The minimum number of milliseconds required to pass before executing another operation on
     * the queue.
     */
    var opRepoExecutionInterval: Long
        get() = getProperty(::opRepoExecutionInterval.name) { 5000 }
        set(value) { setProperty(::opRepoExecutionInterval.name, value) }

    /**
     * The number of milliseconds to delay after the operation repo processing has been woken. This
     * subsequent delay allow a sequence of changes to be grouped, rather than the first enqueue
     * to be executed in isolation (because that is the one doing the waking).
     */
    var opRepoPostWakeDelay: Long
        get() = getProperty(::opRepoPostWakeDelay.name) { 200 }
        set(value) { setProperty(::opRepoPostWakeDelay.name, value) }

    /**
     * The minimum number of milliseconds required to pass to allow the fetching of IAM to occur.
     */
    var fetchIAMMinInterval: Long
        get() = getProperty(::fetchIAMMinInterval.name) { 30000 }
        set(value) { setProperty(::fetchIAMMinInterval.name, value) }

    /**
     * The google project number for GMS devices.
     */
    var googleProjectNumber: String?
        get() = getProperty(::googleProjectNumber.name)
        set(value) { setProperty(::googleProjectNumber.name, value) }

    /**
     * Whether the current application is an enterprise-level
     */
    var enterprise: Boolean
        get() = getProperty(::enterprise.name) { false }
        set(value) { setProperty(::enterprise.name, value) }

    /**
     * Whether SMS auth hash should be used.
     */
    var useIdentityVerification: Boolean
        get() = getProperty(::useIdentityVerification.name) { false }
        set(value) { setProperty(::useIdentityVerification.name, value) }

    /**
     * The notification channel information as a [JSONArray]
     */
    var notificationChannels: JSONArray?
        get() = getProperty(::notificationChannels.name) { null }
        set(value) { setProperty(::notificationChannels.name, value) }

    /**
     * Whether firebase analytics should be used
     */
    var firebaseAnalytics: Boolean
        get() = getProperty(::firebaseAnalytics.name) { false }
        set(value) { setProperty(::firebaseAnalytics.name, value) }

    /**
     * Whether to honor TTL for notifications
     */
    var restoreTTLFilter: Boolean
        get() = getProperty(::restoreTTLFilter.name) { true }
        set(value) { setProperty(::restoreTTLFilter.name, value) }

    /**
     * Whether to track notification receive receipts
     */
    var receiveReceiptEnabled: Boolean
        get() = getProperty(::receiveReceiptEnabled.name) { false }
        set(value) { setProperty(::receiveReceiptEnabled.name, value) }

    /**
     * Whether to clear group on summary clicks
     */
    var clearGroupOnSummaryClick: Boolean
        get() = getProperty(::clearGroupOnSummaryClick.name) { true }
        set(value) { setProperty(::clearGroupOnSummaryClick.name, value) }

    /**
     * The outcomes parameters
     */
    val influenceParams: InfluenceConfigModel
        get() = getProperty(::influenceParams.name) { InfluenceConfigModel(this, ::influenceParams.name) }

    /**
     * The firebase cloud parameters
     */
    val fcmParams: FCMConfigModel
        get() = getProperty(::fcmParams.name) { FCMConfigModel(this, ::fcmParams.name) }

    override fun createModelForProperty(property: String, jsonObject: JSONObject): Model? {
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
        get() = getProperty(::indirectNotificationAttributionWindow.name) { DEFAULT_INDIRECT_ATTRIBUTION_WINDOW }
        set(value) { setProperty(::indirectNotificationAttributionWindow.name, value) }

    /**
     * The maximum number of push notifications that can influence at one time.
     */
    var notificationLimit: Int
        get() = getProperty(::notificationLimit.name) { DEFAULT_NOTIFICATION_LIMIT }
        set(value) { setProperty(::notificationLimit.name, value) }

    /**
     * The number of minutes an IAM can be considered to influence a user.
     */
    var indirectIAMAttributionWindow: Int
        get() = getProperty(::indirectIAMAttributionWindow.name) { DEFAULT_INDIRECT_ATTRIBUTION_WINDOW }
        set(value) { setProperty(::indirectIAMAttributionWindow.name, value) }

    /**
     * The maximum number of IAMs that can influence at one time.
     */
    var iamLimit: Int
        get() = getProperty(::iamLimit.name) { DEFAULT_NOTIFICATION_LIMIT }
        set(value) { setProperty(::iamLimit.name, value) }

    /**
     * Whether DIRECT influences are enabled.
     */
    var isDirectEnabled: Boolean
        get() = getProperty(::isDirectEnabled.name) { false }
        set(value) { setProperty(::isDirectEnabled.name, value) }

    /**
     * Whether INDIRECT influences are enabled.
     */
    var isIndirectEnabled: Boolean
        get() = getProperty(::isIndirectEnabled.name) { false }
        set(value) { setProperty(::isIndirectEnabled.name, value) }

    /**
     * Whether UNATTRIBUTED influences are enabled.
     */
    var isUnattributedEnabled: Boolean
        get() = getProperty(::isUnattributedEnabled.name) { false }
        set(value) { setProperty(::isUnattributedEnabled.name, value) }

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
        get() = getProperty(::projectId.name) { null }
        set(value) { setProperty(::projectId.name, value) }

    /**
     * The FCM app ID.
     */
    var appId: String?
        get() = getProperty(::appId.name) { null }
        set(value) { setProperty(::appId.name, value) }

    /**
     * The FCM api key.
     */
    var apiKey: String?
        get() = getProperty(::apiKey.name) { null }
        set(value) { setProperty(::apiKey.name, value) }
}
