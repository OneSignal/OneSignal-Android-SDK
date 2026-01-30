package com.onesignal.user.internal.customEvents.impl

import com.onesignal.common.JSONUtils
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.time.ITime
import com.onesignal.user.internal.customEvents.ICustomEventController
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.operations.TrackCustomEventOperation

class CustomEventController(
    private val identityModelStore: IdentityModelStore,
    private val configModelStore: ConfigModelStore,
    private val time: ITime,
    private val opRepo: IOperationRepo,
) : ICustomEventController {
    override fun sendCustomEvent(
        name: String,
        properties: Map<String, Any?>?,
    ) {
        val op =
            TrackCustomEventOperation(
                configModelStore.model.appId,
                identityModelStore.model.onesignalId,
                identityModelStore.model.externalId,
                time.currentTimeMillis,
                name,
                properties?.let { JSONUtils.mapToJson(it).toString() },
            )
        opRepo.enqueue(op)
    }
}
