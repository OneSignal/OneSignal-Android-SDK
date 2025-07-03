package com.onesignal.user.internal.customEvents.impl

import com.onesignal.user.internal.customEvents.ICustomEventController
import com.onesignal.user.internal.identity.IdentityModelStore

class CustomEventController(
    private val _identityModelStore: IdentityModelStore,
) : ICustomEventController {
    override suspend fun sendCustomEvent(
        name: String,
        properties: Map<String, CustomEventProperty>?
    ) {
        TODO("Logic for identity verification: always use externalID if IV is on")
        val id = _identityModelStore.model.externalId ?: _identityModelStore.model.onesignalId

        TODO("Send http request in next commit")
    }
}