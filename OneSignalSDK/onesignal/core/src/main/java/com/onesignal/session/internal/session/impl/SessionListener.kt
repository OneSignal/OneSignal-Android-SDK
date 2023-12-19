package com.onesignal.session.internal.session.impl

import com.onesignal.common.threading.suspendifyOnThread
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.session.internal.outcomes.IOutcomeEventsController
import com.onesignal.session.internal.session.ISessionLifecycleHandler
import com.onesignal.session.internal.session.ISessionService
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.operations.RefreshUserOperation
import com.onesignal.user.internal.operations.TrackSessionEndOperation
import com.onesignal.user.internal.operations.TrackSessionStartOperation

/**
 * The [SessionListener] is responsible for subscribing itself as an [ISessionLifecycleHandler]
 * and reacting to the session ending.
 *
 * We need to do the following on session start:
 *
 * 1. Enqueue a [TrackSessionStartOperation] to the [IOperationRepo] so session data will be counted
 *    against the user.
 *
 * We need to do the following on session end:
 *
 * 1. Enqueue a [TrackSessionEndOperation] to the [IOperationRepo] so session data will be counted
 *    against the user.
 * 2. Send up an internal `__os_session_end` outcome, which will pick up any influences that led
 *    to the session existing.
 */
internal class SessionListener(
    private val _operationRepo: IOperationRepo,
    private val _sessionService: ISessionService,
    private val _configModelStore: ConfigModelStore,
    private val _identityModelStore: IdentityModelStore,
    private val _outcomeEventsController: IOutcomeEventsController,
) : IStartableService, ISessionLifecycleHandler {
    override fun start() {
        _sessionService.subscribe(this)
    }

    override fun onSessionStarted() {
        _operationRepo.enqueue(TrackSessionStartOperation(_configModelStore.model.appId, _identityModelStore.model.onesignalId))
        _operationRepo.enqueue(RefreshUserOperation(_configModelStore.model.appId, _identityModelStore.model.onesignalId)
        )
    }

    override fun onSessionActive() {
    }

    override fun onSessionEnded(duration: Long) {
        val durationInSeconds = duration / 1000
        _operationRepo.enqueue(
            TrackSessionEndOperation(_configModelStore.model.appId, _identityModelStore.model.onesignalId, durationInSeconds),
        )

        suspendifyOnThread {
            _outcomeEventsController.sendSessionEndOutcomeEvent(durationInSeconds)
        }
    }
}
