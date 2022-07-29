package com.onesignal.onesignal.core.internal.outcomes.data

import com.onesignal.onesignal.core.internal.backend.http.HttpResponse
import com.onesignal.onesignal.core.internal.backend.http.IHttpClient
import com.onesignal.onesignal.core.internal.outcomes.IOutcomeEventsBackend
import org.json.JSONObject

internal abstract class OSOutcomeEventsClient(val client: IHttpClient) : IOutcomeEventsBackend {
    abstract override suspend fun sendOutcomeEvent(jsonObject: JSONObject) : HttpResponse
}