package com.onesignal.outcomes;

import com.onesignal.OSLogger;
import com.onesignal.OSSharedPreferences;
import com.onesignal.OneSignalDb;

public class MockOSOutcomeCache extends OSOutcomeEventsCache {

    MockOSOutcomeCache(OSLogger logger, OneSignalDb dbHelper, OSSharedPreferences preferences) {
        super(logger, dbHelper, preferences);
    }
}
