package com.onesignal.user.subscriptions

class SmsSubscription(
        id: String,
        enabled: Boolean,
        val number: String
        ) : Subscription(id, enabled) {
}