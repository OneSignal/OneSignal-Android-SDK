package com.onesignal.notifications.internal.listeners

import com.onesignal.common.modeling.ISingletonModelStoreChangeHandler
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.common.modeling.ModelChangedArgs
import com.onesignal.common.threading.suspendifyOnIO
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.notifications.INotificationsManager
import com.onesignal.notifications.IPermissionObserver
import com.onesignal.notifications.internal.channels.INotificationChannelManager
import com.onesignal.notifications.internal.pushtoken.IPushTokenManager
import com.onesignal.user.internal.subscriptions.ISubscriptionChangedHandler
import com.onesignal.user.internal.subscriptions.ISubscriptionManager
import com.onesignal.user.internal.subscriptions.SubscriptionModel
import com.onesignal.user.internal.subscriptions.SubscriptionStatus
import com.onesignal.user.subscriptions.ISubscription

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
    private val _subscriptionManager: ISubscriptionManager,
) : IStartableService,
    ISingletonModelStoreChangeHandler<ConfigModel>,
    IPermissionObserver,
    ISubscriptionChangedHandler {
    override fun start() {
        _configModelStore.subscribe(this)
        _notificationsManager.addPermissionObserver(this)
        _subscriptionManager.subscribe(this)

        // If notification permission is already granted at startup (e.g. after reinstall or
        // app data clear), the push subscription status may still be NO_PERMISSION because
        // no permission-change event will fire — permission was already true when the SDK
        // initialized, so oldPermission == newPermission and no event is emitted.
        // The config-hydration path (onModelReplaced/HYDRATE) only fires when the config
        // cache is invalid, so it cannot be relied on either.
        // Eagerly retrieve the push token here to ensure the subscription is active.
        if (_notificationsManager.permission) {
            retrievePushTokenAndUpdateSubscription()
        }
    }

    override fun onModelReplaced(
        model: ConfigModel,
        tag: String,
    ) {
        // we only need to do things when the config model was replaced
        // via a hydration from the backend.
        if (tag != ModelChangeTags.HYDRATE) {
            return
        }

        _channelManager.processChannelList(model.notificationChannels)

        retrievePushTokenAndUpdateSubscription()
    }

    override fun onModelUpdated(
        args: ModelChangedArgs,
        tag: String,
    ) {
    }

    override fun onNotificationPermissionChange(permission: Boolean) {
        retrievePushTokenAndUpdateSubscription()
    }

    private fun retrievePushTokenAndUpdateSubscription() {
        val pushSubscription = _subscriptionManager.subscriptions.push

        suspendifyOnIO {
            val pushTokenAndStatus = _pushTokenManager.retrievePushToken()
            val permission = _notificationsManager.permission
            _subscriptionManager.addOrUpdatePushSubscriptionToken(
                pushTokenAndStatus.token,
                if (permission) pushTokenAndStatus.status else SubscriptionStatus.NO_PERMISSION,
            )
        }
    }

    override fun onSubscriptionRemoved(subscription: ISubscription) { }

    override fun onSubscriptionAdded(subscription: ISubscription) { }

    override fun onSubscriptionChanged(
        subscription: ISubscription,
        args: ModelChangedArgs,
    ) {
        // when setting optedIn=true and there aren't permissions, automatically drive
        // permission request.
        if (args.path == SubscriptionModel::optedIn.name && args.newValue == true && !_notificationsManager.permission) {
            suspendifyOnIO {
                _notificationsManager.requestPermission(true)
            }
        }
    }
}
