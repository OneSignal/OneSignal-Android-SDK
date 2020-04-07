package com.onesignal.influence;

import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.onesignal.OSLogger;
import com.onesignal.OneSignal;
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

        logger.log(OneSignal.LOG_LEVEL.DEBUG, "OneSignal InAppMessageTracker initInfluencedTypeFromCache: " + this.toString());
    }

    @Override
    void addSessionData(@NonNull JSONObject jsonObject, OSInfluence influence) {
        // In app message don't influence the session
    }

    @Override
    void clearInfluenceData() {
        dataRepository.clearIAMData();
    }

    @Override
    JSONArray getLastChannelObjectsReceivedByNewId(String id) {
        JSONArray lastChannelObjectReceived;
        try {
            lastChannelObjectReceived = getLastChannelObjects();
        } catch (JSONException exception) {
            logger.log(OneSignal.LOG_LEVEL.ERROR, "Generating IAM tracker getLastChannelObjects JSONObject ", exception);
            return new JSONArray();
        }

        // For IAM we handle redisplay, we need to remove duplicates for new influence Id
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            for (int i = 0; i < lastChannelObjectReceived.length(); i++) {
                try {
                    String objectId = lastChannelObjectReceived.getJSONObject(i).getString(getIdTag());
                    if (id.equals(objectId)) {
                        // We call this method for each id saved, it can only have 1 duplicate
                        lastChannelObjectReceived.remove(i);
                        break;
                    }
                } catch (JSONException exception) {
                    logger.log(OneSignal.LOG_LEVEL.ERROR, "Generating tracker lastChannelObjectReceived get JSONObject ", exception);
                }
            }
        } else {
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
                logger.log(OneSignal.LOG_LEVEL.ERROR, "Before KITKAT API, Generating tracker lastChannelObjectReceived get JSONObject ", exception);
            }
        }

        return lastChannelObjectReceived;
    }

    @Override
    public void cacheState() {
        dataRepository.cacheIAMInfluenceType(influenceType == null ? OSInfluenceType.UNATTRIBUTED : influenceType);
    }
}
