package com.onesignal.notifications.internal.registration

import com.onesignal.user.internal.subscriptions.SubscriptionStatus

internal interface IPushRegistrator {
    class RegisterResult(val id: String?, val status: SubscriptionStatus)

    /**
     * Register the provided context for push notifications.
     *
     * @return a [RegisterResult] which describes the result of registration
     */
    suspend fun registerForPush(): RegisterResult
}
