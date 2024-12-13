package com.onesignal.user.internal.service

import com.onesignal.common.IDManager
import com.onesignal.common.threading.OSPrimaryCoroutineScope
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.session.internal.session.ISessionLifecycleHandler
import com.onesignal.session.internal.session.ISessionService
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.operations.RefreshUserOperation

// Ensure user is refreshed only when app
// is in the foreground. This saves resources as there are a number of
// events (such as push received or non-OneSignal events) that start
// the app in the background but will never read/write any user
// properties.
class UserRefreshService(
    private val _applicationService: IApplicationService,
    private val _sessionService: ISessionService,
    private val _operationRepo: IOperationRepo,
    private val _configModelStore: ConfigModelStore,
    private val _identityModelStore: IdentityModelStore,
) : IStartableService,
    ISessionLifecycleHandler {
    private fun refreshUser() {
        if (IDManager.isLocalId(_identityModelStore.model.onesignalId) || !_applicationService.isInForeground) {
            return
        }

        OSPrimaryCoroutineScope.execute {
            _operationRepo.enqueue(
                RefreshUserOperation(
                    _configModelStore.model.appId,
                    _identityModelStore.model.onesignalId,
                ),
            )
        }
    }

    override fun start() = _sessionService.subscribe(this)

    override fun onSessionStarted() = refreshUser()

    override fun onSessionActive() { }

    override fun onSessionEnded(duration: Long) { }
}
