package com.onesignal;

import android.os.Process;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.List;
import java.util.Set;

class OutcomeEventsController {

    private static final String OS_SAVE_OUTCOMES = "OS_SAVE_OUTCOMES";

    static class OutcomeException extends Exception {

        OutcomeException(String message) {
            super(message);
        }

    }

    // Keeps track of unique outcome events sent for ATTRIBUTED sessions on a notification level
    //  Saved in format: <outcome_name + notification_id>
    private Set<String> attributedUniqueOutcomeEventsSentSet;

    // Keeps track of unique outcomeevents sent for UNATTRIBUTED sessions on a session level
    //  Saved in format: <outcome_name>
    private Set<String> unattributedUniqueOutcomeEventsSentSet;

    @NonNull
    private final OutcomeEventsRepository outcomeEventsRepository;
    @NonNull
    private final OSSessionManager osSessionManager;
    @Nullable
    private OneSignal.OutcomeSettings outcomeSettings;

    public OutcomeEventsController(@NonNull OSSessionManager osSessionManager, @NonNull OutcomeEventsRepository outcomeEventsRepository) {
        this.osSessionManager = osSessionManager;
        this.outcomeEventsRepository = outcomeEventsRepository;

        initUniqueOutcomeEventsSentSets();
    }

    OutcomeEventsController(@NonNull OSSessionManager osSessionManager, @NonNull OneSignalDbHelper dbHelper, @Nullable OneSignal.OutcomeSettings outcomeSettings) {
        this.outcomeEventsRepository = new OutcomeEventsRepository(dbHelper);
        this.osSessionManager = osSessionManager;
        this.outcomeSettings = outcomeSettings;

        initUniqueOutcomeEventsSentSets();
    }

    /**
     * Init the sets used for tracking attributed and unattributed unique outcome events
     */
    private void initUniqueOutcomeEventsSentSets() {
        // If UNATTRIBUTED unique outcomes is already set in current session we don't want to clear it
        if (unattributedUniqueOutcomeEventsSentSet == null)
            unattributedUniqueOutcomeEventsSentSet = OSUtils.newConcurrentSet();

        // Get all cached ATTRIBUTED unique outcomes
        attributedUniqueOutcomeEventsSentSet = OSUtils.newConcurrentSet();
        Set<String> tempAttributedUniqueOutcomeEventsSentSet = OneSignalPrefs.getStringSet(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_UNIQUE_OUTCOME_EVENTS_SENT,
                null
        );
        if (tempAttributedUniqueOutcomeEventsSentSet != null)
            attributedUniqueOutcomeEventsSentSet.addAll(tempAttributedUniqueOutcomeEventsSentSet);
    }

    void setOutcomeSettings(@Nullable OneSignal.OutcomeSettings outcomeSettings) {
        this.outcomeSettings = outcomeSettings;
    }

    /**
     * Clean unattributed unique outcome events sent so they can be sent after a new session
     */
    void cleanOutcomes() {
        unattributedUniqueOutcomeEventsSentSet = OSUtils.newConcurrentSet();
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

    void sendOutcomeEvent(@NonNull final String name, @Nullable final OneSignal.OutcomeCallback callback) {
        final JSONArray notificationIds = osSessionManager.getSessionResult().notificationIds;
        sendOutcomeEvent(name, notificationIds, null, callback);
    }

    void sendUniqueOutcomeEvent(@NonNull final String name, @Nullable OneSignal.OutcomeCallback callback) {
        final JSONArray notificationIds = osSessionManager.getSessionResult().notificationIds;

        // Special handling for unique outcomes in the attributed and unattributed scenarios
        if (osSessionManager.getSession().isAttributed()) {
            // Make sure unique notificationIds exist before trying to make measure request
            final JSONArray uniqueNotificationIds = getUniqueNotificationIds(name, notificationIds);
            if (uniqueNotificationIds == null) {
                OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG,
                        "Measure endpoint will not send because unique outcome already sent for: " +
                                "\nSession: " + osSessionManager.getSession().toString() +
                                "\nOutcome name: " + name +
                                "\nnotificationIds: " + notificationIds);
                return;
            }

            sendOutcomeEvent(name, uniqueNotificationIds, null, callback);

        } else if (osSessionManager.getSession().isUnattributed()) {
            // Make sure unique outcome has not been sent for current unattributed session
            if (unattributedUniqueOutcomeEventsSentSet.contains(name)) {
                OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG,
                        "Measure endpoint will not send because unique outcome already sent for: " +
                                "\nSession: " + osSessionManager.getSession().toString() +
                                "\nOutcome name: " + name);
                return;
            }

            unattributedUniqueOutcomeEventsSentSet.add(name);
            sendOutcomeEvent(name, null, null, callback);
        }
    }

    void sendOutcomeEventWithValue(@NonNull String name, float value, @Nullable final OneSignal.OutcomeCallback callback) {
        final JSONArray notificationIds = osSessionManager.getSessionResult().notificationIds;
        OutcomeParams params = OutcomeParams.Builder
                .newInstance()
                .setWeight(value)
                .build();
        sendOutcomeEvent(name, notificationIds, params, callback);
    }

    private void sendOutcomeEvent(@NonNull final String name, @Nullable final JSONArray notificationIds, @Nullable final OutcomeParams params, @Nullable final OneSignal.OutcomeCallback callback) {
        OSSessionManager.SessionResult sessionResult = osSessionManager.getSessionResult();

        final OSSessionManager.Session session = sessionResult.session;
        final String appId = OneSignal.appId;
        final long timestampSeconds = System.currentTimeMillis() / 1000;
        final int deviceType = new OSUtils().getDeviceType();

        OneSignalRestClient.ResponseHandler responseHandler = new OneSignalRestClient.ResponseHandler() {
            @Override
            void onSuccess(String response) {
                super.onSuccess(response);

                OneSignalPrefs.saveStringSet(
                        OneSignalPrefs.PREFS_ONESIGNAL,
                        OneSignalPrefs.PREFS_OS_UNIQUE_OUTCOME_EVENTS_SENT,
                        // Post success, store unique outcome event ids
                        attributedUniqueOutcomeEventsSentSet);

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
                            outcomeEventsRepository.saveOutcomeEvent(
                                    new OutcomeEvent(session, notificationIds, name, timestampSeconds, params));
                        }
                    }, OS_SAVE_OUTCOMES).start();
                }

                if (callback != null)
                    callback.onOutcomeFail(statusCode, response);
            }
        };

        switch (session) {
            case DIRECT:
                outcomeEventsRepository.requestMeasureDirectOutcomeEvent(name, params, appId, notificationIds, deviceType, responseHandler);
                break;
            case INDIRECT:
                outcomeEventsRepository.requestMeasureIndirectOutcomeEvent(name, params, appId, notificationIds, deviceType, responseHandler);
                break;
            case UNATTRIBUTED:
                outcomeEventsRepository.requestMeasureUnattributedOutcomeEvent(name, params, appId, deviceType, responseHandler);
                break;
            case DISABLED:
                OneSignal.Log(OneSignal.LOG_LEVEL.VERBOSE, "Outcomes for current session are disabled");
                break;
        }
    }

    /**
     * Get the unique notifications that have not been sent before with the current unique outcome name
     */
    private JSONArray getUniqueNotificationIds(String name, JSONArray notificationIds) {
        boolean hasNoUniqueNotificationIds = hasNoUniqueOutcomeNotificationIds(name, notificationIds);
        if (hasNoUniqueNotificationIds)
            return null;

        return getUniqueOutcomeNotificationIds(name, notificationIds);
    }

    /**
     * Validate whether or not the JSONArray of notificationIds has ids that have not been sent with the specific unique outcome name
     */
    private boolean hasNoUniqueOutcomeNotificationIds(String uniqueName, JSONArray notificationIds) {
        int uniqueIdCount = 0;
        try {
            for (int i = 0; i < notificationIds.length(); i++) {
                String notificationId = notificationIds.getString(i);
                String uniqueOutcomeNotificationId = uniqueName + "_" + notificationId;

                if (attributedUniqueOutcomeEventsSentSet.contains(uniqueOutcomeNotificationId))
                    uniqueIdCount++;
            }
        } catch (JSONException e) {
            e.printStackTrace();

            return false;
        }

        return uniqueIdCount == notificationIds.length();
    }

    /**
     * Get all of the notificationIds that have not been sent for the specific unique outcome event
     */
    private JSONArray getUniqueOutcomeNotificationIds(String uniqueName, @Nullable JSONArray notificationIds) {
        if (notificationIds == null)
            return new JSONArray();

        JSONArray uniqueNotificationIds = new JSONArray();
        try {
            for (int i = 0; i < notificationIds.length(); i++) {
                String notificationId = notificationIds.getString(i);
                String uniqueOutcomeNotificationId = uniqueName + "_" + notificationId;

                if (!attributedUniqueOutcomeEventsSentSet.contains(uniqueOutcomeNotificationId)) {
                    attributedUniqueOutcomeEventsSentSet.add(uniqueOutcomeNotificationId);
                    uniqueNotificationIds.put(notificationId);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();

            return new JSONArray();
        }

        return uniqueNotificationIds;
    }

    boolean isCacheActive() {
        return outcomeSettings == null || outcomeSettings.isCacheActive();
    }
}
