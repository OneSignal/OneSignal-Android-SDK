package com.onesignal;

import org.json.JSONException;
import org.json.JSONObject;

class UserStatePushSynchronizer extends UserStateSynchronizer {

    @Override
    boolean getSubscribed() {
        return getToSyncUserState().getNotificationTypes() > 0;
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
    protected String userStatePrefix() {
        return "";
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
}
