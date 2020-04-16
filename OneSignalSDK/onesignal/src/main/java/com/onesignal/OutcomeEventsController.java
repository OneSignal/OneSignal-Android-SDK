package com.onesignal;

import android.os.Process;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONArray;

import java.util.List;
import java.util.Set;

class OutcomeEventsController {

    private static final String OS_SAVE_OUTCOMES = "OS_SAVE_OUTCOMES";
    private static final String OS_SEND_SAVED_OUTCOMES = "OS_SEND_SAVED_OUTCOMES";
    private static final String OS_SAVE_UNIQUE_OUTCOME_NOTIFICATIONS = "OS_SAVE_UNIQUE_OUTCOME_NOTIFICATIONS";

    // Keeps track of unique outcome events sent for UNATTRIBUTED sessions on a per session level
    private Set<String> unattributedUniqueOutcomeEventsSentSet;

    @NonNull
    private final OutcomeEventsRepository outcomeEventsRepository;
    @NonNull
    private final OSSessionManager osSessionManager;

    public OutcomeEventsController(@NonNull OSSessionManager osSessionManager, @NonNull OutcomeEventsRepository outcomeEventsRepository) {
        this.osSessionManager = osSessionManager;
        this.outcomeEventsRepository = outcomeEventsRepository;

        initUniqueOutcomeEventsSentSets();
    }

    OutcomeEventsController(@NonNull OSSessionManager osSessionManager, @NonNull OneSignalDbHelper dbHelper) {
        this.outcomeEventsRepository = new OutcomeEventsRepository(dbHelper);
        this.osSessionManager = osSessionManager;

        initUniqueOutcomeEventsSentSets();
    }

    /**
     * Init the sets used for tracking attributed and unattributed unique outcome events
     */
    private void initUniqueOutcomeEventsSentSets() {
        // Get all cached UNATTRIBUTED unique outcomes
        unattributedUniqueOutcomeEventsSentSet = OSUtils.newConcurrentSet();
        Set<String> tempUnattributedUniqueOutcomeEventsSentSet = OneSignalPrefs.getStringSet(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_UNATTRIBUTED_UNIQUE_OUTCOME_EVENTS_SENT,
                null
        );
        if (tempUnattributedUniqueOutcomeEventsSentSet != null)
            unattributedUniqueOutcomeEventsSentSet.addAll(tempUnattributedUniqueOutcomeEventsSentSet);
    }

    /**
     * Clean unattributed unique outcome events sent so they can be sent after a new session
     */
    void cleanOutcomes() {
        unattributedUniqueOutcomeEventsSentSet = OSUtils.newConcurrentSet();
        saveUnattributedUniqueOutcomeEvents();
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
            }
        }, OS_SEND_SAVED_OUTCOMES).start();
    }

    void sendUniqueOutcomeEvent(@NonNull final String name, @Nullable OneSignal.OutcomeCallback callback) {
        OSSessionManager.SessionResult sessionResult = osSessionManager.getSessionResult();
        sendUniqueOutcomeEvent(name, sessionResult, osSessionManager.getSession(), callback);
    }

    void sendUniqueClickOutcomeEvent(@NonNull final String name) {
        OSSessionManager.SessionResult sessionResult = osSessionManager.getIAMSessionResult();
        sendUniqueOutcomeEvent(name, sessionResult, OSSessionManager.Session.UNATTRIBUTED, null);
    }

    void sendOutcomeEvent(@NonNull final String name, @Nullable final OneSignal.OutcomeCallback callback) {
        OSSessionManager.SessionResult sessionResult = osSessionManager.getSessionResult();
        sendAndCreateOutcomeEvent(name, 0, sessionResult.notificationIds, sessionResult.session, callback);
    }

    void sendOutcomeEventWithValue(@NonNull String name, float weight, @Nullable final OneSignal.OutcomeCallback callback) {
        OSSessionManager.SessionResult sessionResult = osSessionManager.getSessionResult();
        sendAndCreateOutcomeEvent(name, weight, sessionResult.notificationIds, sessionResult.session, callback);
    }

    void sendClickOutcomeEventWithValue(@NonNull final String name, float weight) {
        OSSessionManager.SessionResult sessionResult = osSessionManager.getIAMSessionResult();
        sendAndCreateOutcomeEvent(name, weight, sessionResult.notificationIds, sessionResult.session, null);
    }

    private void sendUniqueOutcomeEvent(@NonNull final String name, @NonNull OSSessionManager.SessionResult sessionResult,
                                        OSSessionManager.Session currentSession, @Nullable OneSignal.OutcomeCallback callback) {
        OSSessionManager.Session session = sessionResult.session;
        final JSONArray notificationIds = sessionResult.notificationIds;

        // Special handling for unique outcomes in the attributed and unattributed scenarios
        if (currentSession.isAttributed()) {
            // Make sure unique notificationIds exist before trying to make measure request
            final JSONArray uniqueNotificationIds = getUniqueNotificationIds(name, notificationIds);
            if (uniqueNotificationIds == null) {
                OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG,
                        "Measure endpoint will not send because unique outcome already sent for: " +
                                "\nSession: " + osSessionManager.getSession().toString() +
                                "\nOutcome name: " + name +
                                "\nnotificationIds: " + notificationIds);

                // Return null within the callback to determine not a failure, but not a success in terms of the request made
                if (callback != null)
                    callback.onSuccess(null);

                return;
            }

            sendAndCreateOutcomeEvent(name, 0, uniqueNotificationIds, session, callback);

        } else if (currentSession.isUnattributed()) {
            // Make sure unique outcome has not been sent for current unattributed session
            if (unattributedUniqueOutcomeEventsSentSet.contains(name)) {
                OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG,
                        "Measure endpoint will not send because unique outcome already sent for: " +
                                "\nSession: " + osSessionManager.getSession().toString() +
                                "\nOutcome name: " + name);

                // Return null within the callback to determine not a failure, but not a success in terms of the request made
                if (callback != null)
                    callback.onSuccess(null);

                return;
            }

            unattributedUniqueOutcomeEventsSentSet.add(name);
            sendAndCreateOutcomeEvent(name, 0, null, session, callback);
        } else {
            OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG,
                    "Unique Outcome for current session is disabled");
        }
    }

    private void sendSavedOutcomeEvent(@NonNull final OutcomeEvent event) {
        OneSignalRestClient.ResponseHandler responseHandler = new OneSignalRestClient.ResponseHandler() {
            @Override
            void onSuccess(String response) {
                super.onSuccess(response);
                outcomeEventsRepository.removeEvent(event);
            }
        };

        sendOutcomeEvent(event, responseHandler);
    }

    private void sendAndCreateOutcomeEvent(@NonNull final String name,
                                           @NonNull final float weight,
                                           @Nullable final JSONArray notificationIds,
                                           @NonNull final OSSessionManager.Session session,
                                           @Nullable final OneSignal.OutcomeCallback callback) {
        final long timestampSeconds = System.currentTimeMillis() / 1000;
        final OutcomeEvent outcomeEvent = new OutcomeEvent(session, notificationIds, name, timestampSeconds, weight);

        OneSignalRestClient.ResponseHandler responseHandler = new OneSignalRestClient.ResponseHandler() {
            @Override
            void onSuccess(String response) {
                super.onSuccess(response);

                if (session.isAttributed())
                    saveAttributedUniqueOutcomeNotifications(notificationIds, name);
                else
                    saveUnattributedUniqueOutcomeEvents();

                // The only case where an actual success has occurred and the OutcomeEvent should be sent back
                if (callback != null)
                    callback.onSuccess(outcomeEvent);
            }

            @Override
            void onFailure(int statusCode, String response, Throwable throwable) {
                super.onFailure(statusCode, response, throwable);

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Thread.currentThread().setPriority(Process.THREAD_PRIORITY_BACKGROUND);
                        outcomeEventsRepository.saveOutcomeEvent(outcomeEvent);
                    }
                }, OS_SAVE_OUTCOMES).start();

                OneSignal.onesignalLog(OneSignal.LOG_LEVEL.WARN,
                        "Sending outcome with name: " + name + " failed with status code: " + statusCode + " and response: " + response +
                                "\nOutcome event was cached and will be reattempted on app cold start");

                // Return null within the callback to determine not a failure, but not a success in terms of the request made
                if (callback != null)
                    callback.onSuccess(null);
            }
        };

        sendOutcomeEvent(outcomeEvent, responseHandler);
    }

    private void sendOutcomeEvent(@NonNull OutcomeEvent outcomeEvent, OneSignalRestClient.ResponseHandler responseHandler) {
        final String appId = OneSignal.appId;
        final int deviceType = new OSUtils().getDeviceType();

        switch (outcomeEvent.getSession()) {
            case DIRECT:
                outcomeEventsRepository.requestMeasureDirectOutcomeEvent(appId, deviceType, outcomeEvent, responseHandler);
                break;
            case INDIRECT:
                outcomeEventsRepository.requestMeasureIndirectOutcomeEvent(appId, deviceType, outcomeEvent, responseHandler);
                break;
            case UNATTRIBUTED:
                outcomeEventsRepository.requestMeasureUnattributedOutcomeEvent(appId, deviceType, outcomeEvent, responseHandler);
                break;
            case DISABLED:
                OneSignal.Log(OneSignal.LOG_LEVEL.VERBOSE, "Outcomes for current session are disabled");
                break;
        }
    }

    /**
     * Save the ATTRIBUTED JSONArray of notification ids with unique outcome names to SQL
     */
    private void saveAttributedUniqueOutcomeNotifications(final JSONArray notificationIds, final String name) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setPriority(Process.THREAD_PRIORITY_BACKGROUND);
                outcomeEventsRepository.saveUniqueOutcomeNotifications(notificationIds, name);
            }
        }, OS_SAVE_UNIQUE_OUTCOME_NOTIFICATIONS).start();
    }

    /**
     * Save the current set of UNATTRIBUTED unique outcome names to SharedPrefs
     */
    private void saveUnattributedUniqueOutcomeEvents() {
        OneSignalPrefs.saveStringSet(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_UNATTRIBUTED_UNIQUE_OUTCOME_EVENTS_SENT,
                // Post success, store unattributed unique outcome event names
                unattributedUniqueOutcomeEventsSentSet);
    }

    /**
     * Get the unique notifications that have not been cached/sent before with the current unique outcome name
     */
    private JSONArray getUniqueNotificationIds(String name, JSONArray notificationIds) {
        JSONArray uniqueNotificationIds = outcomeEventsRepository.getNotCachedUniqueOutcomeNotifications(name, notificationIds);
        if (uniqueNotificationIds.length() == 0)
            return null;

        return uniqueNotificationIds;
    }

}
