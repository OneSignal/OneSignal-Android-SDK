package com.onesignal;

import android.os.Handler;
import android.os.HandlerThread;

import androidx.annotation.Nullable;

import com.onesignal.OneSignal.ChangeTagsUpdateHandler;
import com.onesignal.OneSignal.SendTagsError;
import com.onesignal.OneSignalStateSynchronizer.UserStateSynchronizerType;
import com.onesignal.OneSignalStateSynchronizer.OSDeviceInfoCompletionHandler;
import com.onesignal.OneSignalStateSynchronizer.OSDeviceInfoError;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.onesignal.OSInAppMessageController.IN_APP_MESSAGES_JSON_KEY;

abstract class UserStateSynchronizer {

    private static final String CURRENT_STATE = "CURRENT_STATE";
    private static final String TOSYNC_STATE = "TOSYNC_STATE";
    private static final String SESSION = "session";
    private static final String ID = "id";
    private static final String ERRORS = "errors";

    protected static final String IDENTIFIER = "identifier";
    protected static final String DEVICE_TYPE = "device_type";
    protected static final String DEVICE_PLAYER_ID = "device_player_id";
    protected static final String PARENT_PLAYER_ID = "parent_player_id";
    protected static final String USER_SUBSCRIBE_PREF = "userSubscribePref";
    protected static final String ANDROID_PERMISSION = "androidPermission";
    protected static final String SUBSCRIBABLE_STATUS = "subscribableStatus";
    protected static final String TAGS = "tags";
    protected static final String LANGUAGE = "language";
    protected static final String EXTERNAL_USER_ID = "external_user_id";
    protected static final String EMAIL_KEY = "email";
    protected static final String LOGOUT_EMAIL = "logoutEmail";
    protected static final String SMS_NUMBER_KEY = "sms_number";

    static final String EXTERNAL_USER_ID_AUTH_HASH = "external_user_id_auth_hash";
    static final String EMAIL_AUTH_HASH_KEY = "email_auth_hash";
    static final String SMS_AUTH_HASH_KEY = "sms_auth_hash";
    static final String APP_ID = "app_id";

    // Object to synchronize on to prevent concurrent modifications on syncValues and dependValues
    protected final Object LOCK = new Object();

    private UserStateSynchronizerType channel;
    private boolean canMakeUpdates;

    UserStateSynchronizer(UserStateSynchronizerType channel) {
        this.channel = channel;
    }

    UserStateSynchronizerType getChannelType() {
        return channel;
    }

    String getChannelString() {
        return channel.name().toLowerCase();
    }

    static class GetTagsResult {
        boolean serverSuccess;
        JSONObject result;

        GetTagsResult(boolean serverSuccess, JSONObject result) {
            this.serverSuccess = serverSuccess;
            this.result = result;
        }
    }

    abstract void saveChannelId(String id);
    abstract boolean getSubscribed();

    String getRegistrationId() {
        return getToSyncUserState().getSyncValues().optString(IDENTIFIER, null);
    }

    abstract GetTagsResult getTags(boolean fromServer);

    abstract @Nullable String getExternalId(boolean fromServer);

    private AtomicBoolean runningSyncUserState = new AtomicBoolean();

    // Maintain a list of handlers so that if the user calls
    //    sendTags() multiple times it will call each callback
    final private Queue<ChangeTagsUpdateHandler> sendTagsHandlers = new ConcurrentLinkedQueue<>();
    final private Queue<OneSignal.OSInternalExternalUserIdUpdateCompletionHandler> externalUserIdUpdateHandlers = new ConcurrentLinkedQueue<>();
    final private Queue<OSDeviceInfoCompletionHandler> deviceInfoCompletionHandler = new ConcurrentLinkedQueue<>();

    boolean hasQueuedHandlers() {
        return externalUserIdUpdateHandlers.size() > 0;
    }

    class NetworkHandlerThread extends HandlerThread {
        private static final String THREAD_NAME_PREFIX = "OSH_NetworkHandlerThread_";
        protected static final int NETWORK_HANDLER_USERSTATE = 0;

        int mType;

        Handler mHandler;

        static final int MAX_RETRIES = 3, NETWORK_CALL_DELAY_TO_BUFFER_MS = 5_000;
        int currentRetry;

        NetworkHandlerThread(int type) {
            super(THREAD_NAME_PREFIX + UserStateSynchronizer.this.channel);
            mType = type;
            start();
            mHandler = new Handler(getLooper());
        }

        void runNewJobDelayed() {
            if (!canMakeUpdates)
                return;

            synchronized (mHandler) {
                currentRetry = 0;
                mHandler.removeCallbacksAndMessages(null);
                mHandler.postDelayed(getNewRunnable(), NETWORK_CALL_DELAY_TO_BUFFER_MS);
            }
        }

        private Runnable getNewRunnable() {
            switch (mType) {
                case NETWORK_HANDLER_USERSTATE:
                    return new Runnable() {
                        @Override
                        public void run() {
                            boolean syncUserState = !runningSyncUserState.get();
                            if (syncUserState)
                                syncUserState(false);
                        }
                    };
            }

            return null;
        }

        void stopScheduledRunnable() {
            mHandler.removeCallbacksAndMessages(null);
        }

        // Retries if not passed limit.
        // Returns true if there retrying or there is another future sync scheduled already
        boolean doRetry() {
            synchronized (mHandler) {
                boolean doRetry = currentRetry < MAX_RETRIES;
                boolean futureSync = mHandler.hasMessages(0);

                if (doRetry && !futureSync) {
                    currentRetry++;
                    mHandler.postDelayed(getNewRunnable(), currentRetry * 15_000);
                }

                return mHandler.hasMessages(0);
            }
        }
    }

    HashMap<Integer, NetworkHandlerThread> networkHandlerThreads = new HashMap<>();
    private final Object networkHandlerSyncLock = new Object() {};

    protected boolean waitingForSessionResponse = false;

    // currentUserState - Current known state of the user on OneSignal's server.
    // toSyncUserState  - Pending state that will be synced to the OneSignal server.
    //                    diff will be generated between currentUserState when a sync call is made to the server.
    private UserState currentUserState, toSyncUserState;

    protected JSONObject generateJsonDiff(JSONObject cur, JSONObject changedTo, JSONObject baseOutput, Set<String> includeFields) {
        synchronized (LOCK) {
            return JSONUtils.generateJsonDiff(cur, changedTo, baseOutput, includeFields);
        }
    }

    protected UserState getCurrentUserState() {
        if (currentUserState == null) {
            synchronized (LOCK) {
                if (currentUserState == null)
                    currentUserState = newUserState(CURRENT_STATE, true);
            }
        }

        return currentUserState;
    }

    protected UserState getToSyncUserState() {
        if (toSyncUserState == null) {
            synchronized (LOCK) {
                if (toSyncUserState == null)
                    toSyncUserState = newUserState(TOSYNC_STATE, true);
            }
        }

        return toSyncUserState;
    }

    void initUserState() {
        if (currentUserState == null) {
            synchronized (LOCK) {
                if (currentUserState == null)
                    currentUserState = newUserState(CURRENT_STATE, true);
            }
        }
        getToSyncUserState();
    }

    abstract protected UserState newUserState(String inPersistKey, boolean load);

    void clearLocation() {
        getToSyncUserState().clearLocation();
        getToSyncUserState().persistState();
    }

    boolean persist() {
        if (toSyncUserState != null) {
            synchronized (LOCK) {
                // In case current state is being clean in background, save toSyncUserState for next player sync
                boolean unSynced = getCurrentUserState().generateJsonDiff(toSyncUserState, isSessionCall()) != null;
                toSyncUserState.persistState();
                return unSynced;
            }
        }
        return false;
    }

    protected abstract OneSignal.LOG_LEVEL getLogLevel();
    protected abstract String getId();

    private boolean isSessionCall() {
        boolean toSyncSession = getToSyncUserState().getDependValues().optBoolean(SESSION);
        return (toSyncSession || getId() == null) && !waitingForSessionResponse;
    }

    private boolean syncEmailLogout() {
        return getToSyncUserState().getDependValues().optBoolean(LOGOUT_EMAIL, false);
    }

    void syncUserState(boolean fromSyncService) {
        runningSyncUserState.set(true);
        internalSyncUserState(fromSyncService);
        runningSyncUserState.set(false);
    }

    private void internalSyncUserState(boolean fromSyncService) {
        final String userId = getId();

        if (syncEmailLogout() && userId != null) {
            doEmailLogout(userId);
            return;
        }

        if (currentUserState == null)
            initUserState();

        final boolean isSessionCall = !fromSyncService && isSessionCall();
        JSONObject jsonBody, dependDiff;
        synchronized (LOCK) {
            jsonBody = currentUserState.generateJsonDiff(getToSyncUserState(), isSessionCall);
            UserState toSyncState = getToSyncUserState();
            dependDiff = currentUserState.generateJsonDiffFromDependValues(toSyncState, null);;
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "UserStateSynchronizer internalSyncUserState from session call: "+ isSessionCall + " jsonBody: " + jsonBody);
            // Updates did not result in a server side change, skipping network call
            if (jsonBody == null) {
                currentUserState.persistStateAfterSync(dependDiff, null);
                sendTagsHandlersPerformOnSuccess();
                externalUserIdUpdateHandlersPerformOnSuccess();
                return;
            }
            getToSyncUserState().persistState();
        }

        if (!isSessionCall)
            doPutSync(userId, jsonBody, dependDiff);
        else
            doCreateOrNewSession(userId, jsonBody, dependDiff);
    }

    private void doEmailLogout(String userId) {
        String urlStr = "players/" + userId + "/email_logout";
        JSONObject jsonBody = new JSONObject();
        try {
            ImmutableJSONObject dependValues = currentUserState.getDependValues();
            if (dependValues.has(EMAIL_AUTH_HASH_KEY))
                jsonBody.put(EMAIL_AUTH_HASH_KEY, dependValues.optString(EMAIL_AUTH_HASH_KEY));

            ImmutableJSONObject syncValues = currentUserState.getSyncValues();
            if (syncValues.has(PARENT_PLAYER_ID))
                jsonBody.put(PARENT_PLAYER_ID, syncValues.optString(PARENT_PLAYER_ID));

            jsonBody.put(APP_ID, syncValues.optString(APP_ID));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        OneSignalRestClient.postSync(urlStr, jsonBody, new OneSignalRestClient.ResponseHandler() {
            @Override
            void onFailure(int statusCode, String response, Throwable throwable) {
                OneSignal.Log(OneSignal.LOG_LEVEL.WARN, "Failed last request. statusCode: " + statusCode + "\nresponse: " + response);

                if (response400WithErrorsContaining(statusCode, response, "already logged out of email")) {
                    logoutEmailSyncSuccess();
                    return;
                }

                if (response400WithErrorsContaining(statusCode, response, "not a valid device_type"))
                    handlePlayerDeletedFromServer();
                else
                    handleNetworkFailure(statusCode);
            }

            @Override
            void onSuccess(String response) {
                logoutEmailSyncSuccess();
            }
        });
    }

    private void logoutEmailSyncSuccess() {
        getToSyncUserState().removeFromDependValues(LOGOUT_EMAIL);
        toSyncUserState.removeFromDependValues(EMAIL_AUTH_HASH_KEY);
        toSyncUserState.removeFromSyncValues(PARENT_PLAYER_ID);
        toSyncUserState.removeFromSyncValues(EMAIL_KEY);
        toSyncUserState.persistState();

        currentUserState.removeFromDependValues(EMAIL_AUTH_HASH_KEY);
        currentUserState.removeFromSyncValues(PARENT_PLAYER_ID);
        String emailLoggedOut = currentUserState.getSyncValues().optString(EMAIL_KEY);
        currentUserState.removeFromSyncValues(EMAIL_KEY);

        OneSignalStateSynchronizer.setNewSessionForEmail();

        OneSignal.Log(OneSignal.LOG_LEVEL.INFO, "Device successfully logged out of email: " + emailLoggedOut);
        OneSignal.handleSuccessfulEmailLogout();
    }

    private void doPutSync(String userId, final JSONObject jsonBody, final JSONObject dependDiff) {
        if (userId == null) {
            OneSignal.onesignalLog(getLogLevel(), "Error updating the user record because of the null user id");
            sendTagsHandlersPerformOnFailure(new SendTagsError(-1, "Unable to update tags: the current user is not registered with OneSignal"));
            externalUserIdUpdateHandlersPerformOnFailure();
            return;
        }

        OneSignalRestClient.putSync("players/" + userId, jsonBody, new OneSignalRestClient.ResponseHandler() {
            @Override
            void onFailure(int statusCode, String response, Throwable throwable) {
                OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Failed PUT sync request with status code: " + statusCode + " and response: " + response);

                synchronized (LOCK) {
                    if (response400WithErrorsContaining(statusCode, response, "No user with this id found"))
                        handlePlayerDeletedFromServer();
                    else
                        handleNetworkFailure(statusCode);
                }

                if (jsonBody.has(TAGS))
                    sendTagsHandlersPerformOnFailure(new SendTagsError(statusCode, response));

                if (jsonBody.has(EXTERNAL_USER_ID)) {
                    OneSignal.onesignalLog(OneSignal.LOG_LEVEL.ERROR, "Error setting external user id for push with status code: "  + statusCode + " and message: " + response);
                    externalUserIdUpdateHandlersPerformOnFailure();
                }

                if (jsonBody.has(LANGUAGE))
                    deviceInfoHandlersPerformOnFailure(new OSDeviceInfoError(statusCode, response));
            }

            @Override
            void onSuccess(String response) {
                synchronized (LOCK) {
                    currentUserState.persistStateAfterSync(dependDiff, jsonBody);
                    onSuccessfulSync(jsonBody);
                }

                if (jsonBody.has(TAGS))
                   sendTagsHandlersPerformOnSuccess();

                if (jsonBody.has(EXTERNAL_USER_ID))
                    externalUserIdUpdateHandlersPerformOnSuccess();

                if (jsonBody.has(LANGUAGE))
                    deviceInfoHandlersPerformOnSuccess();
            }
        });
    }

    private void doCreateOrNewSession(final String userId, final JSONObject jsonBody, final JSONObject dependDiff) {
        String urlStr;
        if (userId == null)
            urlStr = "players";
        else
            urlStr = "players/" + userId + "/on_session";

        waitingForSessionResponse = true;
        addOnSessionOrCreateExtras(jsonBody);
        OneSignalRestClient.postSync(urlStr, jsonBody, new OneSignalRestClient.ResponseHandler() {
            @Override
            void onFailure(int statusCode, String response, Throwable throwable) {
                synchronized (LOCK) {
                    waitingForSessionResponse = false;
                    OneSignal.Log(OneSignal.LOG_LEVEL.WARN, "Failed last request. statusCode: " + statusCode + "\nresponse: " + response);

                    if (response400WithErrorsContaining(statusCode, response, "not a valid device_type"))
                        handlePlayerDeletedFromServer();
                    else
                        handleNetworkFailure(statusCode);
                }
            }

            @Override
            void onSuccess(String response) {
                synchronized (LOCK) {
                    waitingForSessionResponse = false;
                    currentUserState.persistStateAfterSync(dependDiff, jsonBody);

                    try {
                        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "doCreateOrNewSession:response: " + response);
                        JSONObject jsonResponse = new JSONObject(response);

                        if (jsonResponse.has(ID)) {
                            String newUserId = jsonResponse.optString(ID);
                            updateIdDependents(newUserId);
                            OneSignal.Log(OneSignal.LOG_LEVEL.INFO, "Device registered, UserId = " + newUserId);
                        }
                        else
                            OneSignal.Log(OneSignal.LOG_LEVEL.INFO, "session sent, UserId = " + userId);

                        getUserStateForModification().putOnDependValues(SESSION, false);
                        getUserStateForModification().persistState();

                        // List of in app messages to evaluate for the session
                        if (jsonResponse.has(IN_APP_MESSAGES_JSON_KEY))
                            OneSignal.getInAppMessageController().receivedInAppMessageJson(jsonResponse.getJSONArray(IN_APP_MESSAGES_JSON_KEY));

                        onSuccessfulSync(jsonBody);
                    } catch (JSONException e) {
                        OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "ERROR parsing on_session or create JSON Response.", e);
                    }
                }
            }
        });
    }

    protected abstract void onSuccessfulSync(JSONObject jsonField);

    private void handleNetworkFailure(int statusCode) {
        if (statusCode == HttpURLConnection.HTTP_FORBIDDEN) {
            OneSignal.Log(OneSignal.LOG_LEVEL.FATAL, "403 error updating player, omitting further retries!");
            fireNetworkFailureEvents();
            return;
        }

        boolean retried = getNetworkHandlerThread(NetworkHandlerThread.NETWORK_HANDLER_USERSTATE).doRetry();
        // If there are no more retries and still pending changes send out event of what failed to sync
        if (!retried)
            fireNetworkFailureEvents();
    }

    private void fireNetworkFailureEvents() {
        final JSONObject jsonBody = currentUserState.generateJsonDiff(toSyncUserState, false);
        if (jsonBody != null)
            fireEventsForUpdateFailure(jsonBody);

        if (getToSyncUserState().getDependValues().optBoolean(LOGOUT_EMAIL, false))
            OneSignal.handleFailedEmailLogout();
    }

    protected abstract void fireEventsForUpdateFailure(JSONObject jsonFields);

    protected abstract void addOnSessionOrCreateExtras(JSONObject jsonBody);

    private boolean response400WithErrorsContaining(int statusCode, String response, String contains) {
        if (statusCode == 400 && response != null) {
            try {
                JSONObject responseJson = new JSONObject(response);
                return responseJson.has(ERRORS) && responseJson.optString(ERRORS).contains(contains);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        return false;
    }

    protected NetworkHandlerThread getNetworkHandlerThread(Integer type) {
        synchronized (networkHandlerSyncLock) {
            if (!networkHandlerThreads.containsKey(type))
                networkHandlerThreads.put(type, new NetworkHandlerThread(type));
            return networkHandlerThreads.get(type);
        }
    }

    // Get a JSONObject to apply changes to
    // Schedules a job with a short delay to compare changes
    //   If there are differences a network call with the changes to made
    protected UserState getUserStateForModification() {
        if (toSyncUserState == null)
            toSyncUserState = getCurrentUserState().deepClone(TOSYNC_STATE);

        scheduleSyncToServer();

        return toSyncUserState;
    }

    abstract protected void scheduleSyncToServer();

    void updateDeviceInfo(JSONObject deviceInfo, @Nullable OSDeviceInfoCompletionHandler handler) {
        if (handler != null)
            this.deviceInfoCompletionHandler.add(handler);
        getUserStateForModification().generateJsonDiffFromIntoSyncValued(deviceInfo, null);
    }

    abstract void updateState(JSONObject state);

    void setNewSession() {
        try {
            synchronized (LOCK) {
                getUserStateForModification().putOnDependValues(SESSION, true);
                getUserStateForModification().persistState();
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    boolean getSyncAsNewSession() {
        return getUserStateForModification().getDependValues().optBoolean(SESSION);
    }

    void sendTags(JSONObject tags, @Nullable ChangeTagsUpdateHandler handler) {
        if (handler != null)
            this.sendTagsHandlers.add(handler);
        UserState userStateTags = getUserStateForModification();
        userStateTags.generateJsonDiffFromIntoSyncValued(tags, null);
    }

    void syncHashedEmail(JSONObject emailFields) {
        getUserStateForModification().generateJsonDiffFromIntoSyncValued(emailFields, null);
    }

    void setExternalUserId(final String externalId, final String externalIdAuthHash, OneSignal.OSInternalExternalUserIdUpdateCompletionHandler handler) throws JSONException {
        if (handler != null)
            this.externalUserIdUpdateHandlers.add(handler);

        UserState userState = getUserStateForModification();
        userState.putOnSyncValues(EXTERNAL_USER_ID, externalId);
        if (externalIdAuthHash != null)
            userState.putOnSyncValues(EXTERNAL_USER_ID_AUTH_HASH, externalIdAuthHash);
    }

    abstract void setSubscription(boolean enable);

    private void handlePlayerDeletedFromServer() {
        OneSignal.Log(OneSignal.LOG_LEVEL.WARN, "Creating new player based on missing player_id noted above.");
        OneSignal.handleSuccessfulEmailLogout();
        resetCurrentState();
        updateIdDependents(null);
        scheduleSyncToServer();
    }

    void resetCurrentState() {
        currentUserState.setSyncValues(new JSONObject());
        currentUserState.persistState();
    }

    public abstract boolean getUserSubscribePreference();
    public abstract void setPermission(boolean enable);

    void updateLocation(LocationController.LocationPoint point) {
        UserState userState = getUserStateForModification();
        userState.setLocation(point);
    }

    abstract void updateIdDependents(String id);

    abstract void logoutChannel();

    void sendPurchases(JSONObject jsonBody, OneSignalRestClient.ResponseHandler responseHandler) {
        OneSignalRestClient.post("players/" + getId() + "/on_purchase", jsonBody, responseHandler);
    }

    void readyToUpdate(boolean canMakeUpdates) {
        boolean changed = this.canMakeUpdates != canMakeUpdates;
        this.canMakeUpdates = canMakeUpdates;
        if (changed && canMakeUpdates)
            scheduleSyncToServer();
    }

    private void sendTagsHandlersPerformOnSuccess() {
        JSONObject tags = OneSignalStateSynchronizer.getTags(false).result;
        ChangeTagsUpdateHandler handler;
        while ((handler = sendTagsHandlers.poll()) != null)
            handler.onSuccess(tags);
    }

    private void sendTagsHandlersPerformOnFailure(SendTagsError error) {
        ChangeTagsUpdateHandler handler;
        while ((handler = sendTagsHandlers.poll()) != null)
            handler.onFailure(error);
    }

    private void externalUserIdUpdateHandlersPerformOnSuccess() {
        OneSignal.OSInternalExternalUserIdUpdateCompletionHandler handler;
        while ((handler = externalUserIdUpdateHandlers.poll()) != null) {
            handler.onComplete(getChannelString(), true);
        }
    }

    private void externalUserIdUpdateHandlersPerformOnFailure() {
        OneSignal.OSInternalExternalUserIdUpdateCompletionHandler handler;
        while ((handler = externalUserIdUpdateHandlers.poll()) != null) {
            handler.onComplete(getChannelString(), false);
        }
    }

    private void deviceInfoHandlersPerformOnSuccess() {
        String language = OneSignalStateSynchronizer.getLanguage();
        OSDeviceInfoCompletionHandler handler;
        while ((handler = deviceInfoCompletionHandler.poll()) != null) {
            handler.onSuccess(language);
        }
    }

    private void deviceInfoHandlersPerformOnFailure(OSDeviceInfoError error) {
        OSDeviceInfoCompletionHandler handler;
        while((handler = deviceInfoCompletionHandler.poll()) != null) {
            handler.onFailure(error);
        }
    }
}
