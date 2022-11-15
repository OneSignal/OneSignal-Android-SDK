package com.onesignal.notifications.internal.listeners

import com.onesignal.common.modeling.ISingletonModelStoreChangeHandler
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.common.modeling.ModelChangedArgs
import com.onesignal.common.threading.suspendifyOnThread
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.notifications.INotificationsManager
import com.onesignal.notifications.IPermissionChangedHandler
import com.onesignal.notifications.internal.channels.INotificationChannelManager
import com.onesignal.notifications.internal.pushtoken.IPushTokenManager
import com.onesignal.user.internal.subscriptions.ISubscriptionManager
import com.onesignal.user.internal.subscriptions.SubscriptionStatus

/**
 * The device registration listener will subscribe to events and at the appropriate time will
 * retrieve a push token and update the push subscription.
 *
 * A push token is retrieved when push notification permission on the device have been granted.
 */
internal class DeviceRegistrationListener(
    private val _configModelStore: ConfigModelStore,
    private val _channelManager: INotificationChannelManager,
    private val _pushTokenManager: IPushTokenManager,
    private val _notificationsManager: INotificationsManager,
    private val _subscriptionManager: ISubscriptionManager
) : IStartableService,
    ISingletonModelStoreChangeHandler<ConfigModel>,
    IPermissionChangedHandler {

    override fun start() {
        _configModelStore.subscribe(this)
        _notificationsManager.addPermissionChangedHandler(this)
    }

    override fun onModelReplaced(model: ConfigModel, tag: String) {
        // we only need to do things when the config model was replaced
        // via a hydration from the backend.
        if (tag != ModelChangeTags.HYDRATE) {
            return
        }

        _channelManager.processChannelList(model.notificationChannels)

        retrievePushTokenAndUpdateSubscription(_notificationsManager.permission)
    }

    override fun onModelUpdated(args: ModelChangedArgs, tag: String) {
    }

    override fun onPermissionChanged(permission: Boolean) {
        retrievePushTokenAndUpdateSubscription(permission)
    }

    private fun retrievePushTokenAndUpdateSubscription(permission: Boolean) {
        val pushSubscription = _subscriptionManager.subscriptions.push

        if (pushSubscription.pushToken.isNotEmpty()) {
            _subscriptionManager.addOrUpdatePushSubscription(null, if (permission) SubscriptionStatus.SUBSCRIBED else SubscriptionStatus.NO_PERMISSION)
        } else {
            suspendifyOnThread {
                val pushTokenAndStatus = _pushTokenManager.retrievePushToken()
                _subscriptionManager.addOrUpdatePushSubscription(
                    pushTokenAndStatus.token,
                    if (permission) pushTokenAndStatus.status else SubscriptionStatus.NO_PERMISSION
                )
            }
        }
    }
}
