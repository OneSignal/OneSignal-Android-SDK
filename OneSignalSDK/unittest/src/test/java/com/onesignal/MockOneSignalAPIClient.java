package com.onesignal;

import android.support.annotation.NonNull;

import org.json.JSONObject;

public class MockOneSignalAPIClient implements OneSignalAPIClient {

    private static final int FAIL_STATUS_CODE = 500;
    private static final String FAIL_STRING_RESPONSE = "error";

    private boolean success;
    private JSONObject lastJsonObjectSent = new JSONObject();

    /**
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
    public void put(String url, JSONObject jsonBody, OneSignalApiResponseHandler responseHandler) {
        lastJsonObjectSent = jsonBody;
        if (success)
            responseHandler.onSuccess("");
        else
            responseHandler.onFailure(FAIL_STATUS_CODE, FAIL_STRING_RESPONSE, null);
    }

    @Override
    public void post(String url, JSONObject jsonBody, OneSignalApiResponseHandler responseHandler) {
        lastJsonObjectSent = jsonBody;
        if (success)
            responseHandler.onSuccess("");
        else
            responseHandler.onFailure(FAIL_STATUS_CODE, FAIL_STRING_RESPONSE, null);
    }

    @Override
    public void get(String url, OneSignalApiResponseHandler responseHandler, @NonNull String cacheKey) {
    }

    @Override
    public void getSync(String url, OneSignalApiResponseHandler responseHandler, @NonNull String cacheKey) {
    }

    @Override
    public void putSync(String url, JSONObject jsonBody, OneSignalApiResponseHandler responseHandler) {
        lastJsonObjectSent = jsonBody;
        if (success)
            responseHandler.onSuccess("");
        else
            responseHandler.onFailure(FAIL_STATUS_CODE, FAIL_STRING_RESPONSE, null);
    }

    @Override
    public void postSync(String url, JSONObject jsonBody, OneSignalApiResponseHandler responseHandler) {
        lastJsonObjectSent = jsonBody;
        if (success)
            responseHandler.onSuccess("");
        else
            responseHandler.onFailure(FAIL_STATUS_CODE, FAIL_STRING_RESPONSE, null);
    }
}
