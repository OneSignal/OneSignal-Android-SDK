package com.onesignal;

import org.json.JSONException;
import org.json.JSONObject;

class UserStatePushSynchronizer extends UserStateSynchronizer {

    @Override
    protected UserState newUserState(String inPersistKey, boolean load) {
        return new UserStatePush(inPersistKey, load);
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
                    try {
                        JSONObject lastGetTagsResponse = new JSONObject(responseStr);
                        if (lastGetTagsResponse.has("tags")) {
                            synchronized(syncLock) {
                                JSONObject dependDiff = generateJsonDiff(currentUserState.syncValues.optJSONObject("tags"),
                                        toSyncUserState.syncValues.optJSONObject("tags"),
                                        null, null);

                                currentUserState.syncValues.put("tags", lastGetTagsResponse.optJSONObject("tags"));
                                currentUserState.persistState();

                                // Allow server side tags to overwrite local tags expect for any pending changes
                                //  that haven't been successfully posted.
                                toSyncUserState.mergeTags(lastGetTagsResponse, dependDiff);
                                toSyncUserState.persistState();
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        synchronized(syncLock) {
            return new GetTagsResult(serverSuccess, JSONUtils.getJSONObjectWithoutBlankValues(getToSyncUserState().syncValues, "tags"));
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
            JSONObject toSync = getUserStateForModification().syncValues;
            generateJsonDiff(toSync, syncUpdate, toSync, null);
        } catch(JSONException t) {
            t.printStackTrace();
        }

        try {
            JSONObject dependUpdate = new JSONObject();
            if (pushState.has("subscribableStatus"))
                dependUpdate.put("subscribableStatus", pushState.optInt("subscribableStatus"));
            if (pushState.has("androidPermission"))
                dependUpdate.put("androidPermission", pushState.optBoolean("androidPermission"));
            JSONObject dependValues = getUserStateForModification().dependValues;
            generateJsonDiff(dependValues, dependUpdate, dependValues, null);
        } catch(JSONException t) {
            t.printStackTrace();
        }
    }

    void setEmail(String email, String emailAuthHash) {
        try {
            UserState userState = getUserStateForModification();

            userState.dependValues.put("email_auth_hash", emailAuthHash);

            JSONObject syncValues = userState.syncValues;
            generateJsonDiff(syncValues, new JSONObject().put("email", email), syncValues, null);
        }
        catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    void setSubscription(boolean enable) {
        try {
            getUserStateForModification().dependValues.put("userSubscribePref", enable);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean getUserSubscribePreference() {
        return getToSyncUserState().dependValues.optBoolean("userSubscribePref", true);
    }

    @Override
    public void setPermission(boolean enable) {
        try {
            getUserStateForModification().dependValues.put("androidPermission", enable);
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
            getUserStateForModification().dependValues.put("logoutEmail", true);
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
    }
}
