package com.onesignal;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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

        public boolean isDirect() {
            return this.equals(DIRECT);
        }

        public boolean isIndirect() {
            return this.equals(INDIRECT);
        }

        public boolean isUnattributed() {
            return this.equals(UNATTRIBUTED);
        }

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
    private String directNotificationId = null;
    private JSONArray indirectNotificationIds = null;
    private SessionListener sessionListener = null;

    public OSSessionManager() {
    }

    public OSSessionManager(SessionListener sessionListener) {
        this.sessionListener = sessionListener;
    }

    void addSessionNotificationsIds(JSONObject jsonObject) {
        if (session == null || session.isUnattributed())
            return;

        try {
            if (session.isDirect() && hasDirectNotification()) {
                jsonObject.put(DIRECT_TAG, true);
                jsonObject.put(OutcomesUtils.NOTIFICATIONS_IDS, new JSONArray().put(directNotificationId));
            } else {
                if (indirectNotificationIds == null)
                    setLastNotificationsId();
                if (session.isIndirect() && hasIndirectNotifications()) {
                    jsonObject.put(DIRECT_TAG, false);
                    jsonObject.put(OutcomesUtils.NOTIFICATIONS_IDS, indirectNotificationIds);
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
        if (OneSignal.appEntryState.isNotificationClick())
            // Avoid reset session if coming from a notification click (direct session recently set)
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
    String getDirectNotificationId() {
        return directNotificationId;
    }

    @Nullable
    JSONArray getIndirectNotificationIds() {
        return indirectNotificationIds;
    }

    /**
     * Set the type of active session
     */
    void onSessionStarted() {
        if (session != null)
            //session already set
            return;
        setLastNotificationsId();
        if (hasIndirectNotifications()) {
            setSession(Session.INDIRECT);
            OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "Session indirect with directNotificationId: " + indirectNotificationIds);
        } else {
            onSessionNotInfluenced();
        }
    }

    /**
     * Set active session type to {@link Session#UNATTRIBUTED}
     */
    void onSessionNotInfluenced() {
        setSession(Session.UNATTRIBUTED);
        OutcomesUtils.cacheCurrentSession(getSession());
        OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "Session not influenced");
    }

    /**
     * Set active session type to {@link Session#DIRECT}
     */
    void onSessionFromNotification(String notificationId) {
        setSession(Session.DIRECT);
        this.directNotificationId = notificationId;
        OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "Session Direct with directNotificationId: " + notificationId);
    }

    public void setSession(Session session) {
        this.session = session;
        OutcomesUtils.cacheCurrentSession(getSession());
    }

    /**
     * Set the notifications ids that influenced the session
     */
    void setLastNotificationsId() {
        JSONArray notificationsReceived = OutcomesUtils.getLastNotificationsReceivedData();
        JSONArray notificationsIds = new JSONArray();

        long attributionWindow = OutcomesUtils.getIndirectAttributionWindow() * 60L * 1_000L;
        long currentTime = System.currentTimeMillis();
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

        indirectNotificationIds = notificationsIds;
    }

    SessionResult getSessionResult() {
        if (session.isDirect() && hasDirectNotification()) {
            // Make sure direct flag is true
            if (OutcomesUtils.isDirectSessionEnabled()) {
                JSONArray directNotificationIds = new JSONArray().put(directNotificationId);
                return SessionResult.Builder.newInstance()
                        .setNotificationIds(directNotificationIds)
                        .setSession(Session.DIRECT)
                        .build();
            }
        } else if (session.isIndirect() && hasIndirectNotifications()) {
            // Make sure indirect flag is true
            if (OutcomesUtils.isIndirectSessionEnabled()) {
                return SessionResult.Builder.newInstance()
                        .setNotificationIds(indirectNotificationIds)
                        .setSession(Session.INDIRECT)
                        .build();
            }
        } else if (OutcomesUtils.isUnattributedSessionEnabled()) {
            // Make sure unattributed flag is true
            return SessionResult.Builder.newInstance()
                    .setSession(Session.UNATTRIBUTED)
                    .build();
        }

        // Outcomes is disabled if it gets here
        return SessionResult.Builder.newInstance()
                .setSession(Session.DISABLED)
                .build();
    }

    /**
     * If indirectNotificationIds exist and have at least 1 notification id, this would imply a
     * app open with notifications within the attribution window time
     */
    boolean hasIndirectNotifications() {
        return getIndirectNotificationIds() != null
                && getIndirectNotificationIds().length() > 0;
    }

    /**
     * With a directNotificationId set this would imply that the notification opened logic occurred
     * to set a DIRECT session
     */
    boolean hasDirectNotification() {
        return directNotificationId != null
                && !directNotificationId.isEmpty();
    }

    /**
     * Attempt to override the current session before the 30 second session minimum
     * This should only be done in a upward direction, so UNATTRIBUTED can become INDIRECT or DIRECT
     * And INDIRECT can become DIRECT
     */
    void attemptSessionOverride() {
        // Get cached current session state
        Session currentSession = OutcomesUtils.getCachedSession();
        if (currentSession.isUnattributed()) {
            // We will try to override the UNATTRIBUTED session with INDIRECT
            setLastNotificationsId();
            if (hasIndirectNotifications() && OneSignal.appEntryState.isAppOpen())
                setSession(Session.INDIRECT);
        }

        // Try to override the current session with a DIRECT session
        if (hasDirectNotification() && OneSignal.appEntryState.isNotificationClick())
            setSession(Session.DIRECT);
    }
}
