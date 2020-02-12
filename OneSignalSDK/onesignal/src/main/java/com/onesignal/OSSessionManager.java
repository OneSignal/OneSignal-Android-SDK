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

class OSSessionManager {

    private static final String DIRECT_TAG = "direct";

    public static class SessionResult {
        // Value will be Session.DISABLED only if the outcome type is disabled.
        @NonNull Session session;
        @Nullable JSONArray notificationIds;

        SessionResult(@NonNull SessionResult.Builder builder) {
            this.notificationIds = builder.notificationIds;
            this.session = builder.session;
        }

        public static class Builder {

            private JSONArray notificationIds;
            private Session session;

            public static SessionResult.Builder newInstance() {
                return new SessionResult.Builder();
            }

            private Builder() {
            }

            public SessionResult.Builder setNotificationIds(@Nullable JSONArray notificationIds) {
                this.notificationIds = notificationIds;
                return this;
            }

            public SessionResult.Builder setSession(@NonNull Session session) {
                this.session = session;
                return this;
            }

            public SessionResult build() {
                return new SessionResult(this);
            }
        }
    }

    interface SessionListener {
        // Fire with the SessionResult that just ended.
        void onSessionEnding(@NonNull SessionResult lastSessionResult);
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

        public boolean isAttributed() {
            return this.isDirect() || this.isIndirect();
        }

        public boolean isUnattributed() {
            return this.equals(UNATTRIBUTED);
        }

        public boolean isDisabled() {
            return this.equals(DISABLED);
        }

        public static @NonNull Session fromString(String value) {
            if (value == null || value.isEmpty())
                return UNATTRIBUTED;

            for (Session type : Session.values()) {
                if (type.name().equalsIgnoreCase(value))
                    return type;
            }
            return UNATTRIBUTED;
        }
    }

    @NonNull protected Session session;
    @Nullable private String directNotificationId;
    @Nullable private JSONArray indirectNotificationIds;
    @NonNull private SessionListener sessionListener;

    public OSSessionManager(@NonNull SessionListener sessionListener) {
        this.sessionListener = sessionListener;
        this.initSessionFromCache();
    }

    private void initSessionFromCache() {
        session = OutcomesUtils.getCachedSession();
        if (session.isIndirect())
            indirectNotificationIds = getLastNotificationsReceivedIds();
        else if (session.isDirect())
            directNotificationId = OutcomesUtils.getCachedNotificationOpenId();
    }

    void addSessionNotificationsIds(@NonNull JSONObject jsonObject) {
        if (session.isUnattributed())
            return;

        try {
            if (session.isDirect()) {
                jsonObject.put(DIRECT_TAG, true);
                jsonObject.put(OutcomesUtils.NOTIFICATIONS_IDS, new JSONArray().put(directNotificationId));
            }
            else if (session.isIndirect()) {
                jsonObject.put(DIRECT_TAG, false);
                jsonObject.put(OutcomesUtils.NOTIFICATIONS_IDS, indirectNotificationIds);
            }
        } catch (JSONException e) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Generating addNotificationId:JSON Failed.", e);
        }
    }

    void restartSessionIfNeeded() {
        // Avoid reset session if app was focused due to a notification click (direct session recently set)
        if (OneSignal.getAppEntryState().isNotificationClick())
            return;

        JSONArray lastNotifications = getLastNotificationsReceivedIds();
        if (lastNotifications.length() > 0)
            setSession(Session.INDIRECT, null, lastNotifications);
        else
            setSession(Session.UNATTRIBUTED, null, null);
    }

    @NonNull Session getSession() {
        return session;
    }

    @Nullable String getDirectNotificationId() {
        return directNotificationId;
    }

    @Nullable JSONArray getIndirectNotificationIds() {
        return indirectNotificationIds;
    }

    void onDirectSessionFromNotificationOpen(@NonNull String notificationId) {
        setSession(Session.DIRECT, notificationId, null);
    }

    // Call when the session for the app changes, caches the state, and broadcasts the session that just ended
    private void setSession(@NonNull Session session, @Nullable String directNotificationId, @Nullable JSONArray indirectNotificationIds) {
        if (!willChangeSession(session, directNotificationId, indirectNotificationIds))
            return;

        OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG,
           "OSSession changed" +
              "\nfrom:\n" +
              "session: " + this.session +
              ", directNotificationId: " + this.directNotificationId +
              ", indirectNotificationIds: " + this.indirectNotificationIds +
              "\nto:\n" +
              "session: " + session +
              ", directNotificationId: " + directNotificationId +
              ", indirectNotificationIds: " + indirectNotificationIds);

        OutcomesUtils.cacheCurrentSession(session);
        OutcomesUtils.cacheNotificationOpenId(directNotificationId);

        // Broadcast the session that just ended before finalizing the state change
        sessionListener.onSessionEnding(getSessionResult());

        this.session = session;
        this.directNotificationId = directNotificationId;
        this.indirectNotificationIds = indirectNotificationIds;
    }

    private boolean willChangeSession(@NonNull Session session, @Nullable String directNotificationId, @Nullable JSONArray indirectNotificationIds) {
        if (!session.equals(this.session))
            return true;

        // Allow updating a direct session to a new direct when a new notification is clicked
        if (this.session.isDirect() &&
                this.directNotificationId != null &&
                !this.directNotificationId.equals(directNotificationId)) {
            return true;
        }

        // Allow updating an indirect session to a new indirect when a new notification is received
        if (this.session.isIndirect() &&
           this.indirectNotificationIds != null &&
           this.indirectNotificationIds.length() > 0 &&
           !JSONUtils.compareJSONArrays(this.indirectNotificationIds, indirectNotificationIds)) {
            return true;
        }

        return false;
    }

    /**
     * Set the notifications ids that influenced the session
     */
    @NonNull protected JSONArray getLastNotificationsReceivedIds() {
        JSONArray notificationsReceived = OutcomesUtils.getLastNotificationsReceivedData();
        JSONArray notificationsIds = new JSONArray();

        long attributionWindow = OutcomesUtils.getIndirectAttributionWindow() * 60 * 1_000L;
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

        return notificationsIds;
    }

    // Get the current session based on state + if outcomes features are enabled.
    @NonNull SessionResult getSessionResult() {
        if (session.isDirect()) {
            if (OutcomesUtils.isDirectSessionEnabled()) {
                JSONArray directNotificationIds = new JSONArray().put(directNotificationId);
                return SessionResult.Builder.newInstance()
                        .setNotificationIds(directNotificationIds)
                        .setSession(Session.DIRECT)
                        .build();
            }
        } else if (session.isIndirect()) {
            if (OutcomesUtils.isIndirectSessionEnabled()) {
                return SessionResult.Builder.newInstance()
                        .setNotificationIds(indirectNotificationIds)
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

    @NonNull SessionResult getIAMSessionResult() {
        if (OutcomesUtils.isUnattributedSessionEnabled()) {
            return SessionResult.Builder.newInstance()
                    .setSession(Session.UNATTRIBUTED)
                    .build();
        }

        return SessionResult.Builder.newInstance()
                .setSession(Session.DISABLED)
                .build();
    }

    /**
     * Attempt to override the current session before the 30 second session minimum
     * This should only be done in a upward direction:
     *   * UNATTRIBUTED can become INDIRECT or DIRECT
     *   * INDIRECT can become DIRECT
     *   * DIRECT can become DIRECT
     */
    void attemptSessionUpgrade() {
        // We will try to override any session with DIRECT
        if (OneSignal.getAppEntryState().isNotificationClick()) {
            setSession(Session.DIRECT, directNotificationId, null);
            return;
        }

        // We will try to override the UNATTRIBUTED session with INDIRECT
        if (session.isUnattributed()) {
            JSONArray lastNotificationIds = getLastNotificationsReceivedIds();
            if (lastNotificationIds.length() > 0 && OneSignal.getAppEntryState().isAppOpen())
                setSession(Session.INDIRECT, null, lastNotificationIds);
        }
    }
}
