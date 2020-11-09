package com.onesignal;

import android.support.annotation.Nullable;

import com.onesignal.OneSignalStateSynchronizer.UserStateSynchronizerType;

import org.json.JSONException;
import org.json.JSONObject;

class UserStatePushSynchronizer extends UserStateSynchronizer {

    UserStatePushSynchronizer() {
        super(UserStateSynchronizerType.PUSH);
    }

    @Override
    protected UserState newUserState(String inPersistKey, boolean load) {
        return new UserStatePush(inPersistKey, load);
    }

    @Override
    protected OneSignal.LOG_LEVEL getLogLevel() {
        return OneSignal.LOG_LEVEL.ERROR;
    }

    @Override
    boolean getSubscribed() {
        return getToSyncUserState().isSubscribed();
    }

    private static boolean serverSuccess;

    @Override
    GetTagsResult getTags(boolean fromServer) {
        if (fromServer) {
            String userId = OneSignal.getUserId();
            String appId = OneSignal.getSavedAppId();

            OneSignalRestClient.getSync("players/" + userId + "?app_id=" + appId, new OneSignalRestClient.ResponseHandler() {
                @Override
                void onSuccess(String responseStr) {
                    serverSuccess = true;

                    // This should not typically come from the server as null or empty, but due to Issue #904
                    // This check is added and will prevent further crashes
                    // https://github.com/OneSignal/OneSignal-Android-SDK/issues/904
                    if (responseStr == null || responseStr.isEmpty())
                        responseStr = "{}";

                    try {
                        JSONObject lastGetTagsResponse = new JSONObject(responseStr);
                        if (lastGetTagsResponse.has("tags")) {
                            synchronized(LOCK) {
                                JSONObject dependDiff = generateJsonDiff(currentUserState.getSyncValues().optJSONObject("tags"),
                                        getToSyncUserState().getSyncValues().optJSONObject("tags"),
                                        null, null);

                                currentUserState.putOnSyncValues("tags", lastGetTagsResponse.optJSONObject("tags"));
                                currentUserState.persistState();

                                // Allow server side tags to overwrite local tags expect for any pending changes
                                //  that haven't been successfully posted.
                                getToSyncUserState().mergeTags(lastGetTagsResponse, dependDiff);
                                getToSyncUserState().persistState();
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }, OneSignalRestClient.CACHE_KEY_GET_TAGS);
        }

        synchronized(LOCK) {
            return new GetTagsResult(serverSuccess, JSONUtils.getJSONObjectWithoutBlankValues(toSyncUserState.getSyncValues(), "tags"));
        }
    }

    @Override
    @Nullable String getExternalId(boolean fromServer) {
        synchronized(LOCK) {
            return toSyncUserState.getSyncValues().optString("external_user_id", null);
        }
    }

    @Override
    protected void scheduleSyncToServer() {
        getNetworkHandlerThread(NetworkHandlerThread.NETWORK_HANDLER_USERSTATE).runNewJobDelayed();
    }

    @Override
    void updateState(JSONObject pushState) {
        try {
            JSONObject syncUpdate = new JSONObject();
            syncUpdate.putOpt("identifier", pushState.optString("identifier", null));
            if (pushState.has("device_type"))
                syncUpdate.put("device_type", pushState.optInt("device_type"));
            syncUpdate.putOpt("parent_player_id", pushState.optString("parent_player_id", null));
            UserState userState = getUserStateForModification();
            userState.generateJsonDiffFromIntoSyncValued(syncUpdate, null);
        } catch(JSONException t) {
            t.printStackTrace();
        }

        try {
            JSONObject dependUpdate = new JSONObject();
            if (pushState.has("subscribableStatus"))
                dependUpdate.put("subscribableStatus", pushState.optInt("subscribableStatus"));
            if (pushState.has("androidPermission"))
                dependUpdate.put("androidPermission", pushState.optBoolean("androidPermission"));

            UserState userState = getUserStateForModification();
            userState.generateJsonDiffFromIntoDependValues(dependUpdate, null);
        } catch(JSONException t) {
            t.printStackTrace();
        }
    }

    void setEmail(String email, String emailAuthHash) {
        try {
            UserState userState = getUserStateForModification();

            userState.putOnDependValues("email_auth_hash", emailAuthHash);
            userState.generateJsonDiffFromIntoSyncValued(new JSONObject().put("email", email), null);
        }
        catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    void setSubscription(boolean enable) {
        try {
            getUserStateForModification().putOnDependValues("userSubscribePref", enable);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean getUserSubscribePreference() {
        return getToSyncUserState().getDependValues().optBoolean("userSubscribePref", true);
    }

    @Override
    public void setPermission(boolean enable) {
        try {
            getUserStateForModification().putOnDependValues("androidPermission", enable);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected String getId() {
        return OneSignal.getUserId();
    }

    @Override
    void updateIdDependents(String id) {
        OneSignal.updateUserIdDependents(id);
    }

    @Override
    protected void addOnSessionOrCreateExtras(JSONObject jsonBody) {}

    @Override
    void logoutEmail() {
        try {
            getUserStateForModification().putOnDependValues("logoutEmail", true);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void fireEventsForUpdateFailure(JSONObject jsonFields) {
        if (jsonFields.has("email"))
            OneSignal.fireEmailUpdateFailure();
    }

    @Override
    protected void onSuccessfulSync(JSONObject jsonFields) {
        if (jsonFields.has("email"))
            OneSignal.fireEmailUpdateSuccess();
        if (jsonFields.has("identifier"))
            OneSignal.fireIdsAvailableCallback();
    }
}
