package com.onesignal.session.internal.session.impl

import com.onesignal.common.TimeUtils
import com.onesignal.common.threading.suspendifyOnIO
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.session.internal.outcomes.IOutcomeEventsController
import com.onesignal.session.internal.session.ISessionLifecycleHandler
import com.onesignal.session.internal.session.ISessionService
import com.onesignal.session.internal.session.SessionModelStore
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.operations.TrackSessionEndOperation
import com.onesignal.user.internal.operations.TrackSessionStartOperation
import com.onesignal.user.internal.properties.PropertiesModelStore

/**
 * The [SessionListener] is responsible for subscribing itself as an [ISessionLifecycleHandler]
 * and reacting to the session ending.
 *
 * We need to do the following on session start:
 *
 * 1. Enqueue a [TrackSessionStartOperation] to the [IOperationRepo] so device metadata is refreshed
 *    and, for legacy sessions, the session is counted against the user.
 *
 * We need to do the following on legacy session end; sessions API sessions report these through
 * the sessions API instead:
 *
 * 1. Enqueue a [TrackSessionEndOperation] to the [IOperationRepo] so session time will be counted
 *    against the user.
 * 2. Send up an internal `os__session_duration` outcome, which will pick up any influences that led
 *    to the session existing.
 */
internal class SessionListener(
    private val _operationRepo: IOperationRepo,
    private val _sessionService: ISessionService,
    private val _configModelStore: ConfigModelStore,
    private val _identityModelStore: IdentityModelStore,
    private val _propertiesModelStore: PropertiesModelStore,
    private val _outcomeEventsController: IOutcomeEventsController,
    private val sessionModelStore: SessionModelStore,
) : IStartableService, ISessionLifecycleHandler {
    override fun start() {
        _sessionService.subscribe(this)
    }

    override fun onSessionStarted() {
        _propertiesModelStore.model.timezone = TimeUtils.getTimeZoneId()
        _operationRepo.enqueue(
            TrackSessionStartOperation(
                _configModelStore.model.appId,
                _identityModelStore.model.onesignalId,
                _identityModelStore.model.externalId,
                countsSession = !sessionModelStore.model.usesSessionsApi,
            ),
            true,
        )
    }

    override fun onSessionActive() {
        _propertiesModelStore.model.timezone = TimeUtils.getTimeZoneId()
    }

    override fun onSessionEnded(
        duration: Long,
        usesSessionsApi: Boolean,
    ) {
        if (usesSessionsApi) return

        val durationInSeconds = duration / 1000

        // Time is erroneous if below 1 second or over a day
        if (durationInSeconds < 1L || durationInSeconds > SECONDS_IN_A_DAY) {
            Logging.info("SessionListener.onSessionEnded sending duration of $durationInSeconds seconds")
        }

        _operationRepo.enqueue(
            TrackSessionEndOperation(
                _configModelStore.model.appId,
                _identityModelStore.model.onesignalId,
                _identityModelStore.model.externalId,
                durationInSeconds,
            ),
        )

        suspendifyOnIO {
            _outcomeEventsController.sendSessionEndOutcomeEvent(durationInSeconds)
        }
    }

    companion object {
        const val SECONDS_IN_A_DAY = 86_400L
    }
}
