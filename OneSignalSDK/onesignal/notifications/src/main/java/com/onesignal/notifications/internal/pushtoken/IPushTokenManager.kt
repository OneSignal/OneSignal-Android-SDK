package com.onesignal.notifications.internal.pushtoken

import com.onesignal.common.events.IEventNotifier
import com.onesignal.notifications.internal.registration.IPushRegistrator

internal interface IPushTokenManager : IEventNotifier<IPushTokenChangedHandler> {
    /** The push token for this device **/
    val pushToken: String?
    val pushTokenStatus: IPushRegistrator.RegisterStatus
    suspend fun retrievePushToken()
}

internal interface IPushTokenChangedHandler {
    fun onPushTokenChanged(pushToken: String?, pushTokenStatus: IPushRegistrator.RegisterStatus)
}
