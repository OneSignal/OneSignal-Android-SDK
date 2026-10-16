package com.onesignal.core.internal.operations.impl

import com.onesignal.common.IDManager
import com.onesignal.common.modeling.SimpleModelStore
import com.onesignal.core.internal.operations.Operation
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.session.internal.session.operations.CreateSessionOperation
import com.onesignal.session.internal.session.operations.SessionStateModel
import com.onesignal.session.internal.session.operations.UpdateSessionOperation

/**
 * Queue rules for sessions API operations in [OperationRepo]. Every function must be called while
 * holding the queue lock. Functions return the queue items they removed; the caller drops them
 * from the store and wakes their waiters.
 */
internal class SessionQueuePolicy(
    private val maxQueued: Int = MAX_QUEUED_SESSION_OPERATIONS,
    // Without consent HttpClient fails every request, so these must not be stored or sent.
    private val awaitingConsent: () -> Boolean = { false },
    private val sessions: SimpleModelStore<SessionStateModel> = SimpleModelStore({ SessionStateModel() }),
) {
    // Recent local -> backend ID translations, for an operation built from a local ID just before
    // its record was created but enqueued after the queue was translated.
    private val recentTranslations =
        object : LinkedHashMap<String, String>() {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > MAX_RECENT_TRANSLATIONS
        }

    fun recordTranslations(idTranslations: Map<String, String>) {
        recentTranslations.putAll(idTranslations)
        sessions.list().forEach { it.translateIds(idTranslations) }
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

        val result =
            when {
                awaitingConsent() -> {
                    Logging.debug("SessionQueuePolicy: privacy consent not given, dropping $op")
                    EnqueueResult(accept = false)
                }
                op is UpdateSessionOperation -> admitUpdate(queue, incoming, op)
                else -> admit(queue, incoming)
            }
        // Its updates could never be sent.
        if (!result.accept && op is CreateSessionOperation) updateState(op.localSessionId, queue) { it.isClosed = true }
        return result
    }

    private fun admitUpdate(
        queue: MutableList<OperationRepo.OperationQueueItem>,
        incoming: OperationRepo.OperationQueueItem,
        op: UpdateSessionOperation,
    ): EnqueueResult {
        val state = stateFor(op.sessionId)
        state?.serverSessionId?.let { op.translateIds(mapOf(op.sessionId to it)) }
        val rejection =
            when {
                state?.isClosed == true || hasQueuedEnd(queue, op.sessionId) -> "is closed"
                IDManager.isLocalId(op.sessionId) && state?.onesignalId == null -> "has no create"
                else -> null
            }
        if (rejection != null) {
            Logging.debug("SessionQueuePolicy: session ${op.sessionId} $rejection, dropping $op")
            return EnqueueResult(accept = false)
        }
        state?.let { op.useCreateIds(it.onesignalId, it.subscriptionId) }
        return admit(queue, incoming)
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
        if (accept && op is CreateSessionOperation) {
            updateState(op.localSessionId, queue) {
                it.onesignalId = op.onesignalId
                it.subscriptionId = op.subscriptionId
            }
        }
        if (accept && op is UpdateSessionOperation && op.isEnd) updateState(op.sessionId, queue) { it.isClosed = true }
        return EnqueueResult(accept, removed)
    }

    /** Removes and rejects updates that can never send because [dropped] was their session's create. */
    fun onDropped(
        queue: MutableList<OperationRepo.OperationQueueItem>,
        dropped: Operation,
    ): List<OperationRepo.OperationQueueItem> {
        if (dropped !is CreateSessionOperation) return emptyList()
        updateState(dropped.localSessionId, queue) { it.isClosed = true }
        val orphans = queue.filter { (it.operation as? UpdateSessionOperation)?.sessionId == dropped.localSessionId }
        if (orphans.isNotEmpty()) {
            Logging.debug("SessionQueuePolicy: create for session ${dropped.localSessionId} was dropped, dropping ${orphans.size} updates")
            queue.removeAll(orphans)
        }
        return orphans
    }

    private fun stateFor(sessionId: String) = sessions.list().firstOrNull { it.id == sessionId || it.serverSessionId == sessionId }

    private fun updateState(
        sessionKey: String,
        queue: List<OperationRepo.OperationQueueItem>,
        change: (SessionStateModel) -> Unit,
    ) {
        val existing = stateFor(sessionKey)
        if (existing != null) {
            change(existing)
        } else {
            sessions.add(SessionStateModel().also { it.id = sessionKey }.also(change))
        }
        trimSessions(queue, sessionKey)
    }

    /** Drops the oldest sessions past the cap, but never one with operations still queued, which need its state. */
    private fun trimSessions(
        queue: List<OperationRepo.OperationQueueItem>,
        keep: String,
    ) {
        val all = sessions.list()
        val excess = all.size - maxQueued
        if (excess <= 0) return
        val queuedSessions = queue.mapNotNull { it.operation.sessionKey }.toSet() + keep
        all
            .filter { it.id !in queuedSessions && it.serverSessionId !in queuedSessions }
            .take(excess)
            .forEach { sessions.remove(it.id) }
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
    }
}
