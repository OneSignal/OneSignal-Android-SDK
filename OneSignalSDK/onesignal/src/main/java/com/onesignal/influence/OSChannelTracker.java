package com.onesignal.influence;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.onesignal.OSLogger;
import com.onesignal.influence.model.OSInfluence;
import com.onesignal.influence.model.OSInfluenceChannel;
import com.onesignal.influence.model.OSInfluenceType;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

abstract public class OSChannelTracker {

    private static final String TIME = "time";

    protected OSLogger logger;
    @NonNull
    OSInfluenceDataRepository dataRepository;
    @Nullable
    OSInfluenceType influenceType;
    @Nullable
    JSONArray indirectIds;
    @Nullable
    String directId;

    OSChannelTracker(@NonNull OSInfluenceDataRepository dataRepository, OSLogger logger) {
        this.dataRepository = dataRepository;
        this.logger = logger;
    }

    public abstract String getIdTag();

    abstract OSInfluenceChannel getChannelType();

    abstract JSONArray getLastChannelObjectsReceivedByNewId(String id);

    abstract JSONArray getLastChannelObjects() throws JSONException;

    abstract int getChannelLimit();

    abstract int getIndirectAttributionWindow();

    abstract void saveChannelObjects(JSONArray channelObjects);

    abstract void initInfluencedTypeFromCache();

    abstract void addSessionData(@NonNull JSONObject jsonObject, OSInfluence influence);

    public abstract void cacheState();

    public void resetAndInitInfluence() {
        directId = null;
        indirectIds = getLastReceivedIds();
        influenceType = indirectIds.length() > 0 ? OSInfluenceType.INDIRECT : OSInfluenceType.UNATTRIBUTED;

        cacheState();
        logger.debug("OneSignal OSChannelTracker resetAndInitInfluence: " + getIdTag() + " finish with influenceType: " + influenceType);
    }

    /**
     * Get all received ids that may influence actions
     *
     * @return ids that happen between attribution window
     */
    public JSONArray getLastReceivedIds() {
        JSONArray ids = new JSONArray();
        try {
            JSONArray lastChannelObjectReceived = getLastChannelObjects();
            logger.debug("OneSignal ChannelTracker getLastReceivedIds lastChannelObjectReceived: " + lastChannelObjectReceived);

            long attributionWindow = getIndirectAttributionWindow() * 60 * 1_000L;
            long currentTime = System.currentTimeMillis();
            for (int i = 0; i < lastChannelObjectReceived.length(); i++) {
                JSONObject jsonObject = lastChannelObjectReceived.getJSONObject(i);
                long time = jsonObject.getLong(TIME);
                long difference = currentTime - time;

                if (difference <= attributionWindow) {
                    String id = jsonObject.getString(getIdTag());
                    ids.put(id);
                }
            }
        } catch (JSONException exception) {
            logger.error("Generating tracker getLastReceivedIds JSONObject ", exception);
        }

        return ids;
    }

    /**
     * Save state of last ids received
     */
    public void saveLastId(String id) {
        logger.debug("OneSignal OSChannelTracker for: " + getIdTag() + " saveLastId: " + id);
        if (id == null || id.isEmpty())
            return;

        JSONArray lastChannelObjectsReceived = getLastChannelObjectsReceivedByNewId(id);
        logger.debug("OneSignal OSChannelTracker for: " + getIdTag() + " saveLastId with lastChannelObjectsReceived: " + lastChannelObjectsReceived);

        try {
            JSONObject newInfluenceId = new JSONObject()
                    .put(getIdTag(), id)
                    .put(TIME, System.currentTimeMillis());
            lastChannelObjectsReceived.put(newInfluenceId);
        } catch (JSONException exception) {
            logger.error("Generating tracker newInfluenceId JSONObject ", exception);
            // We don't have new data, stop logic
            return;
        }

        int channelLimit = getChannelLimit();
        JSONArray channelObjectToSave = lastChannelObjectsReceived;

        // Only save the last ids without surpassing the limit
        // Always keep the max quantity of ids possible
        // If the attribution window increases, old ids might influence
        if (lastChannelObjectsReceived.length() > channelLimit) {
            int lengthDifference = lastChannelObjectsReceived.length() - channelLimit;
            // If min sdk is greater than KITKAT we can refactor this logic to removeObject from JSONArray
            channelObjectToSave = new JSONArray();
            for (int i = lengthDifference; i < lastChannelObjectsReceived.length(); i++) {
                try {
                    channelObjectToSave.put(lastChannelObjectsReceived.get(i));
                } catch (JSONException exception) {
                    logger.error("Before KITKAT API, Generating tracker lastChannelObjectsReceived get JSONObject ", exception);
                }
            }
        }

        logger.debug("OneSignal OSChannelTracker for: " + getIdTag() + " with channelObjectToSave: " + channelObjectToSave);
        saveChannelObjects(channelObjectToSave);
    }

    /**
     * Get the current session based on state + if outcomes features are enabled.
     */
    @NonNull
    public OSInfluence getCurrentSessionInfluence() {
        OSInfluence.Builder sessionInfluenceBuilder =
                OSInfluence.Builder.newInstance().setInfluenceType(OSInfluenceType.DISABLED);

        // Channel weren't init yet because application is starting
        if (influenceType == null)
            initInfluencedTypeFromCache();

        if (influenceType.isDirect()) {
            if (isDirectSessionEnabled()) {
                JSONArray directIds = new JSONArray().put(directId);
                sessionInfluenceBuilder = OSInfluence.Builder.newInstance()
                        .setIds(directIds)
                        .setInfluenceType(OSInfluenceType.DIRECT);
            }
        } else if (influenceType.isIndirect()) {
            if (isIndirectSessionEnabled()) {
                sessionInfluenceBuilder = OSInfluence.Builder.newInstance()
                        .setIds(indirectIds)
                        .setInfluenceType(OSInfluenceType.INDIRECT);
            }
        } else if (isUnattributedSessionEnabled()) {
            sessionInfluenceBuilder = OSInfluence.Builder.newInstance()
                    .setInfluenceType(OSInfluenceType.UNATTRIBUTED);
        }

        return sessionInfluenceBuilder
                .setInfluenceChannel(getChannelType())
                .build();
    }

    private boolean isDirectSessionEnabled() {
        return dataRepository.isDirectInfluenceEnabled();
    }

    private boolean isIndirectSessionEnabled() {
        return dataRepository.isIndirectInfluenceEnabled();
    }

    private boolean isUnattributedSessionEnabled() {
        return dataRepository.isUnattributedInfluenceEnabled();
    }

    @Nullable
    public OSInfluenceType getInfluenceType() {
        return influenceType;
    }

    public void setInfluenceType(@NonNull OSInfluenceType influenceType) {
        this.influenceType = influenceType;
    }

    @Nullable
    public JSONArray getIndirectIds() {
        return indirectIds;
    }

    public void setIndirectIds(@Nullable JSONArray indirectIds) {
        this.indirectIds = indirectIds;
    }

    @Nullable
    public String getDirectId() {
        return directId;
    }

    public void setDirectId(@Nullable String directId) {
        this.directId = directId;
    }

    @Override
    public String toString() {
        return "OSChannelTracker{" +
                "tag=" + getIdTag() +
                ", influenceType=" + influenceType +
                ", indirectIds=" + indirectIds +
                ", directId='" + directId + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OSChannelTracker tracker = (OSChannelTracker) o;
        return influenceType == tracker.influenceType && tracker.getIdTag().equals(getIdTag());
    }

    @Override
    public int hashCode() {
        int result = influenceType.hashCode();
        result = 31 * result + getIdTag().hashCode();
        return result;
    }
}
