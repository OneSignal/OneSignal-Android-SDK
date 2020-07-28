package com.onesignal.outcomes.data

import com.onesignal.OneSignalAPIClient
import com.onesignal.OneSignalApiResponseHandler
import org.json.JSONObject

internal abstract class OSOutcomeEventsClient(val client: OneSignalAPIClient) : OutcomeEventsService {
    abstract override fun sendOutcomeEvent(jsonObject: JSONObject, responseHandler: OneSignalApiResponseHandler)
}