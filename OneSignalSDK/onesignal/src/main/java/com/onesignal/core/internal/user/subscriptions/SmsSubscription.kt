package com.onesignal.core.internal.user.subscriptions

import com.onesignal.core.user.subscriptions.ISmsSubscription
import java.util.UUID

class SmsSubscription(
    id: UUID,
    override val number: String
) : Subscription(id), ISmsSubscription
