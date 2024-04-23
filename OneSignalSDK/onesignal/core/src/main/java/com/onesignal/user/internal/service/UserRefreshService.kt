package com.onesignal.user.internal.service

import com.onesignal.common.IDManager
import com.onesignal.core.internal.application.IApplicationLifecycleHandler
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.operations.RefreshUserOperation

// Ensure cache for the user is refreshed once per cold start when app
// is in the foreground. This saves resources as there are a number of
// events (such as push received or non-OneSignal events) that start
// the app in the background but will never read/write any user
// properties.
class UserRefreshService(
    private val _applicationService: IApplicationService,
    private val _operationRepo: IOperationRepo,
    private val _configModelStore: ConfigModelStore,
    private val _identityModelStore: IdentityModelStore,
) : IStartableService,
    IApplicationLifecycleHandler {
    private fun refreshUser() {
        if (IDManager.isLocalId(_identityModelStore.model.onesignalId)) return

        _operationRepo.enqueue(
            RefreshUserOperation(
                _configModelStore.model.appId,
                _identityModelStore.model.onesignalId,
            ),
        )
    }

    override fun start() {
        if (_applicationService.isInForeground) {
            refreshUser()
        } else {
            _applicationService.addApplicationLifecycleHandler(this)
        }
    }

    private var onFocusCalled: Boolean = false

    override fun onFocus() {
        if (onFocusCalled) return
        onFocusCalled = true
        refreshUser()
    }

    override fun onUnfocused() { }
}
