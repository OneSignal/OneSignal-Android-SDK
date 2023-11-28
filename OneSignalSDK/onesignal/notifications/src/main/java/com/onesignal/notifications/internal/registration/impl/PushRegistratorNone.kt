package com.onesignal.notifications.internal.registration.impl

import com.onesignal.notifications.internal.registration.IPushRegistrator
import com.onesignal.user.internal.subscriptions.SubscriptionStatus

internal class PushRegistratorNone : IPushRegistrator, IPushRegistratorCallback {
    override suspend fun registerForPush(): IPushRegistrator.RegisterResult {
        return IPushRegistrator.RegisterResult(null, SubscriptionStatus.ERROR)
    }

    override suspend fun fireCallback(id: String?) {}
}
