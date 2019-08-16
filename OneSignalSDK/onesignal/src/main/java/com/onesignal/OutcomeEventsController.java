package com.onesignal;

import android.os.Bundle;
import android.os.Process;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

class OutcomeEventsController {

    static class OutcomeException extends Exception {

        OutcomeException(String message) {
            super(message);
        }
    }

    private final OutcomeEventsRepository outcomeEventsRepository;
    private final OSSessionManager osSessionManager;

    private Set<OutcomeEvent> eventsSent = Collections.newSetFromMap(new ConcurrentHashMap<OutcomeEvent, Boolean>());

    OutcomeEventsController(OSSessionManager osSessionManager, OneSignalDbHelper dbHelper) {
        this.outcomeEventsRepository = new OutcomeEventsRepository(dbHelper);
        this.osSessionManager = osSessionManager;
    }

    OutcomeEventsController(OSSessionManager osSessionManager, OutcomeEventsRepository outcomeEventsRepository) {
        this.outcomeEventsRepository = outcomeEventsRepository;
        this.osSessionManager = osSessionManager;
    }

    /**
     * Send all the outcomes that from some reason failed
     */
    void sendSavedOutcomes() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setPriority(Process.THREAD_PRIORITY_BACKGROUND);

                List<OutcomeEvent> outcomeEvents = outcomeEventsRepository.getSavedOutcomeEvents();
                for (OutcomeEvent event : outcomeEvents) {
                    sendSavedOutcomeEvent(event);
                }

                outcomeEventsRepository.removeEvents(new ArrayList<>(eventsSent));
                eventsSent.clear();
            }
        }, "OS_SEND_SAVED_OUTCOMES").start();
    }

    private void sendSavedOutcomeEvent(final OutcomeEvent event) {
        OSSessionManager.Session session = event.getSession();
        String name = event.getName();
        String notificationId = event.getNotificationId();

        final long timestamp = event.getTimestamp();

        String appId = OneSignal.appId;
        int deviceType = new OSUtils().getDeviceType();

        OneSignalRestClient.ResponseHandler responseHandler = new OneSignalRestClient.ResponseHandler() {
            @Override
            void onSuccess(String response) {
                super.onSuccess(response);
                eventsSent.add(event);
            }
        };

        switch (session) {
            case DIRECT:
                outcomeEventsRepository.requestMeasureDirectOutcomeEvent(name, appId, notificationId, deviceType, timestamp, responseHandler);
                break;
            case INDIRECT:
                outcomeEventsRepository.requestMeasureIndirectOutcomeEvent(name, appId, notificationId, deviceType, timestamp, responseHandler);
                break;
            case UNATTRIBUTED:
                outcomeEventsRepository.requestMeasureUnattributedOutcomeEvent(name, appId, deviceType, responseHandler);
                break;
        }
    }

    void sendOutcomeEvent(@NonNull final String name) {
        final String notificationId = osSessionManager.getNotificationId();
        final long sessionTimeStamp = System.currentTimeMillis() / 1000;
        OSSessionManager.Session session = osSessionManager.getSession();

        if (notificationId == null || notificationId.isEmpty()) {
            session = OSSessionManager.Session.UNATTRIBUTED;
        }

        final OSSessionManager.Session finalSession = session;

        int deviceType = new OSUtils().getDeviceType();
        String appId = OneSignal.appId;

        OneSignalRestClient.ResponseHandler responseHandler = new OneSignalRestClient.ResponseHandler() {
            @Override
            void onSuccess(String response) {
                super.onSuccess(response);
            }

            @Override
            void onFailure(int statusCode, String response, Throwable throwable) {
                super.onFailure(statusCode, response, throwable);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Thread.currentThread().setPriority(Process.THREAD_PRIORITY_BACKGROUND);
                        outcomeEventsRepository.saveOutcomeEvent(new OutcomeEvent(finalSession, notificationId, name, sessionTimeStamp));
                    }
                }, "OS_SAVE_OUTCOMES").start();
            }
        };

        switch (session) {
            case DIRECT:
                outcomeEventsRepository.requestMeasureDirectOutcomeEvent(name, appId, notificationId, deviceType, responseHandler);
                break;
            case INDIRECT:
                outcomeEventsRepository.requestMeasureIndirectOutcomeEvent(name, appId, notificationId, deviceType, responseHandler);
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
