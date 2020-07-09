package com.onesignal.influence.model;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class OSInfluence {

    private static final String INFLUENCE_CHANNEL = "influence_channel";
    private static final String INFLUENCE_TYPE = "influence_type";
    private static final String INFLUENCE_IDS = "influence_ids";

    private OSInfluenceChannel influenceChannel;
    /**
     * InfluenceType will be DISABLED only if the outcome feature is disabled.
     */
    private OSInfluenceType influenceType;
    @Nullable
    private JSONArray ids;

    private OSInfluence() {
    }

    public OSInfluence(@NonNull String jsonString) throws JSONException {
        JSONObject jsonObject = new JSONObject(jsonString);
        String channel = jsonObject.getString(INFLUENCE_CHANNEL);
        String type = jsonObject.getString(INFLUENCE_TYPE);
        String ids = jsonObject.getString(INFLUENCE_IDS);
        this.influenceChannel = OSInfluenceChannel.fromString(channel);
        this.influenceType = OSInfluenceType.fromString(type);
        this.ids = ids.isEmpty() ? null : new JSONArray(ids);
    }

    OSInfluence(@NonNull OSInfluence.Builder builder) {
        this.ids = builder.ids;
        this.influenceType = builder.influenceType;
        this.influenceChannel = builder.influenceChannel;
    }

    public OSInfluenceChannel getInfluenceChannel() {
        return influenceChannel;
    }

    @NonNull
    public OSInfluenceType getInfluenceType() {
        return influenceType;
    }

    @Nullable
    public JSONArray getIds() {
        return ids;
    }

    @Nullable
    public String getDirectId() throws JSONException {
        return ids != null && ids.length() > 0 ? ids.getString(0) : null;
    }

    public void setIds(@NonNull JSONArray ids) {
        this.ids = ids;
    }

    public static class Builder {

        private JSONArray ids;
        private OSInfluenceType influenceType;
        private OSInfluenceChannel influenceChannel;

        public static OSInfluence.Builder newInstance() {
            return new OSInfluence.Builder();
        }

        private Builder() {
        }

        public OSInfluence.Builder setIds(@Nullable JSONArray ids) {
            this.ids = ids;
            return this;
        }

        public OSInfluence.Builder setInfluenceType(@NonNull OSInfluenceType influenceType) {
            this.influenceType = influenceType;
            return this;
        }

        public OSInfluence.Builder setInfluenceChannel(OSInfluenceChannel influenceChannel) {
            this.influenceChannel = influenceChannel;
            return this;
        }

        public OSInfluence build() {
            return new OSInfluence(this);
        }
    }

    public OSInfluence copy() {
        OSInfluence influence = new OSInfluence();
        influence.ids = this.ids;
        influence.influenceType = this.influenceType;
        influence.influenceChannel = this.influenceChannel;
        return influence;
    }

    public String toJSONString() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(INFLUENCE_CHANNEL, influenceChannel.toString());
        jsonObject.put(INFLUENCE_TYPE, influenceType.toString());
        jsonObject.put(INFLUENCE_IDS, ids != null ? ids.toString() : "");
        return jsonObject.toString();
    }

    @Override
    public String toString() {
        return "SessionInfluence{" +
                "influenceChannel=" + influenceChannel +
                ", influenceType=" + influenceType +
                ", ids=" + ids +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OSInfluence that = (OSInfluence) o;
        return influenceChannel == that.influenceChannel &&
                influenceType == that.influenceType;
    }

    @Override
    public int hashCode() {
        int result = influenceChannel.hashCode();
        result = 31 * result + influenceType.hashCode();
        return result;
    }
}
