package com.onesignal;

import org.json.JSONException;
import org.json.JSONObject;

public class OutcomeEvent {

    private static final String NOTIFICATION_ID = "notification_id";
    private static final String NAME = "name";
    private static final String SESSION = "session";
    private static final String TIMESTAMP = "timestamp";

    private OSSessionManager.Session session;
    private String notificationId;
    private String name;
    private long timestamp;

    OutcomeEvent(OSSessionManager.Session session, String name, long timestamp) {
        this(session, null, name, timestamp);
    }

    public OutcomeEvent(OSSessionManager.Session session, String notificationId, String name, long timestamp) {
        this.session = session;
        this.notificationId = notificationId;
        this.name = name;
        this.timestamp = timestamp;
    }

    OutcomeEvent(JSONObject json) {
        String session = json.optString(SESSION);

        this.session = session != null && !session.isEmpty() ? OSSessionManager.Session.valueOf(session.toUpperCase()) : OSSessionManager.Session.UNATTRIBUTED;
        this.notificationId = json.optString(NOTIFICATION_ID, null);
        this.name = json.optString(NAME);
        this.timestamp = json.optLong(TIMESTAMP);
    }

    public OSSessionManager.Session getSession() {
        return session;
    }

    public void setSession(OSSessionManager.Session session) {
        this.session = session;
    }

    public String getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(String notificationId) {
        this.notificationId = notificationId;
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

    public JSONObject toJSONObject() {
        JSONObject json = new JSONObject();

        try {
            json.put(NOTIFICATION_ID, this.notificationId);
            json.put(NAME, this.name);
            json.put(SESSION, this.session.toString().toLowerCase());
            json.put(TIMESTAMP, this.timestamp);
        } catch (JSONException exception) {
            exception.printStackTrace();
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
                notificationId.equals(event.notificationId) &&
                name.equals(event.name);
    }

    @Override
    public int hashCode() {
        Object[] a = new Object[]{session, notificationId, name, timestamp};

        int result = 1;

        for (Object element : a)
            result = 31 * result + (element == null ? 0 : element.hashCode());

        return result;
    }
}
