package com.onesignal.common.consistency.impl

import com.onesignal.common.IConsistencyManager
import com.onesignal.common.consistency.ICondition
import com.onesignal.common.consistency.OffsetKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manages offsets that function as read-your-write tokens for more accurate segment membership
 * calculation. Uses customizable conditions that block retrieval of the newest offset until met.
 *
 * Usage:
 *  val consistencyManager = ConsistencyManager()
 *  val updateConditionDeferred = consistencyManager.registerCondition(MyCustomCondition())
 *  val newestUpdateOffset = updateConditionDeferred.await()
 */
class ConsistencyManager : IConsistencyManager {
    private val mutex = Mutex()
    private val indexedOffsets: MutableMap<String, MutableMap<OffsetKey, Long?>> = mutableMapOf()
    private val conditions: MutableList<Pair<ICondition, CompletableDeferred<Long?>>> =
        mutableListOf()

    /**
     * Set method to update the offset based on the key.
     *  Params:
     *      id: String - the index of the offset map (e.g. onesignalId)
     *      key: OffsetKey - corresponds to the operation for which we have a read-your-write token
     *      value: Long? - the offset (read-your-write token)
     */
    override suspend fun setOffset(id: String, key: OffsetKey, value: Long?) {
        mutex.withLock {
            val offsets = indexedOffsets.getOrPut(id) { mutableMapOf() }
            offsets[key] = value
            checkConditionsAndComplete()
        }
    }

    /**
     * Register a condition with its corresponding deferred action. Returns a deferred condition.
     */
    override fun registerCondition(condition: ICondition): CompletableDeferred<Long?> {
        val deferred = CompletableDeferred<Long?>()
        val pair = Pair(condition, deferred)
        conditions.add(pair)
        checkConditionsAndComplete()
        return deferred
    }

    private fun checkConditionsAndComplete() {
        for ((condition, deferred) in conditions) {
            if (condition.isMet(indexedOffsets)) {
                val newestOffset = condition.getNewestOffset(indexedOffsets)
                if (!deferred.isCompleted) {
                    deferred.complete(newestOffset)
                }
            }
        }
    }
}
