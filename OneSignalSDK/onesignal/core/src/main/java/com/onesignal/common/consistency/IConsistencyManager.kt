package com.onesignal.common

import com.onesignal.common.consistency.ICondition
import com.onesignal.common.consistency.OffsetKey
import kotlinx.coroutines.CompletableDeferred

interface IConsistencyManager {

    /**
     * Set method to update the offset based on the key.
     * Params:
     *  id: String - the index of the offset map (e.g., onesignalId)
     *  key: OffsetKey - corresponds to the operation for which we have a read-your-write token
     *  value: Long? - the offset (read-your-write token)
     */
    suspend fun setOffset(id: String, key: OffsetKey, value: Long?)

    /**
     * Register a condition with its corresponding deferred action. Returns a deferred condition.
     * Params:
     *  condition: ICondition - the condition to be registered
     * Returns: CompletableDeferred<Long?> - a deferred action that completes when the condition is met
     */
    fun registerCondition(condition: ICondition): CompletableDeferred<Long?>
}
