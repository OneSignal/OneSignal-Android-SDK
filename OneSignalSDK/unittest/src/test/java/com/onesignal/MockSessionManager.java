package com.onesignal;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONArray;

public class MockSessionManager extends OSSessionManager {

    private SessionResult sessionResult;

    public void resetMock() {
        sessionResult = null;
        cleanSession();
    }

    public void setSessionResult(SessionResult sessionResult) {
        this.sessionResult = sessionResult;
    }

    @Override
    public void cleanSession() {
        super.cleanSession();
    }

    @Override
    public void onDirectSessionFromNotificationOpen(String notificationId) {
        super.onDirectSessionFromNotificationOpen(notificationId);
    }

    @Override
    public void onSessionStarted() {
        super.onSessionStarted();
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

    @Override
    SessionResult getSessionResult() {
        if (sessionResult != null)
            return sessionResult;
        return super.getSessionResult();
    }

}