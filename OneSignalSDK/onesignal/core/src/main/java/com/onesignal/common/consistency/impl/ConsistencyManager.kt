package com.onesignal.common.consistency.impl

import com.onesignal.common.consistency.models.ICondition
import com.onesignal.common.consistency.models.IConsistencyManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manages offsets that function as read-your-write tokens for more accurate segment membership
 * calculation. Uses customizable conditions that block retrieval of the newest offset until met.
 *
 * Usage:
 *  val consistencyManager = ConsistencyManager<MyEnum>()
 *  val updateConditionDeferred = consistencyManager.registerCondition(MyCustomCondition())
 *  val newestUpdateOffset = updateConditionDeferred.await()
 */
class ConsistencyManager<K : Enum<K>> : IConsistencyManager<K> {
    private val mutex = Mutex()
    private val indexedOffsets: MutableMap<String, MutableMap<K, Long?>> = mutableMapOf()
    private val conditions: MutableList<Pair<ICondition<K>, CompletableDeferred<Long?>>> =
        mutableListOf()

    /**
     * Set method to update the offset based on the key.
     *  Params:
     *      id: String - the index of the offset map (e.g. onesignalId)
     *      key: K - corresponds to the operation for which we have a read-your-write token
     *      value: Long? - the offset (read-your-write token)
     */
    override suspend fun setOffset(
        id: String,
        key: K,
        value: Long?,
    ) {
        mutex.withLock {
            val offsets = indexedOffsets.getOrPut(id) { mutableMapOf() }
            offsets[key] = value
            checkConditionsAndComplete()
        }
    }

    /**
     * Register a condition with its corresponding deferred action. Returns a deferred condition.
     */
    override suspend fun registerCondition(condition: ICondition<K>): CompletableDeferred<Long?> {
        mutex.withLock {
            val deferred = CompletableDeferred<Long?>()
            val pair = Pair(condition, deferred)
            conditions.add(pair)
            checkConditionsAndComplete()
            return deferred
        }
    }

    private fun checkConditionsAndComplete() {
        val completedConditions = mutableListOf<Pair<ICondition<K>, CompletableDeferred<Long?>>>()

        for ((condition, deferred) in conditions) {
            if (condition.isMet(indexedOffsets)) {
                val newestOffset = condition.getNewestOffset(indexedOffsets)
                if (!deferred.isCompleted) {
                    deferred.complete(newestOffset)
                }
                completedConditions.add(Pair(condition, deferred))
            }
        }

        // Remove completed conditions from the list
        conditions.removeAll(completedConditions)
    }
}
