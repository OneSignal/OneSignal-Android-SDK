package com.onesignal.outcomes.data

import com.onesignal.OneSignalApiResponseHandler
import org.json.JSONObject

interface OutcomeEventsService {
    fun sendOutcomeEvent(jsonObject: JSONObject, responseHandler: OneSignalApiResponseHandler)
}