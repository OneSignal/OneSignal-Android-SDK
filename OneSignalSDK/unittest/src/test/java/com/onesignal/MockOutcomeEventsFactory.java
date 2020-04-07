package com.onesignal;

import com.onesignal.outcomes.OSOutcomeEventsFactory;

public class MockOutcomeEventsFactory extends OSOutcomeEventsFactory {

    public MockOutcomeEventsFactory(OSLogger logger, OneSignalAPIClient apiClient, OneSignalDb dbHelper, OSSharedPreferences preferences) {
        super(logger, apiClient, dbHelper, preferences);
    }
}
