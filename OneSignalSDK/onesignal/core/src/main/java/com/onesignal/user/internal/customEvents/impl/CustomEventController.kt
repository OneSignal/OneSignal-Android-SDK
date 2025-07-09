package com.onesignal.user.internal.customEvents.impl

import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.core.internal.time.ITime
import com.onesignal.user.internal.customEvents.ICustomEventController
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.subscriptions.SubscriptionModelStore

class CustomEventController(
    private val _identityModelStore: IdentityModelStore,
    private val _time: ITime,
    private val _deviceService: IDeviceService,
    private val _configModelStore: ConfigModelStore,
    private val _subscriptionModelStore: SubscriptionModelStore,
) : ICustomEventController, IStartableService {
    override suspend fun sendCustomEvent(
        name: String,
        properties: Map<String, Any>?,
    ) {
        val currentPushSubscription = _subscriptionModelStore.list().firstOrNull { it.id == _configModelStore.model.pushSubscriptionId }
        val customEvent =
            CustomEvent(
                name,
                properties,
                _identityModelStore.model.onesignalId,
                _identityModelStore.model.externalId,
                _time.currentTimeMillis,
                _deviceService.deviceType.name,
                currentPushSubscription?.sdk ?: "Unknown SDK",
                currentPushSubscription?.appVersion ?: "Unknown appVersion",
            )

        // TODO: send http request
    }

    fun saveCustomEvent(event: CustomEvent) {
        // TODO for the caching session
    }

    override fun start() {
        // TODO send all cached events and clear the queue
    }
}
