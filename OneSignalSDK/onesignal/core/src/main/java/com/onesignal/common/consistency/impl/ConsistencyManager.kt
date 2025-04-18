package com.onesignal.common.consistency.impl

import com.onesignal.common.consistency.RywData
import com.onesignal.common.consistency.models.ICondition
import com.onesignal.common.consistency.models.IConsistencyKeyEnum
import com.onesignal.common.consistency.models.IConsistencyManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manages read-your-write tokens for more accurate segment membership
 * calculation. Uses customizable conditions that block retrieval of the newest token until met.
 *
 * Usage:
 *  val consistencyManager = ConsistencyManager<MyEnum>()
 *  val updateConditionDeferred = consistencyManager.registerCondition(MyCustomCondition())
 *  val rywToken = updateConditionDeferred.await()
 */
class ConsistencyManager : IConsistencyManager {
    private val mutex = Mutex()
    private val indexedTokens: MutableMap<String, MutableMap<IConsistencyKeyEnum, RywData>> = mutableMapOf()
    private val conditions: MutableList<Pair<ICondition, CompletableDeferred<RywData?>>> =
        mutableListOf()

    /**
     * Set method to update the token based on the key.
     *  Params:
     *      id: String - the index of the token map (e.g. onesignalId)
     *      key: K - corresponds to the operation for which we have a read-your-write token
     *      value: String? - the token (read-your-write token)
     */
    override suspend fun setRywData(
        id: String,
        key: IConsistencyKeyEnum,
        value: RywData,
    ) {
        mutex.withLock {
            val rywTokens = indexedTokens.getOrPut(id) { mutableMapOf() }
            rywTokens[key] = value
            checkConditionsAndComplete()
        }
    }

    /**
     * Register a condition with its corresponding deferred action. Returns a deferred condition.
     */
    override suspend fun getRywDataFromAwaitableCondition(condition: ICondition): CompletableDeferred<RywData?> {
        mutex.withLock {
            val deferred = CompletableDeferred<RywData?>()
            val pair = Pair(condition, deferred)
            conditions.add(pair)
            checkConditionsAndComplete()
            return deferred
        }
    }

    override suspend fun resolveConditionsWithID(id: String) {
        val completedConditions = mutableListOf<Pair<ICondition, CompletableDeferred<RywData?>>>()

        for ((condition, deferred) in conditions) {
            if (condition.id == id) {
                if (!deferred.isCompleted) {
                    deferred.complete(null)
                }
            }
            completedConditions.add(Pair(condition, deferred))
        }

        // Remove completed conditions from the list
        conditions.removeAll(completedConditions)
    }

    /**
     * IMPORTANT: calling code should be protected by mutex to avoid potential inconsistencies
     */
    private fun checkConditionsAndComplete() {
        val completedConditions = mutableListOf<Pair<ICondition, CompletableDeferred<RywData?>>>()

        for ((condition, deferred) in conditions) {
            if (condition.isMet(indexedTokens)) {
                val rywDataForNewestToken = condition.getRywData(indexedTokens)
                if (!deferred.isCompleted) {
                    deferred.complete(rywDataForNewestToken)
                }
                completedConditions.add(Pair(condition, deferred))
            }
        }

        // Remove completed conditions from the list
        conditions.removeAll(completedConditions)
    }
}
