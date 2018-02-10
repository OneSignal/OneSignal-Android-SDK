package com.onesignal;

import org.json.JSONException;
import org.json.JSONObject;

class UserStateEmailSynchronizer extends UserStateSynchronizer {

    @Override
    protected UserState newUserState(String inPersistKey, boolean load) {
        return new UserStateEmail(inPersistKey, load);
    }

    // Email subscription not readable from SDK
    @Override
    boolean getSubscribed() {
        return false;
    }

    // Email tags not readable from SDK
    @Override
    GetTagsResult getTags(boolean fromServer) {
        return null;
    }

    // Email subscription not settable from SDK
    @Override
    void setSubscription(boolean enable) {}

    // Email does not have a user preference on the SDK
    @Override
    public boolean getUserSubscribePreference() {
        return false;
    }

    // Email subscription not readable from SDK
    @Override
    public void setPermission(boolean enable) {}

    @Override
    void updateState(JSONObject state) {}

    void refresh() {
        postNewSyncUserState();
    }

    @Override
    protected void postNewSyncUserState() {
        // Don't make a POST / PUT network call if we never set an email.

        System.out.println("getId()" + getId());
        System.out.println("getRegistrationId()" + getRegistrationId());
        System.out.println("OneSignal.getUserId()()" + OneSignal.getUserId());


        boolean neverEmail = getId() == null && getRegistrationId() == null;
        if (neverEmail || OneSignal.getUserId() == null) {
            // TODO: Research if persistState is needed here at all.
            //       Persisting here is to often. Locks some tests, could be an issue on a device.
            //       Could do so on a delay if we still need this.
            // toSyncUserState.persistState();
            return;
        }

        getNetworkHandlerThread(NetworkHandlerThread.NETWORK_HANDLER_USERSTATE).runNewJob();
    }


    @Override
    void setEmail(String email) {
        try {
            JSONObject emailJSON = new JSONObject();
            emailJSON.put("identifier", email);

            JSONObject syncValues = getUserStateForModification().syncValues;
            generateJsonDiff(syncValues, emailJSON, syncValues, null);
        }
        catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    String getId() {
        return OneSignal.getEmailId();
    }

    @Override
    void updateIdDependents(String id) {
        OneSignal.updateEmailIdDependents(id);
    }

    @Override
    protected void addPostUserExtras(JSONObject jsonBody) {
        try {
            jsonBody.put("device_type", 11);
            jsonBody.put("device_player_id", OneSignal.getUserId());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
