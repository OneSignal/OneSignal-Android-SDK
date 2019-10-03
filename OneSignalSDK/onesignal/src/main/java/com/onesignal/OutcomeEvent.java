package com.onesignal;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class OutcomeEvent {

    private static final String NOTIFICATION_IDs = "notification_ids";
    private static final String OUTCOME_ID = "id";
    private static final String TIMESTAMP = "timestamp";

    private OSSessionManager.Session session;
    private OutcomeParams params;
    private JSONArray notificationIds;
    private String name;
    private long timestamp;

    public OutcomeEvent(@NonNull OSSessionManager.Session session, @Nullable JSONArray notificationIds, @NonNull String name, long timestamp, @Nullable OutcomeParams params) {
        this.session = session;
        this.notificationIds = notificationIds;
        this.name = name;
        this.timestamp = timestamp;
        this.params = params;
    }

    public OSSessionManager.Session getSession() {
        return session;
    }

    public void setSession(OSSessionManager.Session session) {
        this.session = session;
    }

    public JSONArray getNotificationIds() {
        return notificationIds;
    }

    public void setNotificationIds(JSONArray notificationIds) {
        this.notificationIds = notificationIds;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getParams() {
        return params != null ? params.getAsJSONString() : null;
    }

    public JSONObject toJSONObject() {
        JSONObject json = new JSONObject();

        try {
            json.put(OUTCOME_ID, name);
            json.put(TIMESTAMP, timestamp);

            if (params != null)
                params.addParamsToJson(json);
        } catch (JSONException exception) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Generating OutcomeEvent toJSONObject ", exception);
        }

        return json;
    }

    JSONObject toJSONObjectWithNotification() {
        JSONObject json = toJSONObject();

        try {
            json.put(NOTIFICATION_IDs, notificationIds);
        } catch (JSONException exception) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Generating OutcomeEvent toJSONObject ", exception);
        }

        return json;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OutcomeEvent event = (OutcomeEvent) o;
        return timestamp == event.timestamp &&
                session == event.session &&
                notificationIds.equals(event.notificationIds) &&
                name.equals(event.name);
    }

    @Override
    public int hashCode() {
        Object[] a = new Object[]{session, notificationIds, name, timestamp};

        int result = 1;

        for (Object element : a)
            result = 31 * result + (element == null ? 0 : element.hashCode());

        return result;
    }

    @Override
    public String toString() {
        return "OutcomeEvent{" +
                "session=" + session +
                ", params=" + params +
                ", notificationIds=" + notificationIds +
                ", name='" + name + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
