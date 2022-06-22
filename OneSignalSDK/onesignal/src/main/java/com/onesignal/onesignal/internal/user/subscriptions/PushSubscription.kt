package com.onesignal.onesignal.internal.user.subscriptions

import com.onesignal.onesignal.user.subscriptions.IPushSubscription
import java.util.*

class PushSubscription(
        id: UUID,
        enabled: Boolean,
        override val pushToken: String,
        override val isThisDevice: Boolean
        ) : Subscription(id, enabled), IPushSubscription