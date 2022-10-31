package com.onesignal.session.internal.session.impl

import com.onesignal.common.threading.suspendifyOnThread
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.session.internal.outcomes.IOutcomeEventsController
import com.onesignal.session.internal.session.ISessionLifecycleHandler
import com.onesignal.session.internal.session.ISessionService
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.operations.TrackSessionOperation

/**
 * The [SessionListener] is responsible for subscribing itself as an [ISessionLifecycleHandler]
 * and reacting to the session ending.  Specifically we need to do 2 things whenever a session
 * has ended.
 *
 * 1. Enqueue a [TrackSessionOperation] to the [IOperationRepo] so session data will be counted
 *    against the user.
 * 2. Send up an internal `__os_session_end` outcome, which will pick up any influences that led
 *    to the session existing.
 */
internal class SessionListener(
    private val _operationRepo: IOperationRepo,
    private val _sessionService: ISessionService,
    private val _configModelStore: ConfigModelStore,
    private val _identityModelStore: IdentityModelStore,
    private val _outcomeEventsController: IOutcomeEventsController
) : IStartableService, ISessionLifecycleHandler {

    override fun start() {
        _sessionService.subscribe(this)
    }

    override fun onSessionStarted() {
    }

    override fun onSessionActive() {
    }

    override fun onSessionEnded(duration: Long) {
        _operationRepo.enqueue(TrackSessionOperation(_configModelStore.model.appId, _identityModelStore.model.onesignalId, duration))

        suspendifyOnThread {
            _outcomeEventsController.sendOutcomeEvent("os__session_duration")
        }
    }
}
