package com.onesignal.user.internal.subscriptions

import com.onesignal.common.modeling.Model

enum class SubscriptionType() {
    EMAIL,
    SMS,
    PUSH,
}

enum class SubscriptionStatus(val value: Int) {
    /** The subscription is currently enabled and all is well */
    SUBSCRIBED(1),

    /** The subscription is not enabled because the device user has restricted permissions */
    NO_PERMISSION(0),

    /** The subscription is not enabled because the app has disabled the subscription */
    UNSUBSCRIBE(-2),

    /** The subscription is not enabled due to a missing Jetpack/AndroidX library */
    MISSING_JETPACK_LIBRARY(-3),

    /** The subscription is not enabled due to a missing firebase library */
    MISSING_FIREBASE_FCM_LIBRARY(-4),

    /** The subscription is not enabled due to an outdated Jetpack/AndroidX library */
    OUTDATED_JETPACK_LIBRARY(-5),

    /** The subscription is not enabled due to the FCM sender being invalid */
    INVALID_FCM_SENDER_ID(-6),

    /** The subscription is not enabled due to an outdated google play services library */
    OUTDATED_GOOGLE_PLAY_SERVICES_APP(-7),

    /** The subscription is not enabled due to an FCM initialization error, this can be retried */
    FIREBASE_FCM_INIT_ERROR(-8),

    /** The subscription is not enabled due to an FCM service unavailable error, this can be retried */
    FIREBASE_FCM_ERROR_IOEXCEPTION_SERVICE_NOT_AVAILABLE(-9),

    // -10 is a server side detection only from FCM that the app is no longer installed

    /** The subscription is not enabled due to any other FCM IOException, this can be retried */
    FIREBASE_FCM_ERROR_IOEXCEPTION_OTHER(-11),

    /** The subscription is not enabled due to any other FCM Exception, this can be retried */
    FIREBASE_FCM_ERROR_MISC_EXCEPTION(-12),

    // -13 to -21, -23, and -24 reserved for other platforms

    /** The subscription is not enabled because it was unsubscribed by hand from the dashboard */
    MANUALLY_UNSUBSCRIBED(-22),

    /** The subscription is not enabled due the an HMS timeout, this can be retried */
    HMS_TOKEN_TIMEOUT(-25),

    // Most likely missing "client/app_id".
    // Check that there is "apply plugin: 'com.huawei.agconnect'" in your app/build.gradle

    /** The subscription is not enabled due to the HMS arguments being invalid */
    HMS_ARGUMENTS_INVALID(-26),

    /** The subscription is not enabled due to the HMS exception, this can be retried */
    HMS_API_EXCEPTION_OTHER(-27),

    /** The subscription is not enabled due to a missing HMS library */
    MISSING_HMS_PUSHKIT_LIBRARY(-28),

    /** The subscription is not enabled due to an FCM authentication failed IOException, this can be retried */
    FIREBASE_FCM_ERROR_IOEXCEPTION_AUTHENTICATION_FAILED(-29),

    /** The subscription is not enabled because it was disabled through the REST API */
    DISABLED_FROM_REST_API(-31),

    /** The subscription is not enabled due to some other (unknown locally) error */
    ERROR(9999),
    ;

    /**
     * `true` when this status represents a *transient, retryable* failure to fetch a push token
     * from FCM/HMS (e.g. a temporary IOException, service-unavailable, or init error)
     *
     * A retryable token error means "we momentarily couldn't reach FCM/HMS", not "this device can
     * no longer receive push". It must therefore never downgrade a subscription that is already
     * [SUBSCRIBED] with a valid token — the next successful registration recovers the token.
     */
    val isRetryableTokenError: Boolean
        get() = this in RETRYABLE_TOKEN_ERRORS

    companion object {
        /**
         * Transient, retryable token-fetch failures. These are the statuses that should not
         * overwrite a healthy push subscription.
         */
        private val RETRYABLE_TOKEN_ERRORS =
            setOf(
                FIREBASE_FCM_INIT_ERROR, // -8
                FIREBASE_FCM_ERROR_IOEXCEPTION_SERVICE_NOT_AVAILABLE, // -9
                FIREBASE_FCM_ERROR_IOEXCEPTION_OTHER, // -11
                FIREBASE_FCM_ERROR_MISC_EXCEPTION, // -12
                HMS_TOKEN_TIMEOUT, // -25
                HMS_API_EXCEPTION_OTHER, // -27
                FIREBASE_FCM_ERROR_IOEXCEPTION_AUTHENTICATION_FAILED, // -29
            )

        /**
         * The codes the server owns, meaning the app owner turned this subscription off remotely.
         * The SDK never derives either from device state, and every other server-reported error
         * code stays device-recoverable.
         */
        private val REMOTE_DISABLES =
            setOf(
                MANUALLY_UNSUBSCRIBED, // -22
                DISABLED_FROM_REST_API, // -31
            )

        /**
         * The status for a remote-disable code, or `null` when [value] is not one. The two codes
         * stay distinct in [SubscriptionModel.remoteDisabledReason] and on the wire, so callers
         * report back the exact code the server sent rather than collapsing them.
         */
        fun remoteDisableStatus(value: Int?): SubscriptionStatus? {
            return REMOTE_DISABLES.firstOrNull { it.value == value }
        }

        /**
         * True when [value] is one of the codes for a subscription the app owner disabled
         * remotely. The SDK treats them the same because both mean the server turned this off.
         */
        fun isRemoteDisable(value: Int?): Boolean {
            return remoteDisableStatus(value) != null
        }

        fun fromInt(value: Int): SubscriptionStatus? {
            return SubscriptionStatus.values().firstOrNull { it.value == value }
        }
    }
}

class SubscriptionModel : Model() {
    /**
     * Reflects user preference only, defaults true.
     * The public API for [IPushSubscription.optedIn] considers this value AND permission.
     */
    var optedIn: Boolean
        get() = getBooleanProperty(::optedIn.name)
        set(value) {
            setBooleanProperty(::optedIn.name, value)
        }

    /**
     * Internal-only flag (not surfaced via the public API) used to suppress backend
     * subscription operations for this model. Set to `true` on logout under Identity
     * Verification: the new device-scoped (anonymous) user can't authenticate without
     * a JWT, so the SDK must not generate create-subscription ops for it. The
     * [SubscriptionModelStoreListener] honors this flag by short-circuiting to
     * `(enabled = false, status = UNSUBSCRIBE)` regardless of [optedIn] / [status].
     *
     * Defaults to `false`. On the next login, [com.onesignal.user.internal.UserSwitcher]
     * creates a fresh model that does not carry this flag, restoring the real state.
     */
    var isDisabledInternally: Boolean
        get() = getBooleanProperty(::isDisabledInternally.name) { false }
        set(value) {
            setBooleanProperty(::isDisabledInternally.name, value)
        }

    /**
     * The code for a subscription the app owner turned off remotely, either by hand from the
     * dashboard ([SubscriptionStatus.MANUALLY_UNSUBSCRIBED], -22) or through the REST API
     * ([SubscriptionStatus.DISABLED_FROM_REST_API], -31), or 0 when the server has not disabled
     * this subscription. Both codes mean the same thing to the SDK but are recorded separately, so
     * payloads echo back the one the server sent. Hydrated by RefreshUser and never derived from
     * device state; while set, [SubscriptionModelStoreListener] reports `enabled = false` with the
     * matching status so subscription payloads don't re-enable a suppressed subscription. Cleared
     * when the server reports any other state, or by [IPushSubscription.optIn].
     */
    var remoteDisabledReason: Int
        get() = getIntProperty(::remoteDisabledReason.name) { 0 }
        set(value) {
            setIntProperty(::remoteDisabledReason.name, value)
        }

    /**
     * True from [IPushSubscription.optIn] until the server reports this subscription in any state
     * other than a remote disable. Every opt-in sends a subscription update, and a fetch that
     * started before that update went out still reports the disable the opt-in cleared; while
     * this is set, RefreshUser leaves [remoteDisabledReason] alone instead of recording that
     * stale answer. Memory only, since a fresh process has no update in flight to protect.
     */
    @Volatile
    var remoteDisableClearedByUser: Boolean = false

    var type: SubscriptionType
        get() = getEnumProperty(::type.name)
        set(value) {
            setEnumProperty(::type.name, value)
        }

    var address: String
        get() = getStringProperty(::address.name)
        set(value) {
            setStringProperty(::address.name, value)
        }

    /**
     * This reflects the "device-level" subscription status.
     *
     * For example, if [IPushSubscription.optOut] is called, the SDK sends UNSUBSCRIBE(-2) to the server.
     * However, locally on the model, we still keep the existing status.
     * It is necessary when [IPushSubscription.optIn] is called again to know the true device status.
     */
    var status: SubscriptionStatus
        get() {
            if (!hasProperty(::status.name)) {
                setEnumProperty(::status.name, SubscriptionStatus.SUBSCRIBED)
            }

            // A persisted name this build's enum lacks reads as SUBSCRIBED instead of throwing.
            return getOptEnumProperty<SubscriptionStatus>(::status.name) ?: SubscriptionStatus.SUBSCRIBED
        }
        set(value) {
            setEnumProperty(::status.name, value)
        }

    // Prior to v5.0.5, we did not save the following properties, so we must default get() to ""
    var sdk: String
        get() = getStringProperty(::sdk.name) { "" }
        set(value) {
            setStringProperty(::sdk.name, value)
        }

    var deviceOS: String
        get() = getStringProperty(::deviceOS.name) { "" }
        set(value) {
            setStringProperty(::deviceOS.name, value)
        }

    var carrier: String
        get() = getStringProperty(::carrier.name) { "" }
        set(value) {
            setStringProperty(::carrier.name, value)
        }

    var appVersion: String
        get() = getStringProperty(::appVersion.name) { "" }
        set(value) {
            setStringProperty(::appVersion.name, value)
        }
}
