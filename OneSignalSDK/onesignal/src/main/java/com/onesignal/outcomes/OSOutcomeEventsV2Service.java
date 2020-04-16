package com.onesignal.outcomes;

import com.onesignal.OneSignalAPIClient;
import com.onesignal.OneSignalApiResponseHandler;

import org.json.JSONObject;

class OSOutcomeEventsV2Service extends OSOutcomeEventsClient {

    OSOutcomeEventsV2Service(OneSignalAPIClient client) {
        super(client);
    }

    /***
     * API endpoint /api/v1/outcomes/measure_sources
     */
    public void sendOutcomeEvent(JSONObject object, OneSignalApiResponseHandler responseHandler) {
        client.post("outcomes/measure_sources", object, responseHandler);
    }
}
