package com.onesignal.onesignal.core.internal.outcomes.impl

import com.onesignal.onesignal.core.internal.backend.http.HttpResponse
import com.onesignal.onesignal.core.internal.influence.InfluenceType
import com.onesignal.onesignal.core.internal.logging.Logging
import com.onesignal.onesignal.core.internal.outcomes.IOutcomeEventsBackend
import com.onesignal.onesignal.core.internal.outcomes.IOutcomeEventsCache
import com.onesignal.onesignal.core.internal.outcomes.OutcomeConstants
import com.onesignal.onesignal.core.internal.outcomes.OutcomeEvent
import com.onesignal.onesignal.core.internal.outcomes.OutcomeEventParams
import org.json.JSONException

internal class OSOutcomeEventsV1Repository(
    outcomeEventsCache: IOutcomeEventsCache,
    outcomeEventsService: IOutcomeEventsBackend
) : OSOutcomeEventsRepository(outcomeEventsCache, outcomeEventsService) {
    override suspend fun requestMeasureOutcomeEvent(appId: String, deviceType: Int, eventParams: OutcomeEventParams): HttpResponse? {
        val event = OutcomeEvent.fromOutcomeEventParamsV2toOutcomeEventV1(eventParams)
        return when (event.session) {
            InfluenceType.DIRECT -> requestMeasureDirectOutcomeEvent(appId, deviceType, event)
            InfluenceType.INDIRECT -> requestMeasureIndirectOutcomeEvent(appId, deviceType, event)
            InfluenceType.UNATTRIBUTED -> requestMeasureUnattributedOutcomeEvent(appId, deviceType, event)
            else -> null
        }
    }

    private suspend fun requestMeasureDirectOutcomeEvent(appId: String, deviceType: Int, event: OutcomeEvent): HttpResponse? {
        try {
            val jsonObject = event.toJSONObjectForMeasure()
                .put(OutcomeConstants.APP_ID, appId)
                .put(OutcomeConstants.DEVICE_TYPE, deviceType)
                .put(OutcomeConstants.DIRECT_PARAM, true)
            outcomeEventsService.sendOutcomeEvent(jsonObject)
        } catch (e: JSONException) {
            Logging.error("Generating direct outcome:JSON Failed.", e)
        }

        return null
    }

    private suspend fun requestMeasureIndirectOutcomeEvent(appId: String, deviceType: Int, event: OutcomeEvent): HttpResponse? {
        try {
            val jsonObject = event.toJSONObjectForMeasure()
                .put(OutcomeConstants.APP_ID, appId)
                .put(OutcomeConstants.DEVICE_TYPE, deviceType)
                .put(OutcomeConstants.DIRECT_PARAM, false)
            outcomeEventsService.sendOutcomeEvent(jsonObject)
        } catch (e: JSONException) {
            Logging.error("Generating indirect outcome:JSON Failed.", e)
        }
        return null
    }

    private suspend fun requestMeasureUnattributedOutcomeEvent(appId: String, deviceType: Int, event: OutcomeEvent): HttpResponse? {
        try {
            val jsonObject = event.toJSONObjectForMeasure()
                .put(OutcomeConstants.APP_ID, appId)
                .put(OutcomeConstants.DEVICE_TYPE, deviceType)
            return outcomeEventsService.sendOutcomeEvent(jsonObject)
        } catch (e: JSONException) {
            Logging.error("Generating unattributed outcome:JSON Failed.", e)
        }
        return null
    }
}
