package com.onesignal;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class OutcomeEvent {

    private static final String SESSION = "session";
    private static final String NOTIFICATION_IDS = "notification_ids";
    private static final String OUTCOME_ID = "id";
    private static final String TIMESTAMP = "timestamp";
    private static final String WEIGHT = "weight";

    private OSSessionManager.Session session;
    private JSONArray notificationIds;
    private String name;
    private long timestamp;
    private Float weight;

    public OutcomeEvent(@NonNull OSSessionManager.Session session, @Nullable JSONArray notificationIds, @NonNull String name, long timestamp, float weight) {
        this.session = session;
        this.notificationIds = notificationIds;
        this.name = name;
        this.timestamp = timestamp;
        this.weight = weight;
    }

    public OSSessionManager.Session getSession() {
        return session;
    }

    public JSONArray getNotificationIds() {
        return notificationIds;
    }

    public String getName() {
        return name;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public float getWeight() {
        return weight;
    }

    public JSONObject toJSONObject() {
        JSONObject json = new JSONObject();

        try {
            json.put(SESSION, session);
            json.put(NOTIFICATION_IDS, notificationIds);
            json.put(OUTCOME_ID, name);
            json.put(TIMESTAMP, timestamp);
            json.put(WEIGHT, weight);
        } catch (JSONException exception) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Generating OutcomeEvent toJSONObject ", exception);
        }

        return json;
    }

    JSONObject toJSONObjectForMeasure() {
        JSONObject json = new JSONObject();

        try {
            if (notificationIds != null && notificationIds.length() > 0)
                json.put(NOTIFICATION_IDS, notificationIds);

            json.put(OUTCOME_ID, name);

            if (weight > 0)
                json.put(WEIGHT, weight);

        } catch (JSONException exception) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Generating OutcomeEvent toJSONObject ", exception);
        }

        return json;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;

        if (o == null || this.getClass() != o.getClass())
            return false;

        OutcomeEvent event = (OutcomeEvent) o;
        return this.session.equals(event.session) &&
                this.notificationIds.equals(event.notificationIds) &&
                this.name.equals(event.name) &&
                this.timestamp == event.timestamp &&
                this.weight.equals(event.weight);
    }

    @Override
    public int hashCode() {
        Object[] a = new Object[]{session, notificationIds, name, timestamp, weight};

        int result = 1;

        for (Object element : a)
            result = 31 * result + (element == null ? 0 : element.hashCode());

        return result;
    }

    @Override
    public String toString() {
        return "OutcomeEvent{" +
                "session=" + session +
                ", notificationIds=" + notificationIds +
                ", name='" + name + '\'' +
                ", timestamp=" + timestamp +
                ", weight=" + weight +
                '}';
    }
}
