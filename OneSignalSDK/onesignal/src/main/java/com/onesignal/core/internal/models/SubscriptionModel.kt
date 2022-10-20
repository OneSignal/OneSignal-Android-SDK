package com.onesignal.core.internal.models

import com.onesignal.core.internal.modeling.Model

internal enum class SubscriptionType() {
    EMAIL,
    SMS,
    PUSH
}

internal class SubscriptionModel : Model() {
    var enabled: Boolean
        get() = getProperty(::enabled.name)
        set(value) { setProperty(::enabled.name, value) }

    var type: SubscriptionType
        get() = SubscriptionType.valueOf(getProperty(::type.name))
        set(value) { setProperty(::type.name, value.toString()) }

    var address: String
        get() = getProperty(::address.name)
        set(value) { setProperty(::address.name, value) }

    var status: Int
        get() = getProperty(::status.name) { STATUS_SUBSCRIBED }
        set(value) { setProperty(::status.name, value) }

    companion object {
        const val STATUS_SUBSCRIBED = 1
        const val STATUS_NO_PERMISSION = 0
        const val STATUS_UNSUBSCRIBE = -2
        const val STATUS_MISSING_ANDROID_SUPPORT_LIBRARY = -3
        const val STATUS_MISSING_FIREBASE_FCM_LIBRARY = -4
        const val STATUS_OUTDATED_ANDROID_SUPPORT_LIBRARY = -5
        const val STATUS_INVALID_FCM_SENDER_ID = -6
        const val STATUS_OUTDATED_GOOGLE_PLAY_SERVICES_APP = -7
        const val STATUS_FIREBASE_FCM_INIT_ERROR = -8
        const val STATUS_FIREBASE_FCM_ERROR_IOEXCEPTION_SERVICE_NOT_AVAILABLE = -9

        // -10 is a server side detection only from FCM that the app is no longer installed
        const val STATUS_FIREBASE_FCM_ERROR_IOEXCEPTION_OTHER = -11
        const val STATUS_FIREBASE_FCM_ERROR_MISC_EXCEPTION = -12

        // -13 to -24 reserved for other platforms
        const val STATUS_HMS_TOKEN_TIMEOUT = -25

        // Most likely missing "client/app_id".
        // Check that there is "apply plugin: 'com.huawei.agconnect'" in your app/build.gradle
        const val STATUS_HMS_ARGUMENTS_INVALID = -26
        const val STATUS_HMS_API_EXCEPTION_OTHER = -27
        const val STATUS_MISSING_HMS_PUSHKIT_LIBRARY = -28
        const val STATUS_FIREBASE_FCM_ERROR_IOEXCEPTION_AUTHENTICATION_FAILED = -29
    }
}
