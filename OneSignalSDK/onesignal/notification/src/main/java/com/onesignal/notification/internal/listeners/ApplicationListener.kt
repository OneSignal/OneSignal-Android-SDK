package com.onesignal.notification.internal.listeners

import com.onesignal.core.internal.application.IApplicationLifecycleHandler
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.notification.internal.INotificationStateRefresher

internal class ApplicationListener(
    private val _applicationService: IApplicationService,
    private val _notificationStateRefresher: INotificationStateRefresher
) : IStartableService, IApplicationLifecycleHandler {
    override fun start() {
        _applicationService.addApplicationLifecycleHandler(this)
    }

    override fun onFocus() {
        _notificationStateRefresher.refreshNotificationState()
    }

    override fun onUnfocused() {
    }
}
