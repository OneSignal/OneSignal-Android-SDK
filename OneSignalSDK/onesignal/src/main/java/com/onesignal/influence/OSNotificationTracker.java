package com.onesignal.influence;

import android.support.annotation.NonNull;

import com.onesignal.OSLogger;
import com.onesignal.influence.model.OSInfluence;
import com.onesignal.influence.model.OSInfluenceChannel;
import com.onesignal.influence.model.OSInfluenceType;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

class OSNotificationTracker extends OSChannelTracker {

    public static final String TAG = OSNotificationTracker.class.getCanonicalName();
    private static final String DIRECT_TAG = "direct";
    private static final String NOTIFICATIONS_IDS = "notification_ids";
    private static final String NOTIFICATION_ID = "notification_id";

    OSNotificationTracker(@NonNull OSInfluenceDataRepository dataRepository, OSLogger logger) {
        super(dataRepository, logger);
    }

    @Override
    public String getIdTag() {
        return NOTIFICATION_ID;
    }

    @Override
    JSONArray getLastChannelObjectsReceivedByNewId(String id) {
        try {
            return getLastChannelObjects();
        } catch (JSONException exception) {
            logger.error("Generating Notification tracker getLastChannelObjects JSONObject ", exception);
            return new JSONArray();
        }
    }

    @Override
    JSONArray getLastChannelObjects() throws JSONException {
        return dataRepository.getLastNotificationsReceivedData();
    }

    @Override
    OSInfluenceChannel getChannelType() {
        return OSInfluenceChannel.NOTIFICATION;
    }

    @Override
    int getChannelLimit() {
        return dataRepository.getNotificationLimit();
    }

    @Override
    int getIndirectAttributionWindow() {
        return dataRepository.getNotificationIndirectAttributionWindow();
    }

    @Override
    void saveChannelObjects(JSONArray channelObjects) {
        dataRepository.saveNotifications(channelObjects);
    }

    @Override
    void initInfluencedTypeFromCache() {
        OSInfluenceType influenceType = dataRepository.getNotificationCachedInfluenceType();
        setInfluenceType(influenceType);

        if (influenceType.isIndirect())
            setIndirectIds(getLastReceivedIds());
        else if (influenceType.isDirect())
            setDirectId(dataRepository.getCachedNotificationOpenId());

        logger.debug("OneSignal NotificationTracker initInfluencedTypeFromCache: " + this.toString());
    }

    @Override
    void addSessionData(@NonNull JSONObject jsonObject, OSInfluence influence) {
        if (influence.getInfluenceType().isAttributed())
            try {
                jsonObject.put(DIRECT_TAG, influence.getInfluenceType().isDirect());
                jsonObject.put(NOTIFICATIONS_IDS, influence.getIds());
            } catch (JSONException exception) {
                logger.error("Generating notification tracker addSessionData JSONObject ", exception);
            }
    }

    @Override
    public void cacheState() {
        dataRepository.cacheNotificationInfluenceType(influenceType == null ? OSInfluenceType.UNATTRIBUTED : influenceType);
        dataRepository.cacheNotificationOpenId(directId);
    }

}
