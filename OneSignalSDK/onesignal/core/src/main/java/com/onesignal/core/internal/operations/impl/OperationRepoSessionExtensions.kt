package com.onesignal.core.internal.operations.impl

import com.onesignal.core.internal.operations.Operation
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.session.internal.session.operations.CreateSessionOperation
import com.onesignal.session.internal.session.operations.UpdateSessionOperation

/**
 * Session operations accumulate while offline, one or more per app open. Coalescing keeps
 * each session to at most a create and one update, and the cap bounds how many sessions are kept.
 * All functions here must be called while the queue is synchronized.
 */

internal const val MAX_QUEUED_SESSION_OPERATIONS = 100

/**
 * Folds queued, unsent updates for the same session into [incoming], which then carries the
 * highest duration. An update that ends the session replaces them the same way.
 *
 * @return false if [incoming] should be discarded because the session's end is already queued.
 */
internal fun OperationRepo.coalesceSessionUpdate(
    incoming: OperationRepo.OperationQueueItem,
    operationModelStore: OperationModelStore,
): Boolean {
    val op = incoming.operation as UpdateSessionOperation
    val queued =
        queue.filter {
            val other = it.operation
            other is UpdateSessionOperation && other.appId == op.appId && other.sessionId == op.sessionId
        }
    if (queued.any { (it.operation as UpdateSessionOperation).isEnd }) {
        Logging.debug("OperationRepo: session ${op.sessionId} already has an end queued, discarding $op")
        incoming.waiter?.wake(false)
        return false
    }
    queued.maxOfOrNull { (it.operation as UpdateSessionOperation).activeDuration }?.let {
        op.activeDuration = maxOf(op.activeDuration, it)
    }
    queued.forEach { item ->
        if (incoming.waiter == null) {
            incoming.waiter = item.waiter
        } else {
            item.waiter?.wake(true)
        }
    }
    if (queued.isNotEmpty()) {
        removeQueuedOperations(queued, operationModelStore)
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
    if (added !is CreateSessionOperation && added !is UpdateSessionOperation) return

    while (true) {
        val sessionItems = queue.filter { it.operation is CreateSessionOperation || it.operation is UpdateSessionOperation }
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

private fun OperationRepo.removeQueuedOperations(
    items: List<OperationRepo.OperationQueueItem>,
    operationModelStore: OperationModelStore,
) {
    queue.removeAll(items)
    items.forEach { operationModelStore.remove(it.operation.id) }
}
