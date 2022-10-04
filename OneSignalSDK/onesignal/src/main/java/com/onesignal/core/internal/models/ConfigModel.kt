package com.onesignal.core.internal.models

import com.onesignal.core.internal.modeling.Model

internal class ConfigModel : Model() {
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
     * Maximum time in minutes a user can spend out of focus before a new session is generated by the [SessionController]
     */
    var sessionFocusTimeout: Double
        get() = getProperty(::sessionFocusTimeout.name) { 10.0 }
        set(value) { setProperty(::sessionFocusTimeout.name, value) }

    /**
     * The minimum number of milliseconds required to pass to allow the fetching of IAM to occur.
     */
    var fetchIAMMinInterval: Long
        get() = getProperty(::fetchIAMMinInterval.name) { 30000 }
        set(value) { setProperty(::fetchIAMMinInterval.name, value) }
}