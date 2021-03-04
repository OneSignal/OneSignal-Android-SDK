package com.onesignal;

import androidx.annotation.Nullable;

import com.onesignal.OneSignalStateSynchronizer.UserStateSynchronizerType;

import org.json.JSONException;
import org.json.JSONObject;

abstract class UserStateSecondaryChannelSynchronizer extends UserStateSynchronizer {

    UserStateSecondaryChannelSynchronizer(UserStateSynchronizerType channel) {
        super(channel);
    }

    @Override
    protected abstract UserState newUserState(String inPersistKey, boolean load);

    @Override
    abstract protected String getId();

    @Override
    abstract void logoutChannel();

    abstract protected String getChannelKey();
    abstract protected String getAuthHashKey();

    abstract protected int getDeviceType();

    abstract void fireUpdateSuccess(JSONObject result);

    abstract void fireUpdateFailure();

    @Override
    abstract void updateIdDependents(String id);

    @Override
    protected OneSignal.LOG_LEVEL getLogLevel() {
        return OneSignal.LOG_LEVEL.INFO;
    }

    // Secondary channel subscriptions are not readable from SDK
    @Override
    boolean getSubscribed() {
        return false;
    }

    // Secondary channel tags not readable from SDK
    @Override
    GetTagsResult getTags(boolean fromServer) {
        return null;
    }

    // Secondary channel external id not readable from SDK
    @Override
    @Nullable
    String getExternalId(boolean fromServer) {
        return null;
    }

    // Secondary channel subscription not settable from SDK
    @Override
    void setSubscription(boolean enable) {}

    // Secondary channel does not have a user preference on the SDK
    @Override
    public boolean getUserSubscribePreference() {
        return false;
    }

    // Secondary channel subscription not readable from SDK
    @Override
    public void setPermission(boolean enable) {}

    @Override
    void updateState(JSONObject state) {}

    void refresh() {
        scheduleSyncToServer();
    }

    @Override
    protected void scheduleSyncToServer() {
        // Don't make a POST / PUT network call if we never set an email/SMS.
        boolean userNotRegistered = getId() == null && getRegistrationId() == null;
        if (userNotRegistered || OneSignal.getUserId() == null)
            return;

        getNetworkHandlerThread(NetworkHandlerThread.NETWORK_HANDLER_USERSTATE).runNewJobDelayed();
    }

    @Override
    protected void addOnSessionOrCreateExtras(JSONObject jsonBody) {
        try {
            jsonBody.put(DEVICE_TYPE, getDeviceType());
            jsonBody.putOpt(DEVICE_PLAYER_ID, OneSignal.getUserId());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void fireEventsForUpdateFailure(JSONObject jsonFields) {
        if (jsonFields.has(IDENTIFIER))
            fireUpdateFailure();
    }

    @Override
    protected void onSuccessfulSync(JSONObject jsonFields) {
        if (jsonFields.has(IDENTIFIER)) {
            JSONObject result = new JSONObject();
            try {
                result.put(getChannelKey(), jsonFields.get(IDENTIFIER));
                if (jsonFields.has(getAuthHashKey()))
                    result.put(getAuthHashKey(), jsonFields.get(getAuthHashKey()));
            } catch (JSONException e) {
                e.printStackTrace();
            }
            fireUpdateSuccess(result);
        }
    }

    void setChannelId(String id, String idAuthHash) {
        UserState userState = getUserStateForModification();
        ImmutableJSONObject syncValues = userState.getSyncValues();

        boolean noChange = id.equals(syncValues.optString(IDENTIFIER)) &&
                syncValues.optString(getAuthHashKey()).equals(idAuthHash == null ? "" : idAuthHash);
        if (noChange) {
            JSONObject result = new JSONObject();
            try {
                result.put(getChannelKey(), id);
                result.put(getAuthHashKey(), idAuthHash);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            fireUpdateSuccess(result);
            return;
        }

        String existingEmail = syncValues.optString(IDENTIFIER, null);

        if (existingEmail == null)
            setNewSession();

        try {
            JSONObject emailJSON = new JSONObject();
            emailJSON.put(IDENTIFIER, id);

            if (idAuthHash != null)
                emailJSON.put(getAuthHashKey(), idAuthHash);

            if (idAuthHash == null) {
                if (existingEmail != null && !existingEmail.equals(id)) {
                    saveChannelId("");
                    resetCurrentState();
                    setNewSession();
                }
            }

            userState.generateJsonDiffFromIntoSyncValued(emailJSON, null);
            scheduleSyncToServer();
        }
        catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
