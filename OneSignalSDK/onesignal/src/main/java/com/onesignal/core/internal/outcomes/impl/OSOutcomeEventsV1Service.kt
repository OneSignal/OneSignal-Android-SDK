package com.onesignal.core.internal.outcomes.impl

import com.onesignal.core.internal.http.HttpResponse
import com.onesignal.core.internal.http.IHttpClient
import org.json.JSONObject

internal class OSOutcomeEventsV1Service(client: IHttpClient) : OSOutcomeEventsClient(client) {
    /***
     * API endpoint /api/v1/outcomes/measure
     */
    override suspend fun sendOutcomeEvent(jsonObject: JSONObject): HttpResponse {
        return client.post("outcomes/measure", jsonObject)
    }
}
