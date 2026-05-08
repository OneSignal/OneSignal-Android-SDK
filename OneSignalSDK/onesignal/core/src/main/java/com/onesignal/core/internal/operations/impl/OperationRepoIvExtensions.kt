package com.onesignal.core.internal.operations.impl

import com.onesignal.debug.internal.logging.Logging
import com.onesignal.user.internal.jwt.JwtTokenStore

/**
 * IV-specific behavior layered onto [OperationRepo]. Base-class dispatch sites are gated on
 * `IdentityVerificationService.newCodePathsRun`; the caller passes `ivBehaviorActive` through so
 * these extensions can short-circuit and stay inert for Phase 3 users (new code path on, IV
 * behavior off).
 */

/**
 * Returns `true` if [op] may execute given current IV state.
 *
 * - IV behavior inactive → always `true` (no gating; new paths run but behavior is same as old).
 * - Op opts out via `requiresJwt = false` → `true`.
 * - Anonymous op while IV active → `false` (can't authenticate without an externalId).
 * - Otherwise → `true` iff a JWT is currently stored for the op's externalId.
 */
internal fun OperationRepo.hasValidJwtIfRequired(
    jwtTokenStore: JwtTokenStore,
    op: com.onesignal.core.internal.operations.Operation,
    ivBehaviorActive: Boolean,
): Boolean {
    if (!ivBehaviorActive || !op.requiresJwt) return true
    val externalId = op.externalId ?: return false
    return jwtTokenStore.getJwt(externalId) != null
}

/**
 * Handles a [com.onesignal.core.internal.operations.ExecutionResult.FAIL_UNAUTHORIZED] response
 * when IV behavior is active. Invalidates the JWT for the failing op's externalId
 * and re-queues the ops (waiter wake with `false` so `enqueueAndWait`
 * callers don't hang).
 *
 * Returns `true` if IV-specific handling was applied (caller should stop processing this result),
 * or `false` when IV behavior is inactive or the op is anonymous (caller falls back to default
 * drop-on-fail handling).
 */
internal fun OperationRepo.handleFailUnauthorized(
    startingOp: OperationRepo.OperationQueueItem,
    ops: List<OperationRepo.OperationQueueItem>,
    jwtTokenStore: JwtTokenStore,
    ivBehaviorActive: Boolean,
): Boolean {
    if (!ivBehaviorActive) return false
    val externalId = startingOp.operation.externalId ?: return false

    // Schedules an async fire of onUserJwtInvalidated to subscribers via
    // OneSignalDispatchers.launchOnDefault — the developer-facing listener invocation is
    // NOT ordered with respect to the waiter.wake below; awaiting `enqueueAndWait` callers
    // may resume before, after, or concurrent with the listener.
    jwtTokenStore.invalidateJwt(externalId)
    Logging.info(
        "Operation execution failed with 401 Unauthorized, JWT invalidated for user: $externalId. " +
            "Operations re-queued.",
    )
    // Wake enqueueAndWait callers; re-queue with waiter = null because the original waiter
    // is already woken.
    ops.forEach { it.waiter?.wake(false) }
    synchronized(queue) {
        ops.reversed().forEach {
            queue.add(0, OperationRepo.OperationQueueItem(it.operation, waiter = null, bucket = it.bucket, retries = it.retries))
        }
    }
    return true
}
