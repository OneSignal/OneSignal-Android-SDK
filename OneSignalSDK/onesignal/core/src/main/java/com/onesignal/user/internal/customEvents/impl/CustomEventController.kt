package com.onesignal.user.internal.customEvents.impl

import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.time.ITime
import com.onesignal.user.internal.customEvents.ICustomEventController
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.operations.TrackEventOperation

class CustomEventController(
    private val _identityModelStore: IdentityModelStore,
    private val _configModelStore: ConfigModelStore,
    private val _time: ITime,
    private val _opRepo: IOperationRepo,
) : ICustomEventController {
    override fun sendCustomEvent(event: CustomEvent) {
        val op =
            TrackEventOperation(
                _configModelStore.model.appId,
                _identityModelStore.model.onesignalId,
                _identityModelStore.model.externalId,
                _time.currentTimeMillis,
                event,
            )
        _opRepo.enqueue(op)
    }
}
