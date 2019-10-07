package com.onesignal;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

/**
 * Manager in charge to check what type of session is active
 * <p>
 * Types of sessions
 * - Direct: the session occurred due to a push
 * - Indirect: the session occurred on a time frame less than 24hrs
 * - Unattributed: the session was not influenced nor was on the time frame os a push
 */
public class OSSessionManager {

    private static final String DIRECT_TAG = "direct";
    private static final long HALF_MIN_IN_MILLIS = 30 * 1_000;
    private static final long MIN_IN_MILLIS = 60 * 1_000;

    public static class SessionResult {
        JSONArray notificationIds;
        Session session;

        SessionResult(SessionResult.Builder builder) {
            this.notificationIds = builder.notificationIds;
            this.session = builder.session;
        }

        boolean sessionHasNotifications() {
            return notificationIds != null && notificationIds.length() > 0;
        }

        public static class Builder {

            private JSONArray notificationIds;
            private Session session;

            public static SessionResult.Builder newInstance() {
                return new SessionResult.Builder();
            }

            private Builder() {
            }

            public SessionResult.Builder setNotificationIds(JSONArray notificationIds) {
                this.notificationIds = notificationIds;
                return this;
            }

            public SessionResult.Builder setSession(Session session) {
                this.session = session;
                return this;
            }

            public SessionResult build() {
                return new SessionResult(this);
            }
        }
    }

    interface SessionListener {
        void onSessionRestarted();
    }

    public enum Session {
        DIRECT,
        INDIRECT,
        UNATTRIBUTED,
        DISABLED,
        ;

        public static @NonNull
        Session fromString(String value) {
            if (value == null || value.isEmpty())
                return UNATTRIBUTED;

            for (Session type : Session.values()) {
                if (type.name().equalsIgnoreCase(value))
                    return type;
            }
            return UNATTRIBUTED;
        }
    }

    private Session session = null;
    private String notificationId = null;
    private JSONArray notificationIds = null;
    private Long sessionTime = null;
    private SessionListener sessionListener = null;

    public OSSessionManager() {
    }

    public OSSessionManager(SessionListener sessionListener) {
        this.sessionListener = sessionListener;
    }

    void addSessionNotificationsIds(JSONObject jsonObject) {
        if (session == null || session == Session.UNATTRIBUTED)
            return;
        try {
            if (notificationId != null && session == Session.DIRECT) {
                jsonObject.put(DIRECT_TAG, true);
                jsonObject.put(OutcomesUtils.NOTIFICATIONS_IDS, new JSONArray().put(notificationId));
            } else {
                if (notificationIds == null)
                    setLastNotificationsId();
                if (notificationIds.length() > 0 && session == Session.INDIRECT) {
                    jsonObject.put(DIRECT_TAG, false);
                    jsonObject.put(OutcomesUtils.NOTIFICATIONS_IDS, notificationIds);
                }
            }
        } catch (JSONException e) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Generating addNotificationId:JSON Failed.", e);
        }
    }

    void cleanSession() {
        this.session = null;
    }

    void restartSessionIfNeeded() {
        long resetTime = sessionTime != null ? System.currentTimeMillis() - sessionTime : HALF_MIN_IN_MILLIS;
        if (resetTime < HALF_MIN_IN_MILLIS)
            //avoid reset session if the session was recently being set
            return;
        OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "Session restarted");
        cleanSession();
        onSessionStarted();
        if (sessionListener != null)
            sessionListener.onSessionRestarted();
    }

    @NonNull
    Session getSession() {
        return session != null ? session : Session.UNATTRIBUTED;
    }

    @Nullable
    String getNotificationId() {
        return notificationId;
    }

    @Nullable
    JSONArray getNotificationIds() {
        return notificationIds;
    }

    /**
     * Set the type of active session
     */
    void onSessionStarted() {
        if (session != null)
            //session already set
            return;
        setLastNotificationsId();
        if (notificationIds.length() > 0) {
            session = Session.INDIRECT;
            OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "Session indirect with notificationId: " + notificationIds);
        } else {
            onSessionNotInfluenced();
        }
    }

    /**
     * Set active session type to {@link Session#UNATTRIBUTED}
     */
    void onSessionNotInfluenced() {
        session = Session.UNATTRIBUTED;
        OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "Session not influenced");
    }

    /**
     * Set active session type to {@link Session#DIRECT}
     */
    void onSessionFromNotification(String notificationId) {
        this.session = Session.DIRECT;
        this.sessionTime = System.currentTimeMillis();
        this.notificationId = notificationId;
        OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "Session Direct with notificationId: " + notificationId);
    }

    /**
     * Set the notifications ids that influenced the session
     */
    void setLastNotificationsId() {
        JSONArray notificationsReceived = OutcomesUtils.getLastNotificationsReceivedData();
        JSONArray notificationsIds = new JSONArray();

        long attributionWindow = OutcomesUtils.getIndirectAttributionWindow() * MIN_IN_MILLIS;
        long currentTime = new Date().getTime();
        for (int i = 0; i < notificationsReceived.length(); i++) {
            try {
                JSONObject jsonObject = notificationsReceived.getJSONObject(i);
                long time = jsonObject.getLong(OutcomesUtils.TIME);
                long difference = currentTime - time;

                if (difference <= attributionWindow) {
                    String notificationId = jsonObject.getString(OutcomesUtils.NOTIFICATION_ID);
                    notificationsIds.put(notificationId);
                }
            } catch (JSONException e) {
                OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "From getting notification from array:JSON Failed.", e);
            }
        }

        notificationIds = notificationsIds;
    }

    SessionResult getSessionResult() {
        if (session == OSSessionManager.Session.DIRECT && notificationId != null) {
            if (OutcomesUtils.isDirectSessionEnabled()) {
                JSONArray notificationIds = new JSONArray().put(notificationId);

                return SessionResult.Builder.newInstance()
                        .setNotificationIds(notificationIds)
                        .setSession(Session.DIRECT)
                        .build();
            }
        } else if (session == OSSessionManager.Session.INDIRECT && notificationIds != null) {
            if (OutcomesUtils.isIndirectSessionEnabled()) {
                return SessionResult.Builder.newInstance()
                        .setNotificationIds(notificationIds)
                        .setSession(Session.INDIRECT)
                        .build();
            }
        } else if (OutcomesUtils.isUnattributedSessionEnabled()) {
            return SessionResult.Builder.newInstance()
                    .setSession(Session.UNATTRIBUTED)
                    .build();
        }

        return SessionResult.Builder.newInstance()
                .setSession(Session.DISABLED)
                .build();
    }
}
