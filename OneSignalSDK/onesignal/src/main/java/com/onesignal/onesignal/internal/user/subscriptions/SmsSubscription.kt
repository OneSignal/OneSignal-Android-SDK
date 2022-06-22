package com.onesignal.onesignal.internal.user.subscriptions

import com.onesignal.onesignal.user.subscriptions.ISmsSubscription
import java.util.*

class SmsSubscription(
        id: UUID,
        enabled: Boolean,
        override val number: String
        ) : Subscription(id, enabled), ISmsSubscription