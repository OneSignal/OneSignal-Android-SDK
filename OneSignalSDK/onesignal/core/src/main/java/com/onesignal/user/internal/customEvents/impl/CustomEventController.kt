package com.onesignal.user.internal.customEvents.impl

import com.onesignal.common.IDManager
import com.onesignal.common.exceptions.BackendException
import com.onesignal.common.modeling.ISingletonModelStoreChangeHandler
import com.onesignal.common.modeling.ModelChangedArgs
import com.onesignal.common.threading.suspendifyOnThread
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.core.internal.time.ITime
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.user.internal.backend.IdentityConstants
import com.onesignal.user.internal.customEvents.ICustomEventController
import com.onesignal.user.internal.identity.IdentityModel
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.subscriptions.SubscriptionModelStore
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentLinkedQueue

class CustomEventController(
    private val _identityModelStore: IdentityModelStore,
    private val _time: ITime,
    private val _deviceService: IDeviceService,
    private val _configModelStore: ConfigModelStore,
    private val _subscriptionModelStore: SubscriptionModelStore,
    private val _customEventBackendService: ICustomEventBackendService,
) : ICustomEventController, IStartableService, ISingletonModelStoreChangeHandler<IdentityModel> {
    internal val queue = ConcurrentLinkedQueue<CustomEvent>()
    internal val rfc3339Formatter: SimpleDateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    init {
        _identityModelStore.subscribe(this)
    }

    override fun enqueueCustomEvent(
        name: String,
        properties: Map<String, Any>?,
    ) {
        val currentPushSubscription = _subscriptionModelStore.list().firstOrNull { it.id == _configModelStore.model.pushSubscriptionId }
        // TODO: when identity verification is on, use external ID only
        val event =
            CustomEvent(
                _configModelStore.model.appId,
                name,
                properties,
                _identityModelStore.model.onesignalId,
                _identityModelStore.model.externalId,
                rfc3339Formatter.format(_time.currentTimeMillis),
                _deviceService.deviceType.name,
                currentPushSubscription?.sdk ?: "Unknown SDK",
                currentPushSubscription?.appVersion ?: "Unknown appVersion",
            )

        saveCustomEvent(event)
        if (!IDManager.isLocalId(_identityModelStore.model.onesignalId)) {
            // immediately send the event if onesignalId is already retrieved
            sendCustomEvent(event)
        }
    }

    private fun sendCustomEvent(event: CustomEvent) {
        // send the custom event in the background
        suspendifyOnThread {
            try {
                _customEventBackendService.sendCustomEvent(event)
            } catch (ex: BackendException) {
                // TODO: handling 400 response due to payload being too large
                Logging.warn(
                    "ERROR: $ex for sending $event, ",
                )
            }

            queue.remove(event)
        }
    }

    private fun saveCustomEvent(event: CustomEvent) {
        // TODO: might need to persist saved events
        queue.add(event)
    }

    private fun updateAllEventsWithOnesignalId(onesignalId: String) {
        synchronized(queue) {
            queue.forEach {
                if (it.onesignalId != null && IDManager.isLocalId(it.onesignalId!!)) {
                    it.updateOnesignalId(onesignalId)
                }
            }
        }
    }

    private fun sendAllSavedEvents() {
        synchronized(queue) {
            queue.forEach {
                if (it.onesignalId?.let { id -> !IDManager.isLocalId(id) } == true) {
                    // send any saved event that has an updated onesignal ID
                    sendCustomEvent(it)
                }
            }
        }
    }

    override fun start() {
        sendAllSavedEvents()
    }

    override fun onModelReplaced(
        model: IdentityModel,
        tag: String,
    ) { }

    override fun onModelUpdated(
        args: ModelChangedArgs,
        tag: String,
    ) {
        if (args.property == IdentityConstants.ONESIGNAL_ID) {
            val onesignalId = args.newValue.toString()
            if (!IDManager.isLocalId(onesignalId)) {
                // the onesignal ID is updated, send all queued events with the updated onesignal ID
                updateAllEventsWithOnesignalId(onesignalId)
                sendAllSavedEvents()
            }
        }
    }
}
