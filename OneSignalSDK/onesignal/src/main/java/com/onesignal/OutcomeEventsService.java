package com.onesignal;

import org.json.JSONObject;

class OutcomeEventsService {

    /***
     * API endpoint /api/v1/outcomes/{outcome_id}/measure
     */
    void sendOutcomeEvent(String outcomeId, JSONObject object, OneSignalRestClient.ResponseHandler responseHandler) {
        OneSignalRestClient.post("outcomes/" + outcomeId + "/measure", object, responseHandler);
    }
}
