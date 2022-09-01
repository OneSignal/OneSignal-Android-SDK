package com.onesignal.notification.internal.registration

internal interface IPushRegistrator {
    enum class RegisterStatus(val value: Int) {
        PUSH_STATUS_SUBSCRIBED(1),
        PUSH_STATUS_NO_PERMISSION(0),
        PUSH_STATUS_UNSUBSCRIBE(-2),
        PUSH_STATUS_MISSING_ANDROID_SUPPORT_LIBRARY(-3),
        PUSH_STATUS_MISSING_FIREBASE_FCM_LIBRARY(-4),
        PUSH_STATUS_OUTDATED_ANDROID_SUPPORT_LIBRARY(-5),
        PUSH_STATUS_INVALID_FCM_SENDER_ID(-6),
        PUSH_STATUS_OUTDATED_GOOGLE_PLAY_SERVICES_APP(-7),
        PUSH_STATUS_FIREBASE_FCM_INIT_ERROR(-8),
        PUSH_STATUS_FIREBASE_FCM_ERROR_IOEXCEPTION_SERVICE_NOT_AVAILABLE(-9),

        // -10 is a server side detection only from FCM that the app is no longer installed
        PUSH_STATUS_FIREBASE_FCM_ERROR_IOEXCEPTION_OTHER(-11),
        PUSH_STATUS_FIREBASE_FCM_ERROR_MISC_EXCEPTION(-12),

        // -13 to -24 reserved for other platforms
        PUSH_STATUS_HMS_TOKEN_TIMEOUT(-25),

        // Most likely missing "client/app_id".
        // Check that there is "apply plugin: 'com.huawei.agconnect'" in your app/build.gradle
        PUSH_STATUS_HMS_ARGUMENTS_INVALID(-26),
        PUSH_STATUS_HMS_API_EXCEPTION_OTHER(-27),
        PUSH_STATUS_MISSING_HMS_PUSHKIT_LIBRARY(-28),
        PUSH_STATUS_FIREBASE_FCM_ERROR_IOEXCEPTION_AUTHENTICATION_FAILED(-29),

        // Some error
        PUSH_STATUS_ERROR(9999)
    }

    class RegisterResult(val id: String?, val status: RegisterStatus)

    /**
     * Register the provided context for push notifications.
     *
     * @return a [RegisterResult] which describes the result of registration
     */
    suspend fun registerForPush(): RegisterResult
}
