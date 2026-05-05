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
 * Order matters and is intentional (mirrors reference branches #2599 and #2613):
 * 1. Set `isDisabledInternally = true` on the CURRENT push subscription with the default
 *    NORMAL tag. This propagates through [com.onesignal.user.internal.operations.impl.listeners.SubscriptionModelStoreListener.getUpdateOperation],
 *    which reads the still-current OLD identity and enqueues an `UpdateSubscriptionOperation`
 *    carrying `(enabled = false, status = UNSUBSCRIBE)` — letting the backend know this device's
 *    push subscription is unsubscribing as the user logs out. The OLD user's JWT is still valid
 *    here, so the op dispatches successfully.
 * 2. Switch to the new device-scoped (anonymous) user via
 *    [UserSwitcher.createAndSwitchToNewUser] with `suppressBackendOperation = true` so the
 *    subscription replacement does NOT propagate to listeners — the new anonymous user has no
 *    JWT and any create-subscription op for it would 401 indefinitely.
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
