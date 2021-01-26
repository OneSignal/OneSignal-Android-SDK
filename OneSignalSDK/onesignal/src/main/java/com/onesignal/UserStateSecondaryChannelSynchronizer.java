package com.onesignal;

import androidx.annotation.Nullable;

import com.onesignal.OneSignalStateSynchronizer.UserStateSynchronizerType;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

abstract class UserStateSecondaryChannelSynchronizer extends UserStateSynchronizer {

    UserStateSecondaryChannelSynchronizer(UserStateSynchronizerType channel) {
        super(channel);
    }

    @Override
    protected abstract UserState newUserState(String inPersistKey, boolean load);

    @Override
    abstract protected String getId();

    @Override
    abstract void logoutEmail();

    @Override
    abstract void logoutSMS();

    abstract protected int getDeviceType();

    abstract void fireUpdateSuccess();

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
            jsonBody.put("device_type", getDeviceType());
            jsonBody.putOpt("device_player_id", OneSignal.getUserId());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void fireEventsForUpdateFailure(JSONObject jsonFields) {
        if (jsonFields.has("identifier"))
            fireUpdateFailure();
    }

    @Override
    protected void onSuccessfulSync(JSONObject jsonFields) {
        if (jsonFields.has("identifier"))
            fireUpdateSuccess();
    }
}
