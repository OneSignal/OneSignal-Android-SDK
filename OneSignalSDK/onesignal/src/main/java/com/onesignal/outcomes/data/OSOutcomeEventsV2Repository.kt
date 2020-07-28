package com.onesignal.outcomes.data

import com.onesignal.OSLogger
import com.onesignal.OneSignalApiResponseHandler
import com.onesignal.outcomes.OSOutcomeConstants
import com.onesignal.outcomes.domain.OSOutcomeEventParams
import org.json.JSONException

internal class OSOutcomeEventsV2Repository(logger: OSLogger,
                                           outcomeEventsCache: OSOutcomeEventsCache,
                                           outcomeEventsService: OutcomeEventsService) : OSOutcomeEventsRepository(logger, outcomeEventsCache, outcomeEventsService) {
    override fun requestMeasureOutcomeEvent(appId: String, deviceType: Int, event: OSOutcomeEventParams, responseHandler: OneSignalApiResponseHandler) {
        try {
            event.toJSONObject()
                    .put(OSOutcomeConstants.APP_ID, appId)
                    .put(OSOutcomeConstants.DEVICE_TYPE, deviceType)
                    .also { jsonObject ->
                        outcomeEventsService.sendOutcomeEvent(jsonObject, responseHandler)
                    }
        } catch (e: JSONException) {
            logger.error("Generating indirect outcome:JSON Failed.", e)
        }
    }
}