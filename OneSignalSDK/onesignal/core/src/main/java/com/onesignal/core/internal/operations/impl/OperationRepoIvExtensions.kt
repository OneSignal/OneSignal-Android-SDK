package com.onesignal.core.internal.operations.impl

import com.onesignal.common.threading.suspendifyOnIO
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.user.internal.jwt.IdentityVerificationGates
import com.onesignal.user.internal.jwt.JwtTokenStore

/**
 * IV-specific behavior layered onto [OperationRepo]. Base-class dispatch sites are gated
 * on [IdentityVerificationGates.newCodePathsRun]; these extensions internally short-circuit
 * on [IdentityVerificationGates.ivBehaviorActive] to stay inert for Phase 3 users (new code
 * path on, IV behavior off).
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
): Boolean {
    if (!IdentityVerificationGates.ivBehaviorActive) return true
    if (!op.requiresJwt) return true
    val externalId = op.externalId ?: return false
    return jwtTokenStore.getJwt(externalId) != null
}

/**
 * Handles a [com.onesignal.core.internal.operations.ExecutionResult.FAIL_UNAUTHORIZED] response
 * when IV behavior is active. Invalidates the JWT for the failing op's externalId, re-queues the
 * ops (waiter wake with `false` so `enqueueAndWait` callers don't hang), and fires the
 * configured handler so the developer can supply a fresh JWT.
 *
 * Returns `true` if IV-specific handling was applied (caller should stop processing this result),
 * or `false` when IV behavior is inactive or the op is anonymous (caller falls back to default
 * drop-on-fail handling).
 */
internal fun OperationRepo.handleFailUnauthorized(
    startingOp: OperationRepo.OperationQueueItem,
    ops: List<OperationRepo.OperationQueueItem>,
    jwtTokenStore: JwtTokenStore,
    jwtInvalidatedHandler: ((String) -> Unit)?,
): Boolean {
    if (!IdentityVerificationGates.ivBehaviorActive) return false
    val externalId = startingOp.operation.externalId ?: return false

    jwtTokenStore.invalidateJwt(externalId)
    Logging.warn(
        "Operation execution failed with 401 Unauthorized, JWT invalidated for user: $externalId. " +
            "Operations re-queued.",
    )
    // Fire the handler BEFORE waking waiters — otherwise an `enqueueAndWait` caller
    // could return before the handler has a chance to propagate to the app.
    if (jwtInvalidatedHandler != null) {
        try {
            jwtInvalidatedHandler(externalId)
        } catch (t: Throwable) {
            Logging.warn("JWT-invalidated handler threw", t)
        }
    }
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

/**
 * Post-HYDRATE maintenance: scheduled on IO so it runs *after* `loadSavedOperations` populates
 * the queue (fix for an earlier race where the purge ran against an empty in-memory queue on
 * cold start). Force-execute always fires to release the pre-HYDRATE deferral in `getNextOps`.
 */
internal fun OperationRepo.onJwtConfigHydratedIv(ivRequired: Boolean) {
    suspendifyOnIO {
        awaitInitialized()
        if (ivRequired) {
            removeOperationsWithoutExternalId()
        }
        forceExecuteOperations()
    }
}
