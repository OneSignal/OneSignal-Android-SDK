package com.onesignal.notification.internal.pushtoken

import com.onesignal.core.internal.common.events.IEventNotifier

internal interface IPushTokenManager : IEventNotifier<IPushTokenChangedHandler> {
    /** The push token for this device **/
    val pushToken: String?

    suspend fun retrievePushToken()
}

internal interface IPushTokenChangedHandler {
    fun onPushTokenChanged(pushToken: String?)
}
