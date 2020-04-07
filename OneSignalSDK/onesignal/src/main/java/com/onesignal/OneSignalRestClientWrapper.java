package com.onesignal;

import android.support.annotation.NonNull;

import org.json.JSONObject;

class OneSignalRestClientWrapper implements OneSignalAPIClient {

    @Override
    public void put(String url, JSONObject jsonBody, final OneSignalApiResponseHandler responseHandler) {
        OneSignalRestClient.put(url, jsonBody, new OneSignalRestClient.ResponseHandler() {
            @Override
            public void onSuccess(String response) {
                responseHandler.onSuccess(response);
            }

            @Override
            public void onFailure(int statusCode, String response, Throwable throwable) {
                responseHandler.onFailure(statusCode, response, throwable);
            }
        });
    }

    @Override
    public void post(String url, JSONObject jsonBody, final OneSignalApiResponseHandler responseHandler) {
        OneSignalRestClient.post(url, jsonBody, new OneSignalRestClient.ResponseHandler() {
            @Override
            public void onSuccess(String response) {
                responseHandler.onSuccess(response);
            }

            @Override
            public void onFailure(int statusCode, String response, Throwable throwable) {
                responseHandler.onFailure(statusCode, response, throwable);
            }
        });
    }

    @Override
    public void get(String url, final OneSignalApiResponseHandler responseHandler, @NonNull String cacheKey) {
        OneSignalRestClient.get(url, new OneSignalRestClient.ResponseHandler() {
            @Override
            public void onSuccess(String response) {
                responseHandler.onSuccess(response);
            }

            @Override
            public void onFailure(int statusCode, String response, Throwable throwable) {
                responseHandler.onFailure(statusCode, response, throwable);
            }
        }, cacheKey);
    }

    @Override
    public void getSync(String url, final OneSignalApiResponseHandler responseHandler, @NonNull String cacheKey) {
        OneSignalRestClient.getSync(url, new OneSignalRestClient.ResponseHandler() {
            @Override
            public void onSuccess(String response) {
                responseHandler.onSuccess(response);
            }

            @Override
            public void onFailure(int statusCode, String response, Throwable throwable) {
                responseHandler.onFailure(statusCode, response, throwable);
            }
        }, cacheKey);
    }

    @Override
    public void putSync(String url, JSONObject jsonBody, final OneSignalApiResponseHandler responseHandler) {
        OneSignalRestClient.putSync(url, jsonBody, new OneSignalRestClient.ResponseHandler() {
            @Override
            public void onSuccess(String response) {
                responseHandler.onSuccess(response);
            }

            @Override
            public void onFailure(int statusCode, String response, Throwable throwable) {
                responseHandler.onFailure(statusCode, response, throwable);
            }
        });
    }

    @Override
    public void postSync(String url, JSONObject jsonBody, final OneSignalApiResponseHandler responseHandler) {
        OneSignalRestClient.postSync(url, jsonBody, new OneSignalRestClient.ResponseHandler() {
            @Override
            public void onSuccess(String response) {
                responseHandler.onSuccess(response);
            }

            @Override
            public void onFailure(int statusCode, String response, Throwable throwable) {
                responseHandler.onFailure(statusCode, response, throwable);
            }
        });
    }
}
