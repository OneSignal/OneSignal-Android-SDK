package com.onesignal.user.internal

import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.user.internal.subscriptions.SubscriptionModelStore

/**
 * IV-specific behavior for [LogoutHelper]. The base-class call site dispatches via
 * `if (newCodePathsRun) switchUserIv(...) else legacyLogout()`; this extension
 * internally short-circuits on `ivBehaviorActive` to keep Phase 3 users (new code path on,
 * IV behavior off) on the legacy logout flow.
 */

/**
 * Performs the IV-aware logout user-switch when [ivBehaviorActive] is true.
 *
 * Under IV-required, the new device-scoped (anonymous) user can't authenticate against
 * the backend without a JWT. To prevent the model-store listener from generating create-
 * subscription ops that would 401/permanently-block the queue:
 * 1. Mark the current push subscription as internally disabled.
 * 2. Switch users with [UserSwitcher.createAndSwitchToNewUser] in `suppressBackendOperation`
 *    mode so subscription replacement does NOT propagate to listeners that would enqueue
 *    backend ops.
 *
 * Returns `true` when IV-specific handling was applied (caller skips legacy enqueue),
 * or `false` when IV behavior is inactive (caller falls through to the legacy logout).
 */
internal fun switchUserIv(
    userSwitcher: UserSwitcher,
    subscriptionModelStore: SubscriptionModelStore,
    configModel: ConfigModel,
    ivBehaviorActive: Boolean,
): Boolean {
    if (!ivBehaviorActive) return false

    configModel.pushSubscriptionId?.let { pushSubId ->
        subscriptionModelStore.get(pushSubId)?.let { it.isDisabledInternally = true }
    }
    userSwitcher.createAndSwitchToNewUser(suppressBackendOperation = true)
    return true
}
