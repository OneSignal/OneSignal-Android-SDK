package com.onesignal.onesignal.core.internal.outcomes.data

import com.onesignal.onesignal.core.internal.influence.Influence
import com.onesignal.onesignal.core.internal.logging.Logging
import com.onesignal.onesignal.core.internal.outcomes.IOutcomeEventsCache
import com.onesignal.onesignal.core.internal.outcomes.IOutcomeEventsRepository
import com.onesignal.onesignal.core.internal.outcomes.IOutcomeEventsBackend
import com.onesignal.onesignal.core.internal.outcomes.OutcomeEventParams

internal abstract class OSOutcomeEventsRepository(
    private val outcomeEventsCache: IOutcomeEventsCache,
    val outcomeEventsService: IOutcomeEventsBackend
    ) : IOutcomeEventsRepository {

    override fun getSavedOutcomeEvents(): List<OutcomeEventParams> = outcomeEventsCache.getAllEventsToSend()

    override fun saveOutcomeEvent(event: OutcomeEventParams) {
        outcomeEventsCache.saveOutcomeEvent(event)
    }

    override fun removeEvent(outcomeEvent: OutcomeEventParams) {
        outcomeEventsCache.deleteOldOutcomeEvent(outcomeEvent)
    }

    override fun saveUniqueOutcomeNotifications(eventParams: OutcomeEventParams) {
        outcomeEventsCache.saveUniqueOutcomeEventParams(eventParams)
    }

    override fun getNotCachedUniqueOutcome(name: String, influences: List<Influence>): List<Influence> {
        val influencesNotCached = outcomeEventsCache.getNotCachedUniqueInfluencesForOutcome(name, influences)
        Logging.debug("OneSignal getNotCachedUniqueOutcome influences: $influencesNotCached")
        return influencesNotCached
    }

    override fun getUnattributedUniqueOutcomeEventsSent(): Set<String>? {
        val unattributedUniqueOutcomeEvents = outcomeEventsCache.unattributedUniqueOutcomeEventsSentByChannel
        Logging.debug("OneSignal getUnattributedUniqueOutcomeEventsSentByChannel: $unattributedUniqueOutcomeEvents")
        return unattributedUniqueOutcomeEvents
    }

    override fun saveUnattributedUniqueOutcomeEventsSent(unattributedUniqueOutcomeEvents: Set<String>) {
        Logging.debug("OneSignal save unattributedUniqueOutcomeEvents: $unattributedUniqueOutcomeEvents")
        outcomeEventsCache.saveUnattributedUniqueOutcomeEventsSentByChannel(unattributedUniqueOutcomeEvents)
    }

    override suspend fun cleanCachedUniqueOutcomeEventNotifications(notificationTableName: String, notificationIdColumnName: String) {
        outcomeEventsCache.cleanCachedUniqueOutcomeEventNotifications(notificationTableName, notificationIdColumnName);
    }
}