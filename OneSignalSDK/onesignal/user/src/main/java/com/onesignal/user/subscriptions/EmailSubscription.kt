package com.onesignal.user.subscriptions

class EmailSubscription(
        id: String,
        enabled: Boolean,
        val email: String
        ) : Subscription(id, enabled)
