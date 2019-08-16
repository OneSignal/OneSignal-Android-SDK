package com.onesignal;

import android.support.annotation.NonNull;

public class MockOutcomeEventsController extends OutcomeEventsController {

    public MockOutcomeEventsController(OSSessionManager osSessionManager, OutcomeEventsRepository outcomeEventsRepository) {
        super(osSessionManager, outcomeEventsRepository);
    }

    @Override
    public void sendSavedOutcomes() {
        super.sendSavedOutcomes();
    }

    @Override
    public void sendOutcomeEvent(@NonNull String name) {
        super.sendOutcomeEvent(name);
    }
}
