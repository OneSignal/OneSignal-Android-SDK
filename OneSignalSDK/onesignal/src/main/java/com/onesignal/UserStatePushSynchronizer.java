package com.onesignal;

import androidx.annotation.Nullable;

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
    void saveChannelId(String id) {
        OneSignal.saveUserId(id);
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
                        if (lastGetTagsResponse.has(TAGS)) {
                            synchronized(LOCK) {
                                JSONObject dependDiff = generateJsonDiff(getCurrentUserState().getSyncValues().optJSONObject(TAGS),
                                        getToSyncUserState().getSyncValues().optJSONObject(TAGS),
                                        null, null);

                                getCurrentUserState().putOnSyncValues(TAGS, lastGetTagsResponse.optJSONObject(TAGS));
                                getCurrentUserState().persistState();

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
            return new GetTagsResult(serverSuccess, JSONUtils.getJSONObjectWithoutBlankValues(getToSyncUserState().getSyncValues(), TAGS));
        }
    }

    @Override
    @Nullable String getExternalId(boolean fromServer) {
        synchronized(LOCK) {
            return getToSyncUserState().getSyncValues().optString(EXTERNAL_USER_ID, null);
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
            syncUpdate.putOpt(IDENTIFIER, pushState.optString(IDENTIFIER, null));
            if (pushState.has(DEVICE_TYPE))
                syncUpdate.put(DEVICE_TYPE, pushState.optInt(DEVICE_TYPE));
            syncUpdate.putOpt(PARENT_PLAYER_ID, pushState.optString(PARENT_PLAYER_ID, null));
            UserState userState = getUserStateForModification();
            userState.generateJsonDiffFromIntoSyncValued(syncUpdate, null);
        } catch(JSONException t) {
            t.printStackTrace();
        }

        try {
            JSONObject dependUpdate = new JSONObject();
            if (pushState.has(SUBSCRIBABLE_STATUS))
                dependUpdate.put(SUBSCRIBABLE_STATUS, pushState.optInt(SUBSCRIBABLE_STATUS));
            if (pushState.has(ANDROID_PERMISSION))
                dependUpdate.put(ANDROID_PERMISSION, pushState.optBoolean(ANDROID_PERMISSION));

            UserState userState = getUserStateForModification();
            userState.generateJsonDiffFromIntoDependValues(dependUpdate, null);
        } catch(JSONException t) {
            t.printStackTrace();
        }
    }

    void setEmail(String email, String emailAuthHash) {
        try {
            UserState userState = getUserStateForModification();

            userState.putOnDependValues(EMAIL_AUTH_HASH_KEY, emailAuthHash);
            userState.generateJsonDiffFromIntoSyncValued(new JSONObject().put(EMAIL_KEY, email), null);
        }
        catch (JSONException e) {
            e.printStackTrace();
        }
    }

    void setSMSNumber(String smsNumber, String smsAuthHash) {
        try {
            UserState userState = getUserStateForModification();

            userState.putOnDependValues(SMS_AUTH_HASH_KEY, smsAuthHash);
            userState.generateJsonDiffFromIntoSyncValued(new JSONObject().put(SMS_NUMBER_KEY, smsNumber), null);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    void setSubscription(boolean enable) {
        try {
            getUserStateForModification().putOnDependValues(USER_SUBSCRIBE_PREF, enable);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean getUserSubscribePreference() {
        return getToSyncUserState().getDependValues().optBoolean(USER_SUBSCRIBE_PREF, true);
    }

    public String getLanguage() {
        return  getToSyncUserState().getDependValues().optString(LANGUAGE, null);
    }

    @Override
    public void setPermission(boolean enable) {
        try {
            getUserStateForModification().putOnDependValues(ANDROID_PERMISSION, enable);
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
    void logoutChannel() {
    }

    void logoutEmail() {
        try {
            getUserStateForModification().putOnDependValues(LOGOUT_EMAIL, true);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    void logoutSMS() {
        UserState toSyncUserState = getToSyncUserState();
        toSyncUserState.removeFromDependValues(SMS_AUTH_HASH_KEY);
        toSyncUserState.removeFromSyncValues(SMS_NUMBER_KEY);
        toSyncUserState.persistState();

        UserState currentUserState = getCurrentUserState();
        currentUserState.removeFromDependValues(SMS_AUTH_HASH_KEY);
        String smsNumberLoggedOut = currentUserState.getSyncValues().optString(SMS_NUMBER_KEY);
        currentUserState.removeFromSyncValues(SMS_NUMBER_KEY);

        JSONObject result = new JSONObject();
        try {
            result.put(SMS_NUMBER_KEY, smsNumberLoggedOut);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        OneSignal.Log(OneSignal.LOG_LEVEL.INFO, "Device successfully logged out of SMS number: " + result);
        OneSignal.handleSuccessfulSMSlLogout(result);
    }

    @Override
    protected void fireEventsForUpdateFailure(JSONObject jsonFields) {
        if (jsonFields.has(EMAIL_KEY))
            OneSignal.fireEmailUpdateFailure();

        if (jsonFields.has(SMS_NUMBER_KEY))
            OneSignal.fireSMSUpdateFailure();
    }

    @Override
    protected void onSuccessfulSync(JSONObject jsonFields) {
        if (jsonFields.has(EMAIL_KEY))
            OneSignal.fireEmailUpdateSuccess();

        if (jsonFields.has(SMS_NUMBER_KEY)) {
            JSONObject result = new JSONObject();
            try {
                result.put(SMS_NUMBER_KEY, jsonFields.get(SMS_NUMBER_KEY));
                if (jsonFields.has(SMS_AUTH_HASH_KEY))
                    result.put(SMS_AUTH_HASH_KEY, jsonFields.get(SMS_AUTH_HASH_KEY));
            } catch (JSONException e) {
                e.printStackTrace();
            }
            OneSignal.fireSMSUpdateSuccess(result);
        }
    }
}
