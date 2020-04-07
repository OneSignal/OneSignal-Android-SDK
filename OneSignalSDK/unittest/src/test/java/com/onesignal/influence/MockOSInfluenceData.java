package com.onesignal.influence;

import com.onesignal.OSSharedPreferences;

public class MockOSInfluenceData extends OSInfluenceDataRepository {

    // OUTCOMES KEYS
    // Outcomes Influence Ids
    public static final String PREFS_OS_LAST_ATTRIBUTED_NOTIFICATION_OPEN = OSInfluenceDataRepository.PREFS_OS_LAST_ATTRIBUTED_NOTIFICATION_OPEN;
    public static final String PREFS_OS_LAST_NOTIFICATIONS_RECEIVED = OSInfluenceDataRepository.PREFS_OS_LAST_NOTIFICATIONS_RECEIVED;
    public static final String PREFS_OS_LAST_IAMS_RECEIVED = OSInfluenceDataRepository.PREFS_OS_LAST_IAMS_RECEIVED;
    // Outcomes Influence params
    public static final String PREFS_OS_NOTIFICATION_LIMIT = OSInfluenceDataRepository.PREFS_OS_NOTIFICATION_LIMIT;
    public static final String PREFS_OS_IAM_LIMIT = OSInfluenceDataRepository.PREFS_OS_IAM_LIMIT;
    public static final String PREFS_OS_NOTIFICATION_INDIRECT_ATTRIBUTION_WINDOW = OSInfluenceDataRepository.PREFS_OS_NOTIFICATION_INDIRECT_ATTRIBUTION_WINDOW;
    public static final String PREFS_OS_IAM_INDIRECT_ATTRIBUTION_WINDOW = OSInfluenceDataRepository.PREFS_OS_IAM_INDIRECT_ATTRIBUTION_WINDOW;
    // Outcomes Influence enable params
    public static final String PREFS_OS_DIRECT_ENABLED = OSInfluenceDataRepository.PREFS_OS_DIRECT_ENABLED;
    public static final String PREFS_OS_INDIRECT_ENABLED = OSInfluenceDataRepository.PREFS_OS_INDIRECT_ENABLED;
    public static final String PREFS_OS_UNATTRIBUTED_ENABLED = OSInfluenceDataRepository.PREFS_OS_UNATTRIBUTED_ENABLED;
    // Outcomes Channel Influence types
    public static final String PREFS_OS_OUTCOMES_CURRENT_NOTIFICATION_INFLUENCE = OSInfluenceDataRepository.PREFS_OS_OUTCOMES_CURRENT_NOTIFICATION_INFLUENCE;
    public static final String PREFS_OS_OUTCOMES_CURRENT_IAM_INFLUENCE = OSInfluenceDataRepository.PREFS_OS_OUTCOMES_CURRENT_IAM_INFLUENCE;


    public MockOSInfluenceData(OSSharedPreferences preferences) {
        super(preferences);
    }
}
