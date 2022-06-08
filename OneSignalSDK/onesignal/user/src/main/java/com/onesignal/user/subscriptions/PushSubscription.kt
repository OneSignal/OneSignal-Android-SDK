package com.onesignal.user.subscriptions

class PushSubscription(
        id: String,
        enabled: Boolean,
        val pushToken: String,
        val unsubscribeWhenNotificationsAreDisabled: Boolean
        ) : Subscription(id, enabled) {
}
