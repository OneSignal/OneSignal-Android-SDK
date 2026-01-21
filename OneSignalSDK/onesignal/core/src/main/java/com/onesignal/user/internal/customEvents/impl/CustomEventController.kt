package com.onesignal.user.internal.customEvents.impl

import com.onesignal.common.JSONUtils
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.time.ITime
import com.onesignal.user.internal.customEvents.ICustomEventController
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.operations.TrackCustomEventOperation

class CustomEventController(
    private val _identityModelStore: IdentityModelStore,
    private val _configModelStore: ConfigModelStore,
    private val _time: ITime,
    private val _opRepo: IOperationRepo,
) : ICustomEventController {
    override fun sendCustomEvent(
        name: String,
        properties: Map<String, Any>?,
    ) {
        val op =
            TrackCustomEventOperation(
                _configModelStore.model.appId,
                _identityModelStore.model.onesignalId,
                _identityModelStore.model.externalId,
                _time.currentTimeMillis,
                name,
                properties?.let { JSONUtils.mapToJson(it).toString() },
            )
        _opRepo.enqueue(op)
    }
}
