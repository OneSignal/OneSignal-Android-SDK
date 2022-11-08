package com.onesignal.notifications.internal.registration.impl

import com.onesignal.notifications.internal.registration.IPushRegistrator

internal class PushRegistratorNone() : IPushRegistrator, IPushRegistratorCallback {
    override suspend fun registerForPush(): IPushRegistrator.RegisterResult {
        return IPushRegistrator.RegisterResult(null, IPushRegistrator.RegisterStatus.PUSH_STATUS_ERROR)
    }

    override suspend fun fireCallback(id: String?) {}
}
