package com.onesignal.common.consistency.models

import kotlinx.coroutines.CompletableDeferred

interface IConsistencyManager<K : Enum<K>> {
    /**
     * Set method to update the offset based on the key.
     * Params:
     *  id: String - the index of the offset map (e.g., onesignalId)
     *  key: OffsetKey - corresponds to the operation for which we have a read-your-write token
     *  value: Long? - the offset (read-your-write token)
     */
    suspend fun setOffset(
        id: String,
        key: K,
        value: Long?,
    )

    /**
     * Register a condition with its corresponding deferred action. Returns a deferred condition.
     * Params:
     *  condition: ICondition - the condition to be registered
     * Returns: CompletableDeferred<Long?> - a deferred action that completes when the condition is met
     */
    suspend fun registerCondition(condition: ICondition<K>): CompletableDeferred<Long?>
}