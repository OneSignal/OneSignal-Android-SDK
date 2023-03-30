package com.onesignal.session.internal.outcomes.impl

import com.onesignal.session.internal.influence.Influence

/**
 * The caching interface for outcomes.
 */
internal interface IOutcomeEventsRepository {
    /**
     * Delete event previously saved via [saveOutcomeEvent]
     */
    suspend fun deleteOldOutcomeEvent(event: OutcomeEventParams)

    /**
     * Save an outcome event to send it on the future
     * For offline mode and contingency of errors
     */
    suspend fun saveOutcomeEvent(eventParams: OutcomeEventParams)

    /**
     * Retrieve all outcome event previously saved via [saveOutcomeEvent]
     * For offline mode and contingency of errors
     */
    suspend fun getAllEventsToSend(): List<OutcomeEventParams>

    /**
     * Save a JSONArray of notification ids as separate items with the unique outcome name
     */
    suspend fun saveUniqueOutcomeEventParams(eventParams: OutcomeEventParams)

    /**
     * Create a JSONArray of not cached notification ids from the unique outcome notifications SQL table
     */
    suspend fun getNotCachedUniqueInfluencesForOutcome(
        name: String,
        influences: List<Influence>,
    ): List<Influence>

    /**
     * Deletes cached unique outcome notifications whose ids do not exist inside of the NotificationTable.TABLE_NAME
     */
    suspend fun cleanCachedUniqueOutcomeEventNotifications()
}
