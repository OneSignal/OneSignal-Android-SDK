package com.onesignal.onesignal.core.internal.outcomes.impl

import com.onesignal.onesignal.core.internal.backend.http.IHttpClient
import com.onesignal.onesignal.core.internal.outcomes.IOutcomeEventsCache
import com.onesignal.onesignal.core.internal.outcomes.IOutcomeEventsFactory
import com.onesignal.onesignal.core.internal.outcomes.IOutcomeEventsRepository

internal class OSOutcomeEventsFactory(
    private val apiClient: IHttpClient,
    private val outcomeEventsCache: IOutcomeEventsCache) : IOutcomeEventsFactory {

    private var repository: IOutcomeEventsRepository? = null

    override fun getRepository(): IOutcomeEventsRepository = if (repository != null) validateRepositoryVersion() else createRepository()

    private fun validateRepositoryVersion(): IOutcomeEventsRepository {
        if (!outcomeEventsCache.isOutcomesV2ServiceEnabled && repository is OSOutcomeEventsV1Repository)
            return repository!!
        if (outcomeEventsCache.isOutcomesV2ServiceEnabled && repository is OSOutcomeEventsV2Repository)
            return repository!!
        return createRepository()
    }

    private fun createRepository() = if (outcomeEventsCache.isOutcomesV2ServiceEnabled)
        OSOutcomeEventsV2Repository(outcomeEventsCache, OSOutcomeEventsV2Service(apiClient))
    else
        OSOutcomeEventsV1Repository(outcomeEventsCache, OSOutcomeEventsV1Service(apiClient))

}