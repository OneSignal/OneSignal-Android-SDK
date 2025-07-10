package com.onesignal.user.internal.operations.impl.listeners

import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.operations.Operation
import com.onesignal.core.internal.operations.listeners.ModelStoreListener
import com.onesignal.core.internal.time.ITime
import com.onesignal.user.internal.customEvents.CustomEventModelStore
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.operations.CustomEvent
import com.onesignal.user.internal.operations.TrackEventOperation

internal class CustomEventModelStoreListener(
    store: CustomEventModelStore,
    opRepo: IOperationRepo,
    private val _identityModelStore: IdentityModelStore,
    private val _configModelStore: ConfigModelStore,
    private val _time: ITime,
) : ModelStoreListener<CustomEvent>(store, opRepo) {
    override fun getAddOperation(model: CustomEvent): Operation {
        val op =
            TrackEventOperation(
                _configModelStore.model.appId,
                _identityModelStore.model.onesignalId,
                _identityModelStore.model.externalId,
                _time.currentTimeMillis,
                model,
            )
        return op
    }

    override fun getRemoveOperation(model: CustomEvent): Operation? {
        return null
    }

    override fun getUpdateOperation(
        model: CustomEvent,
        path: String,
        property: String,
        oldValue: Any?,
        newValue: Any?,
    ): Operation? {
        return null
    }
}
