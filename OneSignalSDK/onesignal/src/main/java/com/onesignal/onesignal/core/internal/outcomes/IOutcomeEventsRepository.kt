package com.onesignal.onesignal.core.internal.outcomes

import com.onesignal.onesignal.core.internal.backend.http.HttpResponse
import com.onesignal.onesignal.core.internal.influence.Influence

interface IOutcomeEventsRepository {
    suspend fun requestMeasureOutcomeEvent(
        appId: String,
        deviceType: Int,
        event: OutcomeEventParams
    ): HttpResponse?

    fun getSavedOutcomeEvents(): List<OutcomeEventParams>
    fun saveOutcomeEvent(event: OutcomeEventParams)
    fun removeEvent(outcomeEvent: OutcomeEventParams)
    fun saveUniqueOutcomeNotifications(eventParams: OutcomeEventParams)
    fun getNotCachedUniqueOutcome(name: String, influences: List<Influence>): List<Influence>
    fun getUnattributedUniqueOutcomeEventsSent(): Set<String>?
    fun saveUnattributedUniqueOutcomeEventsSent(unattributedUniqueOutcomeEvents: Set<String>)
    fun cleanCachedUniqueOutcomeEventNotifications(notificationTableName: String, notificationIdColumnName: String)
}