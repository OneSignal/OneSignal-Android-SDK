package com.onesignal.user.internal

import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.user.internal.subscriptions.SubscriptionModel
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
 * 1. Switch to the anonymous user with [UserSwitcher.createAndSwitchToNewUser] in
 *    `suppressBackendOperation` mode so subscription replacement does not propagate to
 *    listeners that would enqueue backend ops.
 * 2. Mark the new push subscription as internally disabled with [ModelChangeTags.NO_PROPOGATE]
 *    so subsequent property mutations (FCM token refresh, permission change, etc.)
 *    short-circuit through [com.onesignal.user.internal.operations.impl.listeners.SubscriptionModelStoreListener.getSubscriptionEnabledAndStatus]
 *    instead of enqueueing real ops.
 *
 * Order matters: setting the flag on the OLD model first (with default NORMAL tag) would
 * fire `getUpdateOperation` against the OLD user with their still-valid JWT — the listener
 * would build an `UpdateSubscriptionOperation(externalId = OLD)` carrying `(false, UNSUBSCRIBE)`,
 * dispatch it, and unsubscribe the just-departed user server-side.
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

    userSwitcher.createAndSwitchToNewUser(suppressBackendOperation = true)
    configModel.pushSubscriptionId?.let { pushSubId ->
        subscriptionModelStore.get(pushSubId)?.setBooleanProperty(
            SubscriptionModel::isDisabledInternally.name,
            true,
            ModelChangeTags.NO_PROPOGATE,
        )
    }
    return true
}
