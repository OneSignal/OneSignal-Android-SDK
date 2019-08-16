package com.onesignal;

import org.json.JSONObject;

public class MockOutcomeEventsService extends OutcomeEventsService {

    private static final int FAIL_STATUS_CODE = 500;
    private static final String FAIL_STRING_RESPONSE = "error";

    private boolean success;

    /**
     * Set to fail or success the api service call
     */
    public void setSuccess(boolean success) {
        this.success = success;
    }

    @Override
    void sendOutcomeEvent(JSONObject object, OneSignalRestClient.ResponseHandler responseHandler) {
        if (success)
            responseHandler.onSuccess("");
        else
            responseHandler.onFailure(FAIL_STATUS_CODE, FAIL_STRING_RESPONSE, null);
    }
}
