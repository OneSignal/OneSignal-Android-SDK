package com.onesignal.onesignal.core.internal.outcomes

import androidx.annotation.WorkerThread
import com.onesignal.onesignal.core.internal.influence.Influence

interface IOutcomeEventsCache {
    val isOutcomesV2ServiceEnabled: Boolean
    val unattributedUniqueOutcomeEventsSentByChannel: Set<String>?

    fun saveUnattributedUniqueOutcomeEventsSentByChannel(unattributedUniqueOutcomeEvents: Set<String>?)

    /**
     * Delete event from the DB
     */
    @WorkerThread
    fun deleteOldOutcomeEvent(event: OutcomeEventParams)

    /**
     * Save an outcome event to send it on the future
     * For offline mode and contingency of errors
     */
    @WorkerThread
    fun saveOutcomeEvent(eventParams: OutcomeEventParams)

    /**
     * Save an outcome event to send it on the future
     * For offline mode and contingency of errors
     */
    @WorkerThread
    fun getAllEventsToSend(): List<OutcomeEventParams>

    /**
     * Save a JSONArray of notification ids as separate items with the unique outcome name
     */
    @WorkerThread
    fun saveUniqueOutcomeEventParams(eventParams: OutcomeEventParams)

    /**
     * Create a JSONArray of not cached notification ids from the unique outcome notifications SQL table
     */
    @WorkerThread
    fun getNotCachedUniqueInfluencesForOutcome(
        name: String,
        influences: List<Influence>
    ): List<Influence>

    /**
     * Deletes cached unique outcome notifications whose ids do not exist inside of the NotificationTable.TABLE_NAME
     */
    suspend fun cleanCachedUniqueOutcomeEventNotifications(
        notificationTableName: String,
        notificationIdColumnName: String
    )
}