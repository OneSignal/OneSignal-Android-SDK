package com.onesignal.core.internal.operations.impl

import com.onesignal.common.IDManager
import com.onesignal.core.internal.operations.Operation
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.session.internal.session.operations.CreateSessionOperation
import com.onesignal.session.internal.session.operations.SessionOperation
import com.onesignal.session.internal.session.operations.UpdateSessionOperation
import com.onesignal.user.internal.operations.LoginUserFromSubscriptionOperation
import com.onesignal.user.internal.operations.LoginUserOperation

/**
 * Session operations accumulate while offline, one or more per app open. Coalescing keeps
 * each session to at most a create and one update, and the cap bounds how many sessions are kept.
 * All functions here must be called while the queue is synchronized.
 */

internal const val MAX_QUEUED_SESSION_OPERATIONS = 100

/**
 * @return false if [incoming] was discarded because it could never be sent; see [admitSessionUpdate].
 */
internal fun OperationRepo.admitSessionOperation(
    incoming: OperationRepo.OperationQueueItem,
    operationModelStore: OperationModelStore,
): Boolean {
    val op = incoming.operation as SessionOperation
    if (isUnsendable(op)) {
        Logging.debug("OperationRepo: discarding $op, its anonymous user is never created under identity verification")
        incoming.waiter?.wake(false)
        return false
    }
    return op !is UpdateSessionOperation || admitSessionUpdate(incoming, operationModelStore)
}

/**
 * Drops queued session operations that can never be sent, after anonymous operations are purged.
 */
internal fun OperationRepo.dropUnsendableSessionOperations(operationModelStore: OperationModelStore) {
    val unsendable = queue.filter { (it.operation as? SessionOperation)?.let(::isUnsendable) == true }
    if (unsendable.isEmpty()) return

    Logging.warn("OperationRepo: dropping ${unsendable.size} session operation(s) for an anonymous user under identity verification")
    unsendable.forEach { it.waiter?.wake(false) }
    removeQueuedOperations(unsendable, operationModelStore)
    dropOrphanedSessionUpdates(unsendable, operationModelStore)
}

/**
 * Under required identity verification the anonymous user is never created, so a local OneSignal ID
 * that no identified login will create never gets a backend ID.
 */
private fun OperationRepo.isUnsendable(op: SessionOperation): Boolean =
    isInitialized &&
        isIdentityVerificationRequired &&
        IDManager.isLocalId(op.onesignalId) &&
        (queue + inFlight).none {
            val createsUser =
                when (val login = it.operation) {
                    is LoginUserOperation -> login.onesignalId == op.onesignalId
                    is LoginUserFromSubscriptionOperation -> login.onesignalId == op.onesignalId
                    else -> false
                }
            createsUser && it.operation.externalId != null
        }

/**
 * Folds queued, unsent updates for the same session into [incoming], which then carries the
 * highest duration. An update that ends the session replaces them the same way.
 *
 * @return false if [incoming] should be discarded: the session's end is already queued or
 * sending, or its session ID is local and no create for it remains, so it could never be sent.
 */
private fun OperationRepo.admitSessionUpdate(
    incoming: OperationRepo.OperationQueueItem,
    operationModelStore: OperationModelStore,
): Boolean {
    val op = incoming.operation as UpdateSessionOperation
    val pending = (queue + inFlight).map { it.operation }
    val hasEnd = pending.any { it is UpdateSessionOperation && it.isSameSession(op) && it.isEnd }
    // Saved ops may not be loaded yet, so a missing create only proves an orphan after initialization.
    val isOrphaned =
        isInitialized &&
            IDManager.isLocalId(op.sessionId) &&
            pending.none { it is CreateSessionOperation && it.localSessionId == op.sessionId }
    if (hasEnd || isOrphaned) {
        Logging.debug("OperationRepo: discarding $op, session ended: $hasEnd, never created: $isOrphaned")
        incoming.waiter?.wake(false)
        return false
    }

    val queued = queue.mapNotNull { item -> (item.operation as? UpdateSessionOperation)?.takeIf { it.isSameSession(op) } }
    queued.maxOfOrNull { it.activeDuration }?.let { op.activeDuration = maxOf(op.activeDuration, it) }
    // Otherwise a session that keeps enqueueing updates would never reach the attempt limit.
    queued.maxOfOrNull { it.failedAttempts }?.let { op.failedAttempts = maxOf(op.failedAttempts, it) }
    val queuedItems = queue.filter { it.operation in queued }
    queuedItems.forEach { item ->
        if (incoming.waiter == null) {
            incoming.waiter = item.waiter
        } else {
            item.waiter?.wake(true)
        }
    }
    if (queuedItems.isNotEmpty()) {
        removeQueuedOperations(queuedItems, operationModelStore)
        Logging.debug("OperationRepo: coalesced ${queued.size} queued update(s) for session ${op.sessionId}")
    }
    return true
}

/**
 * Drops the oldest session operations once more than [MAX_QUEUED_SESSION_OPERATIONS] are queued.
 */
internal fun OperationRepo.enforceSessionOperationCap(
    added: Operation,
    operationModelStore: OperationModelStore,
) {
    if (added !is SessionOperation) return

    while (true) {
        val sessionItems = queue.filter { it.operation is SessionOperation }
        if (sessionItems.size <= MAX_QUEUED_SESSION_OPERATIONS) return

        val oldest = sessionItems.first()
        Logging.warn("OperationRepo: more than $MAX_QUEUED_SESSION_OPERATIONS session operations queued, dropping oldest: ${oldest.operation}")
        oldest.waiter?.wake(false)
        removeQueuedOperations(listOf(oldest), operationModelStore)
        dropOrphanedSessionUpdates(listOf(oldest), operationModelStore)
    }
}

/**
 * Updates for a session whose create was dropped can never get a backend session ID, so drop them too.
 */
internal fun OperationRepo.dropOrphanedSessionUpdates(
    dropped: List<OperationRepo.OperationQueueItem>,
    operationModelStore: OperationModelStore,
) {
    val localSessionIds = dropped.mapNotNull { (it.operation as? CreateSessionOperation)?.localSessionId }.toSet()
    if (localSessionIds.isEmpty()) return

    val orphans = queue.filter { (it.operation as? UpdateSessionOperation)?.sessionId in localSessionIds }
    if (orphans.isEmpty()) return

    Logging.warn("OperationRepo: dropping ${orphans.size} update(s) for sessions that were never created")
    orphans.forEach { it.waiter?.wake(false) }
    removeQueuedOperations(orphans, operationModelStore)
}

private fun UpdateSessionOperation.isSameSession(other: UpdateSessionOperation) = appId == other.appId && sessionId == other.sessionId

private fun OperationRepo.removeQueuedOperations(
    items: List<OperationRepo.OperationQueueItem>,
    operationModelStore: OperationModelStore,
) {
    queue.removeAll(items)
    items.forEach { operationModelStore.remove(it.operation.id) }
}
