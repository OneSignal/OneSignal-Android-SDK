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

    // -13 to -24 reserved for other platforms

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

    /** The subscription is not enabled due to an FCM authentication failed IOException */
    FIREBASE_FCM_ERROR_IOEXCEPTION_AUTHENTICATION_FAILED(-29),

    /** The subscription is not enabled because the app has disabled the subscription via API */
    DISABLED_FROM_REST_API_DEFAULT_REASON(-30),

    /** The subscription is not enabled due to some other (unknown locally) error */
    ERROR(9999),
    ;

    companion object {
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

            return getEnumProperty(::status.name)
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
