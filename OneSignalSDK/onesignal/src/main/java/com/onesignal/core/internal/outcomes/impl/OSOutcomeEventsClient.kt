package com.onesignal.core.internal.outcomes.impl

import com.onesignal.core.internal.backend.http.HttpResponse
import com.onesignal.core.internal.backend.http.IHttpClient
import com.onesignal.core.internal.outcomes.IOutcomeEventsBackend
import org.json.JSONObject

internal abstract class OSOutcomeEventsClient(val client: IHttpClient) : IOutcomeEventsBackend {
    abstract override suspend fun sendOutcomeEvent(jsonObject: JSONObject): HttpResponse
}
