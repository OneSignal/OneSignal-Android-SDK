package com.onesignal.core.internal.listeners

import com.onesignal.core.internal.common.suspendifyOnThread
import com.onesignal.core.internal.models.ConfigModelStore
import com.onesignal.core.internal.models.IdentityModelStore
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.operations.TrackSessionOperation
import com.onesignal.core.internal.outcomes.IOutcomeEventsController
import com.onesignal.core.internal.session.ISessionLifecycleHandler
import com.onesignal.core.internal.session.ISessionService
import com.onesignal.core.internal.startup.IStartableService

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
        _operationRepo.enqueue(TrackSessionOperation(_configModelStore.get().appId, _identityModelStore.get().onesignalId, duration))

        suspendifyOnThread {
            _outcomeEventsController.sendOutcomeEvent("__os_session_end")
        }
    }
}
