package com.onesignal;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class UserStateSMSSynchronizer extends UserStateSecondaryChannelSynchronizer {

    private static final String SMS_NUMBER_KEY = "sms_number";
    private static final String SMS_AUTH_HASH_KEY = "sms_auth_hash";

    UserStateSMSSynchronizer() {
        super(OneSignalStateSynchronizer.UserStateSynchronizerType.SMS);
    }

    @Override
    protected UserState newUserState(String inPersistKey, boolean load) {
        return new UserStateSMS(inPersistKey, load);
    }

    @Override
    protected String getId() {
        return OneSignal.getSMSId();
    }

    @Override
    void logoutEmail() {
    }

    @Override
    void logoutSMS() {
        OneSignal.saveSMSId("");

        resetCurrentState();
        getToSyncUserState().removeFromSyncValues("identifier");
        List<String> keysToRemove = new ArrayList<>();
        keysToRemove.add("sms_auth_hash");
        keysToRemove.add("device_player_id");
        keysToRemove.add("external_user_id");
        getToSyncUserState().removeFromSyncValues(keysToRemove);
        getToSyncUserState().persistState();

        OneSignal.getSMSSubscriptionState().clearSMSAndId();
    }

    @Override
    protected String getChannelKey() {
        return SMS_NUMBER_KEY;
    }

    @Override
    protected String getAuthHashKey() {
        return SMS_AUTH_HASH_KEY;
    }

    @Override
    protected int getDeviceType() {
        return UserState.DEVICE_TYPE_SMS;
    }

    @Override
    void fireUpdateSuccess(JSONObject result) {
        OneSignal.fireSMSUpdateSuccess(result);
    }

    @Override
    void fireUpdateFailure() {
        OneSignal.fireSMSUpdateFailure();
    }

    @Override
    void updateIdDependents(String id) {
        OneSignal.updateSMSIdDependents(id);
    }

    void setSMSNumber(String smsNumber, String smsAuthHash) {
        UserState userState = getUserStateForModification();
        ImmutableJSONObject syncValues = userState.getSyncValues();

        boolean noChange = smsNumber.equals(syncValues.optString(IDENTIFIER)) &&
                syncValues.optString(getAuthHashKey()).equals(smsAuthHash == null ? "" : smsAuthHash);
        if (noChange) {
            JSONObject result = new JSONObject();
            try {
                result.put(IDENTIFIER, smsNumber);
                result.put(getAuthHashKey(), smsAuthHash);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            fireUpdateSuccess(result);
            return;
        }

        String existingsms = syncValues.optString(IDENTIFIER, null);

        if (existingsms == null)
            setNewSession();

        try {
            JSONObject smsJSON = new JSONObject();
            smsJSON.put(IDENTIFIER, smsNumber);

            if (smsAuthHash != null)
                smsJSON.put(getAuthHashKey(), smsAuthHash);

            if (smsAuthHash == null) {
                if (existingsms != null && !existingsms.equals(smsNumber)) {
                    OneSignal.saveSMSId("");
                    resetCurrentState();
                    setNewSession();
                }
            }

            userState.generateJsonDiffFromIntoSyncValued(smsJSON, null);
            scheduleSyncToServer();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
