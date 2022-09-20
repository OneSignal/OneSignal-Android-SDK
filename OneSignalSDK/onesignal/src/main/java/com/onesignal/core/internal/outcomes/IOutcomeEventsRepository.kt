package com.onesignal.core.internal.outcomes

import com.onesignal.core.internal.http.HttpResponse
import com.onesignal.core.internal.influence.Influence

internal interface IOutcomeEventsRepository {
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
    suspend fun cleanCachedUniqueOutcomeEventNotifications(notificationTableName: String, notificationIdColumnName: String)
}
