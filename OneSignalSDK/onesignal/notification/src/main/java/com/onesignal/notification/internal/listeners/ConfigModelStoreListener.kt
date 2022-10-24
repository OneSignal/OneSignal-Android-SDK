package com.onesignal.notification.internal.listeners

import com.onesignal.common.modeling.ISingletonModelStoreChangeHandler
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.common.modeling.ModelChangedArgs
import com.onesignal.common.threading.suspendifyOnThread
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.notification.internal.channels.INotificationChannelManager
import com.onesignal.notification.internal.pushtoken.IPushTokenManager

internal class ConfigModelStoreListener(
    private val _configModelStore: ConfigModelStore,
    private val _channelManager: INotificationChannelManager,
    private val _pushTokenManager: IPushTokenManager
) : IStartableService, ISingletonModelStoreChangeHandler<ConfigModel> {

    override fun start() {
        _configModelStore.subscribe(this)
    }

    override fun onModelReplaced(model: ConfigModel, tag: String) {
        // we only need to do things when the config model was replaced
        // via a hydration from the backend.
        if (tag != ModelChangeTags.HYDRATE) {
            return
        }

        // Refresh the notification permissions whenever we come back into focus
        _channelManager.processChannelList(model.notificationChannels)

        suspendifyOnThread {
            _pushTokenManager.retrievePushToken()
        }
    }

    override fun onModelUpdated(args: ModelChangedArgs, tag: String) {
    }
}
