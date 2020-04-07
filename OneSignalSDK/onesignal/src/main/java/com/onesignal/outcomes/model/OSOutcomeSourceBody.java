package com.onesignal.outcomes.model;

import android.support.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class OSOutcomeSourceBody {

    private static final String NOTIFICATION_IDS = "notification_ids";
    private static final String IAM_IDS = "in_app_message_ids";

    @Nullable
    private JSONArray notificationIds;
    @Nullable
    private JSONArray inAppMessagesIds;

    public OSOutcomeSourceBody() {
        this(new JSONArray(), new JSONArray());
    }

    public OSOutcomeSourceBody(@Nullable JSONArray notificationIds, @Nullable JSONArray inAppMessagesIds) {
        this.notificationIds = notificationIds;
        this.inAppMessagesIds = inAppMessagesIds;
    }

    public JSONObject toJSONObject() throws JSONException {
        JSONObject json = new JSONObject();
        json.put(NOTIFICATION_IDS, notificationIds);
        json.put(IAM_IDS, inAppMessagesIds);
        return json;
    }

    @Nullable
    public JSONArray getNotificationIds() {
        return notificationIds;
    }

    public void setNotificationIds(@Nullable JSONArray notificationIds) {
        this.notificationIds = notificationIds;
    }

    @Nullable
    public JSONArray getInAppMessagesIds() {
        return inAppMessagesIds;
    }

    public void setInAppMessagesIds(@Nullable JSONArray inAppMessagesIds) {
        this.inAppMessagesIds = inAppMessagesIds;
    }

    @Override
    public String toString() {
        return "OSOutcomeSourceBody{" +
                "notificationIds=" + notificationIds +
                ", inAppMessagesIds=" + inAppMessagesIds +
                '}';
    }
}
