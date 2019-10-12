package com.onesignal;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONArray;

public class MockSessionManager extends OSSessionManager {

    private SessionResult sessionResult;

    public MockSessionManager() {
        super(new SessionListener() {
            @Override
            public void onSessionEnding(SessionResult lastSessionResult) {
            }
        });
    }

    public void resetMock() {
        session = Session.UNATTRIBUTED;
        sessionResult = null;
        cleanSession();
    }

    public void setSessionResult(SessionResult sessionResult) {
        this.sessionResult = sessionResult;
    }

    public void cleanSession() {
        // TODO: Remove, this isn't in production code any more
//        session = null;
    }

    @Override
    public void onDirectSessionFromNotificationOpen(String notificationId) {
        super.onDirectSessionFromNotificationOpen(notificationId);
    }

    public void startSession() {
        super.restartSessionIfNeeded();
        // TODO: is restartSessionIfNeeded the same?
//        super.startSession();
    }

    @NonNull
    @Override
    public Session getSession() {
        return super.getSession();
    }

    @Nullable
    @Override
    public String getDirectNotificationId() {
        return super.getDirectNotificationId();
    }

    @Nullable
    @Override
    public JSONArray getIndirectNotificationIds() {
        return super.getIndirectNotificationIds();
    }

    @NonNull
    public JSONArray getLastNotificationsReceivedIds() {
        return super.getLastNotificationsReceivedIds();
    }

    @Override
    SessionResult getSessionResult() {
        if (sessionResult != null)
            return sessionResult;
        return super.getSessionResult();
    }

}