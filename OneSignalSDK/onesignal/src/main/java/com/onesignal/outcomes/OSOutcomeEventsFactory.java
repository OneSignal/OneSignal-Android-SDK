package com.onesignal.outcomes;

import com.onesignal.OSLogger;
import com.onesignal.OSSharedPreferences;
import com.onesignal.OneSignalAPIClient;
import com.onesignal.OneSignalDb;
import com.onesignal.outcomes.domain.OSOutcomeEventsRepository;

public class OSOutcomeEventsFactory {

    private final OSLogger logger;
    private final OSOutcomeEventsCache outcomeEventsCache;
    private final OneSignalAPIClient apiClient;
    private OSOutcomeEventsRepository repository;

    public OSOutcomeEventsFactory(OSLogger logger, OneSignalAPIClient apiClient, OneSignalDb dbHelper, OSSharedPreferences preferences) {
        this.logger = logger;
        this.apiClient = apiClient;

        outcomeEventsCache = new OSOutcomeEventsCache(logger, dbHelper, preferences);
    }

    public OSOutcomeEventsRepository getRepository() {
        if (repository == null)
            createRepository();
        else
            validateRepositoryVersion();
        return repository;
    }

    private void validateRepositoryVersion() {
        if (!outcomeEventsCache.isOutcomesV2ServiceEnabled() && repository instanceof OSOutcomeEventsV1Repository)
            return;
        if (outcomeEventsCache.isOutcomesV2ServiceEnabled() && repository instanceof OSOutcomeEventsV2Repository)
            return;

        createRepository();
    }

    private void createRepository() {
        if (outcomeEventsCache.isOutcomesV2ServiceEnabled())
            repository = new OSOutcomeEventsV2Repository(logger, outcomeEventsCache, new OSOutcomeEventsV2Service(apiClient));
        else
            repository = new OSOutcomeEventsV1Repository(logger, outcomeEventsCache, new OSOutcomeEventsV1Service(apiClient));
    }
}
