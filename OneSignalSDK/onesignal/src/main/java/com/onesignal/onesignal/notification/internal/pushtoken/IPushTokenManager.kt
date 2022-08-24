package com.onesignal.onesignal.notification.internal.pushtoken

import com.onesignal.onesignal.core.internal.common.events.*

interface IPushTokenManager : IEventNotifier<IPushTokenChangedHandler> {
    /** The push token for this device **/
    val pushToken: String?

    suspend fun retrievePushToken()
}

interface IPushTokenChangedHandler {
    fun onPushTokenChanged(pushToken: String?)
}
