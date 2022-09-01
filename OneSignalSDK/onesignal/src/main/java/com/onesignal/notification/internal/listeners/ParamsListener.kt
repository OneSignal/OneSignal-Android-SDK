package com.onesignal.notification.internal.listeners

import com.onesignal.core.internal.common.suspendifyOnThread
import com.onesignal.core.internal.params.IParamsChangedHandler
import com.onesignal.core.internal.params.IParamsService
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.notification.internal.channels.INotificationChannelManager
import com.onesignal.notification.internal.pushtoken.IPushTokenManager

internal class ParamsListener(
    private val _paramsService: IParamsService,
    private val _channelManager: INotificationChannelManager,
    private val _pushTokenManager: IPushTokenManager
) : IStartableService, IParamsChangedHandler {

    override fun start() {
        _paramsService.subscribe(this)
    }

    override fun onParamsChanged() {
        // Refresh the notification permissions whenever we come back into focus
        _channelManager.processChannelList(_paramsService.notificationChannels)

        suspendifyOnThread {
            _pushTokenManager.retrievePushToken()
        }
    }
}
