package com.onesignal.core.internal.outcomes.impl

import com.onesignal.core.internal.backend.http.HttpResponse
import com.onesignal.core.internal.logging.Logging
import com.onesignal.core.internal.outcomes.IOutcomeEventsBackend
import com.onesignal.core.internal.outcomes.IOutcomeEventsCache
import com.onesignal.core.internal.outcomes.OutcomeConstants
import com.onesignal.core.internal.outcomes.OutcomeEventParams
import org.json.JSONException

internal class OSOutcomeEventsV2Repository(
    outcomeEventsCache: IOutcomeEventsCache,
    outcomeEventsService: IOutcomeEventsBackend
) : OSOutcomeEventsRepository(outcomeEventsCache, outcomeEventsService) {
    override suspend fun requestMeasureOutcomeEvent(appId: String, deviceType: Int, event: OutcomeEventParams): HttpResponse? {
        try {
            val jsonObject = event.toJSONObject()
                .put(OutcomeConstants.APP_ID, appId)
                .put(OutcomeConstants.DEVICE_TYPE, deviceType)
            return outcomeEventsService.sendOutcomeEvent(jsonObject)
        } catch (e: JSONException) {
            Logging.error("Generating indirect outcome:JSON Failed.", e)
        }
        return null
    }
}
