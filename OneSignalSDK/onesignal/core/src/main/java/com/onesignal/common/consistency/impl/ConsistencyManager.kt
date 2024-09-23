package com.onesignal.common.consistency.impl

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
    private val indexedTokens: MutableMap<String, MutableMap<IConsistencyKeyEnum, String?>> = mutableMapOf()
    private val conditions: MutableList<Pair<ICondition, CompletableDeferred<String?>>> =
        mutableListOf()

    companion object {
        /**
         * To account for the possibility the backend does not provide a token, we can use this
         * constant to set a value and resolve a condition. This is applicable say, when we don't
         * want to block execution if a RYW token is omitted from the backend response
         */
        const val RYW_TOKEN_NOT_PROVIDED = "0"
    }

    /**
     * Set method to update the token based on the key.
     *  Params:
     *      id: String - the index of the token map (e.g. onesignalId)
     *      key: K - corresponds to the operation for which we have a read-your-write token
     *      value: String? - the token (read-your-write token)
     */
    override suspend fun setRywToken(
        id: String,
        key: IConsistencyKeyEnum,
        value: String?,
    ) {
        mutex.withLock {
            val rywTokens = indexedTokens.getOrPut(id) { mutableMapOf() }
            rywTokens[key] = value ?: RYW_TOKEN_NOT_PROVIDED
            checkConditionsAndComplete()
        }
    }

    /**
     * Register a condition with its corresponding deferred action. Returns a deferred condition.
     */
    override suspend fun registerCondition(condition: ICondition): CompletableDeferred<String?> {
        mutex.withLock {
            val deferred = CompletableDeferred<String?>()
            val pair = Pair(condition, deferred)
            conditions.add(pair)
            checkConditionsAndComplete()
            return deferred
        }
    }

    /**
     * IMPORTANT: calling code should be protected by mutex to avoid potential inconsistencies
     */
    private fun checkConditionsAndComplete() {
        val completedConditions = mutableListOf<Pair<ICondition, CompletableDeferred<String?>>>()

        for ((condition, deferred) in conditions) {
            if (condition.isMet(indexedTokens)) {
                val newestToken = condition.getNewestToken(indexedTokens)
                if (!deferred.isCompleted) {
                    deferred.complete(newestToken)
                }
                completedConditions.add(Pair(condition, deferred))
            }
        }

        // Remove completed conditions from the list
        conditions.removeAll(completedConditions)
    }
}
