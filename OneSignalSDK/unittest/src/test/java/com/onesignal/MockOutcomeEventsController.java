package com.onesignal;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public class MockOutcomeEventsController extends OutcomeEventsController {

    public MockOutcomeEventsController(OSSessionManager osSessionManager, OutcomeEventsRepository outcomeEventsRepository) {
        super(osSessionManager, outcomeEventsRepository);
    }

    @Override
    public void clearOutcomes() {
        super.clearOutcomes();
    }

    @Override
    public void sendSavedOutcomes() {
        super.sendSavedOutcomes();
    }

    public void sendOutcomeEvent(@NonNull String name, float value) {
        super.sendOutcomeEvent(name, value, null);
    }

    @Override
    public void sendOutcomeEvent(@NonNull String name, float value, @Nullable OneSignal.OutcomeCallback callback) {
        super.sendOutcomeEvent(name, value, callback);
    }

    @Override
    public void sendOutcomeEvent(@NonNull String name, @NonNull String value) throws OutcomeException {
        super.sendOutcomeEvent(name, value);
    }

    @Override
    public void sendOutcomeEvent(@NonNull String name, @NonNull Bundle params) {
        super.sendOutcomeEvent(name, params);
    }

    public void sendOutcomeEvent(@NonNull String name) {
        super.sendOutcomeEvent(name, (OneSignal.OutcomeCallback) null);
    }

    @Override
    public void sendOutcomeEvent(@NonNull String name, @Nullable OneSignal.OutcomeCallback callback) {
        super.sendOutcomeEvent(name, callback);
    }

    public void sendUniqueOutcomeEvent(@NonNull String name) {
        super.sendUniqueOutcomeEvent(name, null);
    }

    @Override
    public void sendUniqueOutcomeEvent(@NonNull String name, @Nullable OneSignal.OutcomeCallback callback) {
        super.sendUniqueOutcomeEvent(name, callback);
    }

    @Override
    public void setOutcomeSettings(@Nullable OneSignal.OutcomeSettings outcomeSettings) {
        super.setOutcomeSettings(outcomeSettings);
    }
}
