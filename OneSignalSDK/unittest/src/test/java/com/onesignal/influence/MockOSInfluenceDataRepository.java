package com.onesignal.influence;

import com.onesignal.OSSharedPreferences;

public class MockOSInfluenceDataRepository extends OSInfluenceDataRepository {

    // OUTCOMES KEYS
    // Outcomes Influence enable params
    public static final String PREFS_OS_UNATTRIBUTED_ENABLED = OSInfluenceDataRepository.PREFS_OS_UNATTRIBUTED_ENABLED;

    public MockOSInfluenceDataRepository(OSSharedPreferences preferences) {
        super(preferences);
    }
}
