package com.onesignal;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.onesignal.outcomes.OSOutcomeEventsFactory;

public class MockOutcomeEventsController extends OSOutcomeEventsController {

    public MockOutcomeEventsController(MockSessionManager sessionManager, OSOutcomeEventsFactory factory) {
        super(sessionManager, factory);
    }

    @Override
    public void cleanOutcomes() {
        super.cleanOutcomes();
    }

    @Override
    public void sendSavedOutcomes() {
        super.sendSavedOutcomes();
    }

    public void sendOutcomeEvent(@NonNull String name) {
        sendOutcomeEvent(name, null);
    }

    @Override
    public void sendOutcomeEvent(@NonNull String name, @Nullable OneSignal.OutcomeCallback callback) {
        super.sendOutcomeEvent(name, callback);
    }

    public void sendUniqueOutcomeEvent(@NonNull String name) {
        sendUniqueOutcomeEvent(name, null);
    }

    @Override
    public void sendUniqueOutcomeEvent(@NonNull String name, @Nullable OneSignal.OutcomeCallback callback) {
        super.sendUniqueOutcomeEvent(name, callback);
    }

    public void sendOutcomeEventWithValue(@NonNull String name, float value) {
        sendOutcomeEventWithValue(name, value, null);
    }

    @Override
    public void sendOutcomeEventWithValue(@NonNull String name, float value, @Nullable OneSignal.OutcomeCallback callback) {
        super.sendOutcomeEventWithValue(name, value, callback);
    }

}
