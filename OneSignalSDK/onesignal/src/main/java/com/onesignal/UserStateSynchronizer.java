package com.onesignal;

import android.os.Handler;
import android.os.HandlerThread;
import com.onesignal.OneSignal.ChangeTagsUpdateHandler;
import com.onesignal.OneSignal.SendTagsError;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

abstract class UserStateSynchronizer {

    static class GetTagsResult {
        boolean serverSuccess;
        JSONObject result;

        GetTagsResult(boolean serverSuccess, JSONObject result) {
            this.serverSuccess = serverSuccess;
            this.result = result;
        }
    }

    // Object to synchronize on to prevent concurrent modifications on syncValues and dependValues
    protected final Object syncLock = new Object() {};

    abstract boolean getSubscribed();

    String getRegistrationId() {
        return getToSyncUserState().syncValues.optString("identifier", null);
    }

    abstract GetTagsResult getTags(boolean fromServer);

    private AtomicBoolean runningSyncUserState = new AtomicBoolean();

    // maintain an array of handlers so that if the user calls
    // sendTags() multiple times, it will call each callback
    private ArrayList<ChangeTagsUpdateHandler> sendTagsHandlers = new ArrayList<ChangeTagsUpdateHandler>();

    class NetworkHandlerThread extends HandlerThread {
        protected static final int NETWORK_HANDLER_USERSTATE = 0;

        int mType;

        Handler mHandler = null;

        static final int MAX_RETRIES = 3, NETWORK_CALL_DELAY_TO_BUFFER_MS = 5_000;
        int currentRetry;

        NetworkHandlerThread(int type) {
            super("OSH_NetworkHandlerThread");
            mType = type;
            start();
            mHandler = new Handler(getLooper());
        }

        void runNewJobDelayed() {
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
                            if (!runningSyncUserState.get())
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

    protected boolean nextSyncIsSession = false, waitingForSessionResponse = false;

    // currentUserState - Current known state of the user on OneSignal's server.
    // toSyncUserState  - Pending state that will be synced to the OneSignal server.
    //                    diff will be generated between currentUserState when a sync call is made to the server.
    protected UserState currentUserState, toSyncUserState;

    protected JSONObject generateJsonDiff(JSONObject cur, JSONObject changedTo, JSONObject baseOutput, Set<String> includeFields) {
        synchronized (syncLock) {
            return JSONUtils.generateJsonDiff(cur, changedTo, baseOutput, includeFields);
        }
    }

    protected UserState getToSyncUserState() {
        synchronized (syncLock) {
            if (toSyncUserState == null)
                toSyncUserState = newUserState("TOSYNC_STATE", true);
        }

        return toSyncUserState;
    }

    void initUserState() {
        synchronized (syncLock) {
            if (currentUserState == null)
                currentUserState = newUserState("CURRENT_STATE", true);
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
            synchronized (syncLock) {
                boolean unSynced = currentUserState.generateJsonDiff(toSyncUserState, isSessionCall()) != null;
                toSyncUserState.persistState();
                return unSynced;
            }
        }
        return false;
    }

    protected abstract String getId();

    private boolean isSessionCall() {
        return nextSyncIsSession && !waitingForSessionResponse;
    }

    private boolean syncEmailLogout() {
        return getToSyncUserState().dependValues.optBoolean("logoutEmail", false);
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

        final boolean isSessionCall = isSessionCall();
        JSONObject jsonBody, dependDiff;
        synchronized (syncLock) {
            jsonBody = currentUserState.generateJsonDiff(getToSyncUserState(), isSessionCall);
            dependDiff = generateJsonDiff(currentUserState.dependValues, getToSyncUserState().dependValues, null, null);

            if (jsonBody == null) {
                currentUserState.persistStateAfterSync(dependDiff, null);

                for (ChangeTagsUpdateHandler handler : this.sendTagsHandlers) {
                    if (handler != null) {
                        handler.onSuccess(OneSignalStateSynchronizer.getTags(false).result);
                    }
                }

                this.sendTagsHandlers.clear();
                
                return;
            }
            getToSyncUserState().persistState();
        }

        if (!isSessionCall || fromSyncService)
            doPutSync(userId, jsonBody, dependDiff);
        else
            doCreateOrNewSession(userId, jsonBody, dependDiff);
    }

    private void doEmailLogout(String userId) {
        String urlStr = "players/" + userId + "/email_logout";
        JSONObject jsonBody = new JSONObject();
        try {
            JSONObject dependValues = currentUserState.dependValues;
            if (dependValues.has("email_auth_hash"))
                jsonBody.put("email_auth_hash", dependValues.optString("email_auth_hash"));

            JSONObject syncValues = currentUserState.syncValues;
            if (syncValues.has("parent_player_id"))
                jsonBody.put("parent_player_id", syncValues.optString("parent_player_id"));

            jsonBody.put("app_id", syncValues.optString("app_id"));
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
                    handleNetworkFailure();
            }

            @Override
            void onSuccess(String response) {
                logoutEmailSyncSuccess();
            }
        });
    }

    private void logoutEmailSyncSuccess() {
        getToSyncUserState().dependValues.remove("logoutEmail");
        toSyncUserState.dependValues.remove("email_auth_hash");
        toSyncUserState.syncValues.remove("parent_player_id");
        toSyncUserState.persistState();

        currentUserState.dependValues.remove("email_auth_hash");
        currentUserState.syncValues.remove("parent_player_id");
        String emailLoggedOut = currentUserState.syncValues.optString("email");
        currentUserState.syncValues.remove("email");

        OneSignalStateSynchronizer.setSyncAsNewSessionForEmail();

        OneSignal.Log(OneSignal.LOG_LEVEL.INFO, "Device successfully logged out of email: " + emailLoggedOut);
        OneSignal.handleSuccessfulEmailLogout();
    }

    private void doPutSync(String userId, final JSONObject jsonBody, final JSONObject dependDiff) {
        if (userId == null) {
            for (ChangeTagsUpdateHandler handler : this.sendTagsHandlers) {
                if (handler != null) {
                    handler.onFailure(new SendTagsError(-1, "Unable to update tags: the current user is not registered with OneSignal"));
                }
            }

            this.sendTagsHandlers.clear();

            return;
        }
        
        final ArrayList<ChangeTagsUpdateHandler> tagsHandlers = (ArrayList<ChangeTagsUpdateHandler>) this.sendTagsHandlers.clone();

        this.sendTagsHandlers.clear();

        OneSignalRestClient.putSync("players/" + userId, jsonBody, new OneSignalRestClient.ResponseHandler() {
            @Override
            void onFailure(int statusCode, String response, Throwable throwable) {
                OneSignal.Log(OneSignal.LOG_LEVEL.WARN, "Failed last request. statusCode: " + statusCode + "\nresponse: " + response);

                synchronized (syncLock) {
                    if (response400WithErrorsContaining(statusCode, response, "No user with this id found"))
                        handlePlayerDeletedFromServer();
                    else
                        handleNetworkFailure();
                }

                if (jsonBody.has("tags"))
                    for (ChangeTagsUpdateHandler handler : tagsHandlers) {
                        if (handler != null) {
                            handler.onFailure(new SendTagsError(statusCode, response));
                        }
                    }

            }

            @Override
            void onSuccess(String response) {
                synchronized (syncLock) {
                    currentUserState.persistStateAfterSync(dependDiff, jsonBody);
                    onSuccessfulSync(jsonBody);
                }

                JSONObject tags = OneSignalStateSynchronizer.getTags(false).result;

                if (jsonBody.has("tags") && tags != null)
                    for (ChangeTagsUpdateHandler handler : tagsHandlers) {
                        if (handler != null) {
                            handler.onSuccess(tags);
                        }
                    }

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
                synchronized (syncLock) {
                    waitingForSessionResponse = false;
                    OneSignal.Log(OneSignal.LOG_LEVEL.WARN, "Failed last request. statusCode: " + statusCode + "\nresponse: " + response);

                    if (response400WithErrorsContaining(statusCode, response, "not a valid device_type"))
                        handlePlayerDeletedFromServer();
                    else
                        handleNetworkFailure();
                }
            }

            @Override
            void onSuccess(String response) {
                synchronized (syncLock) {
                    nextSyncIsSession = waitingForSessionResponse = false;
                    currentUserState.persistStateAfterSync(dependDiff, jsonBody);

                    try {
                        JSONObject jsonResponse = new JSONObject(response);

                        if (jsonResponse.has("id")) {
                            String newUserId = jsonResponse.optString("id");
                            updateIdDependents(newUserId);

                            OneSignal.Log(OneSignal.LOG_LEVEL.INFO, "Device registered, UserId = " + newUserId);
                        }
                        else
                            OneSignal.Log(OneSignal.LOG_LEVEL.INFO, "session sent, UserId = " + userId);

                        OneSignal.updateOnSessionDependents();
                        onSuccessfulSync(jsonBody);
                    } catch (Throwable t) {
                        OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "ERROR parsing on_session or create JSON Response.", t);
                    }
                }
            }
        });
    }

    protected abstract void onSuccessfulSync(JSONObject jsonField);

    private void handleNetworkFailure() {
        boolean retried = getNetworkHandlerThread(NetworkHandlerThread.NETWORK_HANDLER_USERSTATE).doRetry();
        if (retried)
            return;

        // If there are no more retries and still pending changes send out event of what failed to sync
        final JSONObject jsonBody = currentUserState.generateJsonDiff(toSyncUserState, false);
        if (jsonBody != null)
            fireEventsForUpdateFailure(jsonBody);

        if (getToSyncUserState().dependValues.optBoolean("logoutEmail", false))
            OneSignal.handleFailedEmailLogout();
    }

    protected abstract void fireEventsForUpdateFailure(JSONObject jsonFields);

    protected abstract void addOnSessionOrCreateExtras(JSONObject jsonBody);

    private boolean response400WithErrorsContaining(int statusCode, String response, String contains) {
        if (statusCode == 400 && response != null) {
            try {
                JSONObject responseJson = new JSONObject(response);
                return responseJson.has("errors") && responseJson.optString("errors").contains(contains);
            } catch (Throwable t) {
                t.printStackTrace();
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
            toSyncUserState = currentUserState.deepClone("TOSYNC_STATE");

        scheduleSyncToServer();

        return toSyncUserState;
    }

    abstract protected void scheduleSyncToServer();

    void updateDeviceInfo(JSONObject deviceInfo) {
        JSONObject toSync = getUserStateForModification().syncValues;
        generateJsonDiff(toSync, deviceInfo, toSync, null);
    }

    abstract void updateState(JSONObject state);

    void setSyncAsNewSession() {
        nextSyncIsSession = true;
    }


    void sendTags(JSONObject tags, ChangeTagsUpdateHandler handler) {
        this.sendTagsHandlers.add(handler);
        JSONObject userStateTags = getUserStateForModification().syncValues;
        generateJsonDiff(userStateTags, tags, userStateTags, null);
    }

    void syncHashedEmail(JSONObject emailFields) {
        JSONObject syncValues = getUserStateForModification().syncValues;
        generateJsonDiff(syncValues, emailFields, syncValues, null);
    }

    abstract void setSubscription(boolean enable);

    private void handlePlayerDeletedFromServer() {
        OneSignal.handleSuccessfulEmailLogout();

        resetCurrentState();
        nextSyncIsSession = true;
        scheduleSyncToServer();
    }

    void resetCurrentState() {
        currentUserState.syncValues = new JSONObject();
        currentUserState.persistState();
    }

    public abstract boolean getUserSubscribePreference();
    public abstract void setPermission(boolean enable);

    void updateLocation(LocationGMS.LocationPoint point) {
        UserState userState = getUserStateForModification();
        userState.setLocation(point);
    }

    abstract void updateIdDependents(String id);

    abstract void logoutEmail();
}
