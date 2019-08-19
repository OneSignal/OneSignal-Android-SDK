package com.onesignal;

import android.os.Bundle;
import android.os.Process;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.List;
import java.util.Set;

class OutcomeEventsController {

    static class OutcomeException extends Exception {

        OutcomeException(String message) {
            super(message);
        }
    }

    private Set<String> eventsSent = OSUtils.newConcurrentSet();

    @NonNull
    private final OutcomeEventsRepository outcomeEventsRepository;
    @NonNull
    private final OSSessionManager osSessionManager;
    @Nullable
    private OneSignal.OutcomeSettings outcomeSettings;

    OutcomeEventsController(@NonNull OSSessionManager osSessionManager, @NonNull OutcomeEventsRepository outcomeEventsRepository) {
        this.outcomeEventsRepository = outcomeEventsRepository;
        this.osSessionManager = osSessionManager;
    }

    OutcomeEventsController(@NonNull OSSessionManager osSessionManager, @NonNull OneSignalDbHelper dbHelper, @Nullable OneSignal.OutcomeSettings outcomeSettings) {
        this.outcomeEventsRepository = new OutcomeEventsRepository(dbHelper);
        this.osSessionManager = osSessionManager;
        this.outcomeSettings = outcomeSettings;
    }

    void setOutcomeSettings(@Nullable OneSignal.OutcomeSettings outcomeSettings) {
        this.outcomeSettings = outcomeSettings;
    }

    /**
     * Clean events sent
     */
    void clearOutcomes() {
        eventsSent = OSUtils.newConcurrentSet();
    }

    /**
     * Send all the outcomes that from some reason failed
     */
    void sendSavedOutcomes() {
        if (outcomeSettings != null && !outcomeSettings.isCacheActive())
            return;

        new Thread(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setPriority(Process.THREAD_PRIORITY_BACKGROUND);

                List<OutcomeEvent> outcomeEvents = outcomeEventsRepository.getSavedOutcomeEvents();
                for (OutcomeEvent event : outcomeEvents) {
                    sendSavedOutcomeEvent(event);
                }
            }
        }, "OS_SEND_SAVED_OUTCOMES").start();
    }

    private void sendSavedOutcomeEvent(@NonNull final OutcomeEvent event) {
        OSSessionManager.Session session = event.getSession();
        int deviceType = new OSUtils().getDeviceType();
        String appId = OneSignal.appId;

        OneSignalRestClient.ResponseHandler responseHandler = new OneSignalRestClient.ResponseHandler() {
            @Override
            void onSuccess(String response) {
                super.onSuccess(response);
                outcomeEventsRepository.removeEvent(event);
            }
        };

        switch (session) {
            case DIRECT:
                outcomeEventsRepository.requestMeasureDirectOutcomeEvent(appId, deviceType, event, responseHandler);
                break;
            case INDIRECT:
                outcomeEventsRepository.requestMeasureIndirectOutcomeEvent(appId, deviceType, event, responseHandler);
                break;
            case UNATTRIBUTED:
                outcomeEventsRepository.requestMeasureUnattributedOutcomeEvent(appId, deviceType, event, responseHandler);
                break;
        }
    }

    void sendUniqueOutcomeEvent(@NonNull final String name, @Nullable OneSignal.OutcomeCallback callback) {
        if (eventsSent.contains(name)) {
            //Event already sent
            return;
        }
        sendOutcomeEvent(name, callback);
        //Even if the outcome fail we don't attempt to send it again because we have cache
        eventsSent.add(name);
    }

    void sendOutcomeEvent(@NonNull final String name, @Nullable final OneSignal.OutcomeCallback callback) {
        sendOutcomeEvent(name, null, callback);
    }

    void sendOutcomeEvent(@NonNull String name, float value, @Nullable final OneSignal.OutcomeCallback callback) {
        OutcomeParams params = OutcomeParams.Builder
                .newInstance()
                .setWeight(value)
                .build();
        sendOutcomeEvent(name, params, callback);
    }

    void sendOutcomeEvent(@NonNull String name, @NonNull String value) throws OutcomeException {
        if (value.isEmpty()) {
            throw new OutcomeException("Value must not be empty");
        }
        //TODO when backend changes are done
    }

    private void sendOutcomeEvent(@NonNull final String name, @Nullable final OutcomeParams params, @Nullable final OneSignal.OutcomeCallback callback) {
        final String notificationId = osSessionManager.getNotificationId();
        final OSSessionManager.Session session = notificationId != null ? osSessionManager.getSession() : OSSessionManager.Session.UNATTRIBUTED;
        final long timestamp = System.currentTimeMillis() / 1000;

        int deviceType = new OSUtils().getDeviceType();
        String appId = OneSignal.appId;

        OneSignalRestClient.ResponseHandler responseHandler = new OneSignalRestClient.ResponseHandler() {
            @Override
            void onSuccess(String response) {
                super.onSuccess(response);
                if (callback != null)
                    callback.onOutcomeSuccess(name);
            }

            @Override
            void onFailure(int statusCode, String response, Throwable throwable) {
                super.onFailure(statusCode, response, throwable);
                if (isCacheActive()) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            Thread.currentThread().setPriority(Process.THREAD_PRIORITY_BACKGROUND);
                            outcomeEventsRepository.saveOutcomeEvent(new OutcomeEvent(session, notificationId, name, timestamp, params));
                        }
                    }, "OS_SAVE_OUTCOMES").start();
                } else {
                    eventsSent.remove(name);
                }

                if (callback != null)
                    callback.onOutcomeFail(statusCode, response);
            }
        };

        switch (session) {
            case DIRECT:
                outcomeEventsRepository.requestMeasureDirectOutcomeEvent(name, params, appId, notificationId, deviceType, responseHandler);
                break;
            case INDIRECT:
                outcomeEventsRepository.requestMeasureIndirectOutcomeEvent(name, params, appId, notificationId, deviceType, responseHandler);
                break;
            case UNATTRIBUTED:
                outcomeEventsRepository.requestMeasureUnattributedOutcomeEvent(name, params, appId, deviceType, responseHandler);
                break;
        }
    }

    void sendOutcomeEvent(@NonNull String name, @NonNull Bundle params) {
        //TODO when backend changes are done
    }

    boolean isCacheActive() {
        return outcomeSettings == null || outcomeSettings.isCacheActive();
    }
}
