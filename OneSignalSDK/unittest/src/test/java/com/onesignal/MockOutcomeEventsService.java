package com.onesignal;

import org.json.JSONObject;

public class MockOutcomeEventsService extends OutcomeEventsService {

    private static final int FAIL_STATUS_CODE = 500;
    private static final String FAIL_STRING_RESPONSE = "error";

    private boolean success;
    private JSONObject lastJsonObjectSent = new JSONObject();

    /**
     * Set to fail or success the api service call
     */
    public void setSuccess(boolean success) {
        this.success = success;
    }

    public void resetLastJsonObjectSent() {
        this.lastJsonObjectSent = null;
    }

    public String getLastJsonObjectSent() {
        return lastJsonObjectSent.toString();
    }

    @Override
    void sendOutcomeEvent(JSONObject object, OneSignalRestClient.ResponseHandler responseHandler) {
        lastJsonObjectSent = object;
        if (success)
            responseHandler.onSuccess("");
        else
            responseHandler.onFailure(FAIL_STATUS_CODE, FAIL_STRING_RESPONSE, null);
    }
}
