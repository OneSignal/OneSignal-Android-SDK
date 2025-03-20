package com.onesignal.user.internal.migrations

import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.user.internal.subscriptions.SubscriptionModelStore
import com.onesignal.user.internal.subscriptions.SubscriptionType

/**
 * Purpose: Automatically recovers a missing push sub ID in the config model store due
 * to a bug migrating from the SDK 5.2.0-beta
 */
class RecoverConfigPushSubscription(
    private val _configModelStore: ConfigModelStore,
    private val _subscriptionModelStore: SubscriptionModelStore,
) : MigrationRecovery() {
    val activePushSubscription by lazy { _subscriptionModelStore.list().firstOrNull { it.type == SubscriptionType.PUSH } }

    override fun isInBadState(): Boolean {
        val isPushSubIdMissing = _configModelStore.model.pushSubscriptionId == null
        return isPushSubIdMissing && activePushSubscription != null
    }

    override fun recover() {
        _configModelStore.model.pushSubscriptionId = activePushSubscription?.id
    }

    override fun recoveryMessage(): String {
        return "Recovering missing push subscription ID in the config model store."
    }
}
