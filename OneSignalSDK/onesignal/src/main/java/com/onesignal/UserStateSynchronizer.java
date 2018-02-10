package com.onesignal;

import android.os.Handler;
import android.os.HandlerThread;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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

    class NetworkHandlerThread extends HandlerThread {
        protected static final int NETWORK_HANDLER_USERSTATE = 0;

        int mType;

        Handler mHandler = null;

        static final int MAX_RETRIES = 3;
        int currentRetry;

        NetworkHandlerThread(int type) {
            super("OSH_NetworkHandlerThread");
            mType = type;
            start();
            mHandler = new Handler(getLooper());
        }

        void runNewJob() {
            currentRetry = 0;
            mHandler.removeCallbacksAndMessages(null);
            mHandler.postDelayed(getNewRunnable(), 5000);
        }

        private Runnable getNewRunnable() {
            switch (mType) {
                case NETWORK_HANDLER_USERSTATE:
                    return new Runnable() {
                        @Override
                        public void run() {
                            syncUserState(false);
                        }
                    };
            }

            return null;
        }

        void stopScheduledRunnable() {
            mHandler.removeCallbacksAndMessages(null);
        }

        void doRetry() {
            if (currentRetry < MAX_RETRIES && !mHandler.hasMessages(0)) {
                currentRetry++;
                mHandler.postDelayed(getNewRunnable(), currentRetry * 15000);
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
        if (toSyncUserState == null)
            toSyncUserState = newUserState("TOSYNC_STATE", true);

        return toSyncUserState;
    }

    void initUserState() {
        if (currentUserState == null)
            currentUserState = newUserState("CURRENT_STATE", true);

        if (toSyncUserState == null)
            toSyncUserState = newUserState("TOSYNC_STATE", true);
    }

    abstract protected UserState newUserState(String inPersistKey, boolean load);

    void clearLocation() {
        getToSyncUserState().clearLocation();
        getToSyncUserState().persistState();
    }

    boolean stopAndPersist() {
        for (Map.Entry<Integer, NetworkHandlerThread> handlerThread : networkHandlerThreads.entrySet())
            handlerThread.getValue().stopScheduledRunnable();

        if (toSyncUserState != null) {
            boolean unSynced = currentUserState.generateJsonDiff(toSyncUserState, isSessionCall()) != null;
            toSyncUserState.persistState();
            return unSynced;
        }
        return false;
    }

    abstract String getId();

    private boolean isSessionCall() {
        final String userId = getId();
        return userId == null || (nextSyncIsSession && !waitingForSessionResponse);
    }

    void syncUserState(boolean fromSyncService) {
        final String userId = getId();
        boolean isSessionCall =  isSessionCall();

        final JSONObject jsonBody = currentUserState.generateJsonDiff(toSyncUserState, isSessionCall);
        final JSONObject dependDiff = generateJsonDiff(currentUserState.dependValues, toSyncUserState.dependValues, null, null);

        if (jsonBody == null) {
            currentUserState.persistStateAfterSync(dependDiff, null);
            return;
        }
        toSyncUserState.persistState();

        // Prevent non-create player network calls when we don't have a player id yet.
        if (userId == null && !nextSyncIsSession)
            return;

        if (!isSessionCall || fromSyncService) {
            OneSignalRestClient.putSync("players/" + userId, jsonBody, new OneSignalRestClient.ResponseHandler() {
                @Override
                void onFailure(int statusCode, String response, Throwable throwable) {
                    OneSignal.Log(OneSignal.LOG_LEVEL.WARN, "Failed last request. statusCode: " + statusCode + "\nresponse: " + response);

                    if (response400WithErrorsContaining(statusCode, response, "No user with this id found"))
                        handlePlayerDeletedFromServer();
                    else
                        getNetworkHandlerThread(NetworkHandlerThread.NETWORK_HANDLER_USERSTATE).doRetry();
                }

                @Override
                void onSuccess(String response) {
                    currentUserState.persistStateAfterSync(dependDiff, jsonBody);
                }
            });
        }
        else {
            String urlStr;
            if (userId == null)
                urlStr = "players";
            else
                urlStr = "players/" + userId + "/on_session";

            waitingForSessionResponse = true;
            addPostUserExtras(jsonBody);
            OneSignalRestClient.postSync(urlStr, jsonBody, new OneSignalRestClient.ResponseHandler() {
                @Override
                void onFailure(int statusCode, String response, Throwable throwable) {
                    waitingForSessionResponse = false;
                    OneSignal.Log(OneSignal.LOG_LEVEL.WARN, "Failed last request. statusCode: " + statusCode + "\nresponse: " + response);

                    if (response400WithErrorsContaining(statusCode, response, "not a valid device_type"))
                        handlePlayerDeletedFromServer();
                    else
                        getNetworkHandlerThread(NetworkHandlerThread.NETWORK_HANDLER_USERSTATE).doRetry();
                }

                @Override
                void onSuccess(String response) {
                    nextSyncIsSession = waitingForSessionResponse = false;
                    currentUserState.persistStateAfterSync(dependDiff, jsonBody);

                    try {
                        JSONObject jsonResponse = new JSONObject(response);

                        if (jsonResponse.has("id")) {
                            String userId = jsonResponse.optString("id");
                            updateIdDependents(userId);

                            OneSignal.Log(OneSignal.LOG_LEVEL.INFO, "Device registered, UserId = " + userId);
                        }
                        else
                            OneSignal.Log(OneSignal.LOG_LEVEL.INFO, "session sent, UserId = " + getId());

                        OneSignal.updateOnSessionDependents();
                    } catch (Throwable t) {
                        OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "ERROR parsing on_session or create JSON Response.", t);
                    }
                }
            });
        }
    }

    protected abstract void addPostUserExtras(JSONObject jsonBody);

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

    protected UserState getUserStateForModification() {
        if (toSyncUserState == null)
            toSyncUserState = currentUserState.deepClone("TOSYNC_STATE");

        postNewSyncUserState();

        return toSyncUserState;
    }

    abstract protected void postNewSyncUserState();

    void updateDeviceInfo(JSONObject deviceInfo) {
        JSONObject toSync = getUserStateForModification().syncValues;
        generateJsonDiff(toSync, deviceInfo, toSync, null);

        nextSyncIsSession = nextSyncIsSession || getId() == null;
    }

    abstract void updateState(JSONObject state);

    void setSyncAsNewSession() {
        nextSyncIsSession = true;
    }


    void sendTags(JSONObject tags) {
        JSONObject userStateTags = getUserStateForModification().syncValues;
        generateJsonDiff(userStateTags, tags, userStateTags, null);
    }

    void syncHashedEmail(JSONObject emailFields) {
        JSONObject syncValues = getUserStateForModification().syncValues;
        generateJsonDiff(syncValues, emailFields, syncValues, null);
    }

    abstract void setEmail(String email);

    abstract void setSubscription(boolean enable);

    private void handlePlayerDeletedFromServer() {
        resetCurrentState();
        nextSyncIsSession = true;
        postNewSyncUserState();
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
}
