package com.onesignal.common.consistency.models

import com.onesignal.common.consistency.RywData
import kotlinx.coroutines.CompletableDeferred

interface IConsistencyManager {
    /**
     * Set method to update the RYW token based on the key.
     * Params:
     *  id: String - the index of the RYW token map (e.g., onesignalId)
     *  key: IConsistencyKeyEnum - corresponds to the operation for which we have a read-your-write token
     *  value: String? - the read-your-write token
     */
    suspend fun setRywData(
        id: String,
        key: IConsistencyKeyEnum,
        value: RywData,
    )

    /**
     * Register a condition with its corresponding deferred action. Returns a deferred condition.
     * Params:
     *  condition: ICondition - the condition to be registered
     * Returns: CompletableDeferred<String?> - a deferred action that completes when the condition is met
     */
    suspend fun getRywDataFromAwaitableCondition(condition: ICondition): CompletableDeferred<RywData?>

    /**
     * Resolve all conditions with a specific ID
     */
    suspend fun resolveConditionsWithID(id: String)
}
