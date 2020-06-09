package com.onesignal.influence;

import android.support.annotation.NonNull;

import com.onesignal.OSLogger;
import com.onesignal.influence.model.OSInfluence;
import com.onesignal.influence.model.OSInfluenceChannel;
import com.onesignal.influence.model.OSInfluenceType;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

class OSInAppMessageTracker extends OSChannelTracker {

    public static final String TAG = OSInAppMessageTracker.class.getCanonicalName();
    private static final String IAM_ID = "iam_id";

    OSInAppMessageTracker(@NonNull OSInfluenceDataRepository dataRepository, OSLogger logger) {
        super(dataRepository, logger);
    }

    @Override
    public String getIdTag() {
        return IAM_ID;
    }

    @Override
    OSInfluenceChannel getChannelType() {
        return OSInfluenceChannel.IAM;
    }

    @Override
    JSONArray getLastChannelObjectsReceivedByNewId(String id) {
        JSONArray lastChannelObjectReceived;
        try {
            lastChannelObjectReceived = getLastChannelObjects();
        } catch (JSONException exception) {
            logger.error("Generating IAM tracker getLastChannelObjects JSONObject ", exception);
            return new JSONArray();
        }

        // For IAM we handle redisplay, we need to remove duplicates for new influence Id
        // If min sdk is greater than KITKAT we can refactor this logic to removeObject from JSONArray
        try {
            JSONArray auxLastChannelObjectReceived = new JSONArray();
            for (int i = 0; i < lastChannelObjectReceived.length(); i++) {
                String objectId = lastChannelObjectReceived.getJSONObject(i).getString(getIdTag());
                if (!id.equals(objectId)) {
                    auxLastChannelObjectReceived.put(lastChannelObjectReceived.getJSONObject(i));
                }
            }
            lastChannelObjectReceived = auxLastChannelObjectReceived;
        } catch (JSONException exception) {
            logger.error("Before KITKAT API, Generating tracker lastChannelObjectReceived get JSONObject ", exception);

        }

        return lastChannelObjectReceived;
    }

    @Override
    JSONArray getLastChannelObjects() throws JSONException {
        return dataRepository.getLastIAMsReceivedData();
    }

    @Override
    int getChannelLimit() {
        return dataRepository.getIAMLimit();
    }

    @Override
    int getIndirectAttributionWindow() {
        return dataRepository.getIAMIndirectAttributionWindow();
    }

    @Override
    void saveChannelObjects(JSONArray channelObjects) {
        dataRepository.saveIAMs(channelObjects);
    }

    @Override
    void initInfluencedTypeFromCache() {
        setInfluenceType(dataRepository.getIAMCachedInfluenceType());
        if (influenceType != null && influenceType.isIndirect())
            setIndirectIds(getLastReceivedIds());

        logger.debug("OneSignal InAppMessageTracker initInfluencedTypeFromCache: " + this.toString());
    }

    @Override
    void addSessionData(@NonNull JSONObject jsonObject, OSInfluence influence) {
        // In app message don't influence the session
    }

    @Override
    public void cacheState() {
        // We only need to cache INDIRECT and UNATTRIBUTED influence types
        // DIRECT is downgrade to INDIRECT to avoid inconsistency state
        // where the app might be close before dismissing current displayed IAM
        OSInfluenceType influenceTypeToCache = influenceType == null ? OSInfluenceType.UNATTRIBUTED : influenceType;
        dataRepository.cacheIAMInfluenceType(influenceTypeToCache == OSInfluenceType.DIRECT ? OSInfluenceType.INDIRECT : influenceTypeToCache);
    }
}
