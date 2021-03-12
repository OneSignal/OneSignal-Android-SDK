package com.onesignal.outcomes.data

import com.onesignal.OSLogger
import com.onesignal.OneSignalApiResponseHandler
import com.onesignal.influence.domain.OSInfluence
import com.onesignal.outcomes.domain.OSOutcomeEventParams
import com.onesignal.outcomes.domain.OSOutcomeEventsRepository

internal abstract class OSOutcomeEventsRepository(protected val logger: OSLogger,
                                                  private val outcomeEventsCache: OSOutcomeEventsCache,
                                                  val outcomeEventsService: OutcomeEventsService) : OSOutcomeEventsRepository {
    abstract override fun requestMeasureOutcomeEvent(appId: String, deviceType: Int, event: OSOutcomeEventParams, responseHandler: OneSignalApiResponseHandler)

    override fun getSavedOutcomeEvents(): List<OSOutcomeEventParams> = outcomeEventsCache.getAllEventsToSend()

    override fun saveOutcomeEvent(event: OSOutcomeEventParams) {
        outcomeEventsCache.saveOutcomeEvent(event)
    }

    override fun removeEvent(outcomeEvent: OSOutcomeEventParams) {
        outcomeEventsCache.deleteOldOutcomeEvent(outcomeEvent)
    }

    override fun saveUniqueOutcomeNotifications(eventParams: OSOutcomeEventParams) {
        outcomeEventsCache.saveUniqueOutcomeEventParams(eventParams)
    }

    override fun getNotCachedUniqueOutcome(name: String, influences: List<OSInfluence>): List<OSInfluence> {
        val influencesNotCached = outcomeEventsCache.getNotCachedUniqueInfluencesForOutcome(name, influences)
        logger.debug("OneSignal getNotCachedUniqueOutcome influences: $influencesNotCached")
        return influencesNotCached
    }

    override fun getUnattributedUniqueOutcomeEventsSent(): Set<String>? {
        val unattributedUniqueOutcomeEvents = outcomeEventsCache.unattributedUniqueOutcomeEventsSentByChannel
        logger.debug("OneSignal getUnattributedUniqueOutcomeEventsSentByChannel: $unattributedUniqueOutcomeEvents")
        return unattributedUniqueOutcomeEvents
    }

    override fun saveUnattributedUniqueOutcomeEventsSent(unattributedUniqueOutcomeEvents: Set<String>) {
        logger.debug("OneSignal save unattributedUniqueOutcomeEvents: $unattributedUniqueOutcomeEvents")
        outcomeEventsCache.saveUnattributedUniqueOutcomeEventsSentByChannel(unattributedUniqueOutcomeEvents)
    }

    override fun cleanCachedUniqueOutcomeEventNotifications(notificationTableName: String, notificationIdColumnName: String) {
        outcomeEventsCache.cleanCachedUniqueOutcomeEventNotifications(notificationTableName, notificationIdColumnName);
    }
}