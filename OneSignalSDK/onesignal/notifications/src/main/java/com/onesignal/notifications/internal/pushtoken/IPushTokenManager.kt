package com.onesignal.notifications.internal.pushtoken

import com.onesignal.user.internal.subscriptions.SubscriptionStatus

internal interface IPushTokenManager {
    suspend fun retrievePushToken(): PushTokenResponse
}

internal class PushTokenResponse(val token: String?, val status: SubscriptionStatus)
