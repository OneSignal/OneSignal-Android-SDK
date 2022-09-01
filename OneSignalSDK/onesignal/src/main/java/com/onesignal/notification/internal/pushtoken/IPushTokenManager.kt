package com.onesignal.notification.internal.pushtoken

import com.onesignal.core.internal.common.events.IEventNotifier

interface IPushTokenManager : IEventNotifier<IPushTokenChangedHandler> {
    /** The push token for this device **/
    val pushToken: String?

    suspend fun retrievePushToken()
}

interface IPushTokenChangedHandler {
    fun onPushTokenChanged(pushToken: String?)
}
