package com.onesignal;

import org.json.JSONObject;

class OutcomeEventsService {

    /***
     * API endpoint /api/v1/outcomes/outcomes/measure
     */
    void sendOutcomeEvent(JSONObject object, OneSignalRestClient.ResponseHandler responseHandler) {
        OneSignalRestClient.post("outcomes/measure", object, responseHandler);
    }
}
