package com.onesignal;

import android.os.Bundle;
import android.support.annotation.NonNull;

import org.json.JSONObject;

import java.util.concurrent.ConcurrentHashMap;

class OutcomeEventsController {

    static class OutcomeException extends Exception {

        OutcomeException(String message) {
            super(message);
        }
    }

    private final OutcomeEventsRepository outcomeEventsRepository;
    private final OSSessionManager osSessionManager;

    private final ConcurrentHashMap<String, JSONObject> eventsNotSent = new ConcurrentHashMap<>();

    OutcomeEventsController(OSSessionManager osSessionManager) {
        this.outcomeEventsRepository = new OutcomeEventsRepository();
        this.osSessionManager = osSessionManager;
    }

    OutcomeEventsController(OSSessionManager osSessionManager, OutcomeEventsRepository outcomeEventsRepository) {
        this.outcomeEventsRepository = outcomeEventsRepository;
        this.osSessionManager = osSessionManager;
    }

    void sendOutcomeEvent(@NonNull String name) {
        OSSessionManager.Session session = osSessionManager.getSession();
        String appId = OneSignal.appId;
        int deviceType = new OSUtils().getDeviceType();

        OneSignalRestClient.ResponseHandler responseHandler = new OneSignalRestClient.ResponseHandler() {
            @Override
            void onSuccess(String response) {
                super.onSuccess(response);
            }

            @Override
            void onFailure(int statusCode, String response, Throwable throwable) {
                super.onFailure(statusCode, response, throwable);
            }
        };

        switch (session) {
            case DIRECT:
                String notificationId = osSessionManager.getNotificationId();
                if (notificationId != null && !notificationId.isEmpty()) {
                    outcomeEventsRepository.requestMeasureDirectOutcomeEvent(name, appId, notificationId, deviceType, responseHandler);
                    break;
                }
                // If notification from some reason is null or empty we should send anyways the event as indirect
            case INDIRECT:
                outcomeEventsRepository.requestMeasureIndirectOutcomeEvent(name, appId, deviceType, responseHandler);
                break;
            case UNATTRIBUTED:
                outcomeEventsRepository.requestMeasureUnattributedOutcomeEvent(name, appId, deviceType, responseHandler);
                break;
        }
    }

    void sendOutcomeEvent(@NonNull String name, int value) {
        //TODO when backend changes are done
    }

    void sendOutcomeEvent(@NonNull String name, @NonNull String value) throws OutcomeException {
        if (value.isEmpty()) {
            throw new OutcomeException("Value must not be empty");
        }
        //TODO when backend changes are done
    }

    void sendOutcomeEvent(@NonNull String name, @NonNull Bundle params) {
        //TODO when backend changes are done
    }

    private void sendOutcomeEvent() {
        String outcomeId = "";
        OSSessionManager.Session session = osSessionManager.getSession();
        outcomeEventsRepository.requestMeasureDirectOutcomeEvent(outcomeId, OneSignal.appId, "",
                new OSUtils().getDeviceType(), new OneSignalRestClient.ResponseHandler() {
                    @Override
                    void onSuccess(String response) {
                        super.onSuccess(response);
                        OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "Outcome sent successfully");
                    }

                    @Override
                    void onFailure(int statusCode, String response, Throwable throwable) {
                        super.onFailure(statusCode, response, throwable);
                        OneSignal.Log(OneSignal.LOG_LEVEL.WARN, "Failed outcome event request. statusCode: " + statusCode + "\nresponse: " + response);
                    }
                });
    }
}
