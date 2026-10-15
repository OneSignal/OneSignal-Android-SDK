package com.onesignal.core.internal.operations.impl

import com.onesignal.core.internal.operations.Operation
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.session.internal.session.operations.CreateSessionOperation
import com.onesignal.session.internal.session.operations.UpdateSessionOperation

/**
 * Queue rules for sessions API operations in [OperationRepo]. Every function must be called while
 * holding the queue lock. Functions return the queue items they removed; the caller drops them
 * from the store and wakes their waiters.
 */
internal class SessionQueuePolicy(
    private val maxQueued: Int = MAX_QUEUED_SESSION_OPERATIONS,
) {
    // Recent local -> backend ID translations, for updates built from a local ID just before the
    // create succeeded but enqueued after the queue was translated.
    private val recentTranslations =
        object : LinkedHashMap<String, String>() {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > MAX_RECENT_TRANSLATIONS
        }

    // Sessions that take no more updates: ended, or their create was dropped. The queue alone
    // misses an end that is executing.
    private val closedSessions =
        object : LinkedHashMap<String, Unit>() {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?) = size > MAX_CLOSED_SESSIONS
        }

    fun recordTranslations(idTranslations: Map<String, String>) {
        recentTranslations.putAll(idTranslations)
        idTranslations.forEach { (localId, backendId) ->
            if (closedSessions.remove(localId) != null) closedSessions[backendId] = Unit
        }
    }

    class EnqueueResult(
        val accept: Boolean,
        val removed: List<OperationRepo.OperationQueueItem> = emptyList(),
    )

    fun onEnqueue(
        queue: MutableList<OperationRepo.OperationQueueItem>,
        incoming: OperationRepo.OperationQueueItem,
    ): EnqueueResult {
        val op = incoming.operation
        if (!op.isSessionOperation) return EnqueueResult(accept = true)
        op.translateIds(recentTranslations)

        return if (op is UpdateSessionOperation && (op.sessionId in closedSessions || hasQueuedEnd(queue, op.sessionId))) {
            Logging.debug("SessionQueuePolicy: session ${op.sessionId} is closed, dropping $op")
            EnqueueResult(accept = false)
        } else {
            admit(queue, incoming)
        }
    }

    private fun admit(
        queue: MutableList<OperationRepo.OperationQueueItem>,
        incoming: OperationRepo.OperationQueueItem,
    ): EnqueueResult {
        val op = incoming.operation
        val removed = mutableListOf<OperationRepo.OperationQueueItem>()
        if (op is UpdateSessionOperation) {
            val unsent = queue.filter { (it.operation as? UpdateSessionOperation)?.sessionId == op.sessionId }
            // Durations are cumulative, so the newest update carries everything the unsent ones would.
            unsent.forEach {
                val replaced = it.operation as UpdateSessionOperation
                op.activeDuration = maxOf(op.activeDuration, replaced.activeDuration)
                incoming.retries = maxOf(incoming.retries, it.retries)
                incoming.retryAt = maxOf(incoming.retryAt, it.retryAt)
                val waiters = synchronized(it) { it.waiters.toList().also { _ -> it.waiters.clear() } }
                incoming.attachOrWake(waiters)
            }
            queue.removeAll(unsent)
            removed.addAll(unsent)
        }

        var accept = true
        while (queue.count { it.operation.isSessionOperation } + (if (accept) 1 else 0) > maxQueued) {
            val oldestSession = queue.first { it.operation.isSessionOperation }.operation.sessionKey
            val dropped = queue.filter { it.operation.sessionKey == oldestSession }
            Logging.warn("SessionQueuePolicy: more than $maxQueued sessions API operations queued, dropping session $oldestSession")
            queue.removeAll(dropped)
            removed.addAll(dropped)
            if (op.sessionKey == oldestSession) accept = false
        }
        if (accept && op is UpdateSessionOperation && op.isEnd) closedSessions[op.sessionId] = Unit
        return EnqueueResult(accept, removed)
    }

    /** Removes and rejects updates that can never send because [dropped] was their session's create. */
    fun onDropped(
        queue: MutableList<OperationRepo.OperationQueueItem>,
        dropped: Operation,
    ): List<OperationRepo.OperationQueueItem> {
        if (dropped !is CreateSessionOperation) return emptyList()
        closedSessions[dropped.localSessionId] = Unit
        val orphans = queue.filter { (it.operation as? UpdateSessionOperation)?.sessionId == dropped.localSessionId }
        if (orphans.isNotEmpty()) {
            Logging.debug("SessionQueuePolicy: create for session ${dropped.localSessionId} was dropped, dropping ${orphans.size} updates")
            queue.removeAll(orphans)
        }
        return orphans
    }

    private fun hasQueuedEnd(
        queue: List<OperationRepo.OperationQueueItem>,
        sessionId: String,
    ) = queue.any { (it.operation as? UpdateSessionOperation)?.let { update -> update.sessionId == sessionId && update.isEnd } == true }

    private val Operation.isSessionOperation get() = this is CreateSessionOperation || this is UpdateSessionOperation

    private val Operation.sessionKey: String?
        get() =
            when (this) {
                is CreateSessionOperation -> localSessionId
                is UpdateSessionOperation -> sessionId
                else -> null
            }

    companion object {
        const val MAX_QUEUED_SESSION_OPERATIONS = 100
        private const val MAX_RECENT_TRANSLATIONS = 50
        private const val MAX_CLOSED_SESSIONS = 50
    }
}
