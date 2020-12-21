package com.onesignal;

import android.os.Process;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.onesignal.influence.model.OSInfluence;
import com.onesignal.influence.model.OSInfluenceType;
import com.onesignal.outcomes.OSOutcomeEventsFactory;
import com.onesignal.outcomes.model.OSOutcomeEventParams;
import com.onesignal.outcomes.model.OSOutcomeSource;
import com.onesignal.outcomes.model.OSOutcomeSourceBody;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

class OSOutcomeEventsController {

    private static final String OS_SAVE_OUTCOMES = "OS_SAVE_OUTCOMES";
    private static final String OS_SEND_SAVED_OUTCOMES = "OS_SEND_SAVED_OUTCOMES";
    private static final String OS_SAVE_UNIQUE_OUTCOME_NOTIFICATIONS = "OS_SAVE_UNIQUE_OUTCOME_NOTIFICATIONS";

    // Keeps track of unique outcome events sent for UNATTRIBUTED sessions on a per session level
    private Set<String> unattributedUniqueOutcomeEventsSentOnSession;

    @NonNull
    private final OSOutcomeEventsFactory outcomeEventsFactory;
    @NonNull
    private final OSSessionManager osSessionManager;

    public OSOutcomeEventsController(@NonNull OSSessionManager osSessionManager, @NonNull OSOutcomeEventsFactory outcomeEventsFactory) {
        this.osSessionManager = osSessionManager;
        this.outcomeEventsFactory = outcomeEventsFactory;

        initUniqueOutcomeEventsSentSets();
    }

    /**
     * Init the sets used for tracking attributed and unattributed unique outcome events
     */
    private void initUniqueOutcomeEventsSentSets() {
        // Get all cached UNATTRIBUTED unique outcomes
        unattributedUniqueOutcomeEventsSentOnSession = OSUtils.newConcurrentSet();
        Set<String> tempUnattributedUniqueOutcomeEventsSentSet = outcomeEventsFactory.getRepository().getUnattributedUniqueOutcomeEventsSent();
        if (tempUnattributedUniqueOutcomeEventsSentSet != null)
            unattributedUniqueOutcomeEventsSentOnSession = tempUnattributedUniqueOutcomeEventsSentSet;
    }

    /**
     * Clean unattributed unique outcome events sent so they can be sent after a new session
     */
    void cleanOutcomes() {
        OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "OneSignal cleanOutcomes for session");
        unattributedUniqueOutcomeEventsSentOnSession = OSUtils.newConcurrentSet();
        saveUnattributedUniqueOutcomeEvents();
    }

    /**
     * Any outcomes cached in local DB will be reattempted to be sent again
     * Cached outcomes come from the failure callback of the network request
     */
    void sendSavedOutcomes() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setPriority(Process.THREAD_PRIORITY_BACKGROUND);

                List<OSOutcomeEventParams> outcomeEvents = outcomeEventsFactory.getRepository().getSavedOutcomeEvents();
                for (OSOutcomeEventParams event : outcomeEvents) {
                    sendSavedOutcomeEvent(event);
                }
            }
        }, OS_SEND_SAVED_OUTCOMES).start();
    }

    private void sendSavedOutcomeEvent(@NonNull final OSOutcomeEventParams event) {
        int deviceType = new OSUtils().getDeviceType();
        String appId = OneSignal.appId;

        OneSignalApiResponseHandler responseHandler = new OneSignalApiResponseHandler() {
            @Override
            public void onSuccess(String response) {
                outcomeEventsFactory.getRepository().removeEvent(event);
            }

            @Override
            public void onFailure(int statusCode, String response, Throwable throwable) {
            }
        };

        outcomeEventsFactory.getRepository().requestMeasureOutcomeEvent(appId, deviceType, event, responseHandler);
    }

    void sendClickActionOutcomes(List<OSInAppMessageOutcome> outcomes) {
        for (OSInAppMessageOutcome outcome : outcomes) {
            String name = outcome.getName();

            if (outcome.isUnique()) {
                sendUniqueOutcomeEvent(name, null);
            } else if (outcome.getWeight() > 0) {
                sendOutcomeEventWithValue(name, outcome.getWeight(), null);
            } else {
                sendOutcomeEvent(name, null);
            }
        }
    }

    void sendUniqueOutcomeEvent(@NonNull final String name, @Nullable OneSignal.OutcomeCallback callback) {
        List<OSInfluence> sessionResult = osSessionManager.getInfluences();
        sendUniqueOutcomeEvent(name, sessionResult, callback);
    }

    void sendOutcomeEvent(@NonNull final String name, @Nullable final OneSignal.OutcomeCallback callback) {
        List<OSInfluence> influences = osSessionManager.getInfluences();
        sendAndCreateOutcomeEvent(name, 0, influences, callback);
    }

    void sendOutcomeEventWithValue(@NonNull String name, float weight, @Nullable final OneSignal.OutcomeCallback callback) {
        List<OSInfluence> influences = osSessionManager.getInfluences();
        sendAndCreateOutcomeEvent(name, weight, influences, callback);
    }

    /**
     * An unique outcome is considered unattributed when all channels are unattributed
     * If one channel is attributed is enough reason to cache attribution
     */
    private void sendUniqueOutcomeEvent(@NonNull final String name, @NonNull List<OSInfluence> sessionInfluences, @Nullable OneSignal.OutcomeCallback callback) {
        List<OSInfluence> influences = removeDisabledInfluences(sessionInfluences);
        if (influences.isEmpty()) {
            OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "Unique Outcome disabled for current session");
            return;
        }

        boolean attributed = false;
        for (OSInfluence influence : influences) {
            if (influence.getInfluenceType().isAttributed()) {
                // At least one channel attributed this outcome
                attributed = true;
                break;
            }
        }

        // Special handling for unique outcomes in the attributed and unattributed scenarios
        if (attributed) {
            // Make sure unique Ids exist before trying to make measure request
            final List<OSInfluence> uniqueInfluences = getUniqueIds(name, influences);
            if (uniqueInfluences == null) {
                OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG,
                        "Measure endpoint will not send because unique outcome already sent for: " +
                                "\nSessionInfluences: " + influences.toString() +
                                "\nOutcome name: " + name);

                // Return null within the callback to determine not a failure, but not a success in terms of the request made
                if (callback != null)
                    callback.onSuccess(null);

                return;
            }

            sendAndCreateOutcomeEvent(name, 0, uniqueInfluences, callback);
        } else {
            // Make sure unique outcome has not been sent for current unattributed session
            if (unattributedUniqueOutcomeEventsSentOnSession.contains(name)) {
                OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG,
                        "Measure endpoint will not send because unique outcome already sent for: " +
                                "\nSession: " + OSInfluenceType.UNATTRIBUTED +
                                "\nOutcome name: " + name);

                // Return null within the callback to determine not a failure, but not a success in terms of the request made
                if (callback != null)
                    callback.onSuccess(null);

                return;
            }

            unattributedUniqueOutcomeEventsSentOnSession.add(name);
            sendAndCreateOutcomeEvent(name, 0, influences, callback);
        }
    }

    private void sendAndCreateOutcomeEvent(@NonNull final String name,
                                           @NonNull final float weight,
                                           @NonNull List<OSInfluence> influences,
                                           @Nullable final OneSignal.OutcomeCallback callback) {
        final long timestampSeconds = System.currentTimeMillis() / 1000;
        final int deviceType = new OSUtils().getDeviceType();
        final String appId = OneSignal.appId;

        OSOutcomeSourceBody directSourceBody = null;
        OSOutcomeSourceBody indirectSourceBody = null;
        boolean unattributed = false;

        for (OSInfluence influence : influences) {
            switch (influence.getInfluenceType()) {
                case DIRECT:
                    directSourceBody = setSourceChannelIds(influence, directSourceBody == null ? new OSOutcomeSourceBody() : directSourceBody);
                    break;
                case INDIRECT:
                    indirectSourceBody = setSourceChannelIds(influence, indirectSourceBody == null ? new OSOutcomeSourceBody() : indirectSourceBody);
                    break;
                case UNATTRIBUTED:
                    unattributed = true;
                    break;
                case DISABLED:
                    OneSignal.Log(OneSignal.LOG_LEVEL.VERBOSE, "Outcomes disabled for channel: " + influence.getInfluenceChannel());
                    if (callback != null)
                        callback.onSuccess(null);
                    return; // finish method
            }
        }

        if (directSourceBody == null && indirectSourceBody == null && !unattributed) {
            // Disabled for all channels
            OneSignal.Log(OneSignal.LOG_LEVEL.VERBOSE, "Outcomes disabled for all channels");
            if (callback != null)
                callback.onSuccess(null);
            return;
        }

        OSOutcomeSource source = new OSOutcomeSource(directSourceBody, indirectSourceBody);

        final OSOutcomeEventParams eventParams = new OSOutcomeEventParams(name, source, weight);

        OneSignalApiResponseHandler responseHandler = new OneSignalApiResponseHandler() {
            @Override
            public void onSuccess(String response) {
                saveUniqueOutcome(eventParams);

                // The only case where an actual success has occurred and the OutcomeEvent should be sent back
                if (callback != null)
                    callback.onSuccess(OutcomeEvent.fromOutcomeEventParamsV2toOutcomeEventV1(eventParams));
            }

            @Override
            public void onFailure(int statusCode, String response, Throwable throwable) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Thread.currentThread().setPriority(Process.THREAD_PRIORITY_BACKGROUND);
                        // Only if we need to save and retry the outcome, then we will save the timestamp for future sending
                        eventParams.setTimestamp(timestampSeconds);
                        outcomeEventsFactory.getRepository().saveOutcomeEvent(eventParams);
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

        outcomeEventsFactory.getRepository().requestMeasureOutcomeEvent(appId, deviceType, eventParams, responseHandler);
    }

    private OSOutcomeSourceBody setSourceChannelIds(OSInfluence influence, OSOutcomeSourceBody sourceBody) {
        switch (influence.getInfluenceChannel()) {
            case IAM:
                sourceBody.setInAppMessagesIds(influence.getIds());
                break;
            case NOTIFICATION:
                sourceBody.setNotificationIds(influence.getIds());
                break;
        }

        return sourceBody;
    }

    private List<OSInfluence> removeDisabledInfluences(List<OSInfluence> influences) {
        List<OSInfluence> availableInfluences = new ArrayList<>(influences);
        for (OSInfluence influence : influences) {
            if (influence.getInfluenceType().isDisabled()) {
                OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG,
                        "Outcomes disabled for channel: " + influence.getInfluenceChannel().toString());
                availableInfluences.remove(influence);
            }
        }

        return availableInfluences;
    }

    private void saveUniqueOutcome(OSOutcomeEventParams eventParams) {
        if (eventParams.isUnattributed())
            saveUnattributedUniqueOutcomeEvents();
        else
            saveAttributedUniqueOutcomeNotifications(eventParams);

    }

    /**
     * Save the ATTRIBUTED JSONArray of notification ids with unique outcome names to SQL
     */
    private void saveAttributedUniqueOutcomeNotifications(final OSOutcomeEventParams eventParams) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setPriority(Process.THREAD_PRIORITY_BACKGROUND);
                outcomeEventsFactory.getRepository().saveUniqueOutcomeNotifications(eventParams);
            }
        }, OS_SAVE_UNIQUE_OUTCOME_NOTIFICATIONS).start();
    }

    /**
     * Save the current set of UNATTRIBUTED unique outcome names to SharedPrefs
     */
    private void saveUnattributedUniqueOutcomeEvents() {
        outcomeEventsFactory.getRepository().saveUnattributedUniqueOutcomeEventsSent(unattributedUniqueOutcomeEventsSentOnSession);
    }

    /**
     * Get the unique notifications that have not been cached/sent before with the current unique outcome name
     */
    private List<OSInfluence> getUniqueIds(String name, List<OSInfluence> influences) {
        List<OSInfluence> uniqueInfluences = outcomeEventsFactory.getRepository().getNotCachedUniqueOutcome(name, influences);
        return uniqueInfluences.size() > 0 ? uniqueInfluences : null;
    }

}
