package com.onesignal.iam.internal.prompt.impl

import com.onesignal.iam.internal.prompt.InAppMessagePromptTypes
import com.onesignal.location.ILocationManager

internal class InAppMessageLocationPrompt(
    private val _locationManager: ILocationManager
) : InAppMessagePrompt() {
    override suspend fun handlePrompt(): PromptActionResult? {
        val result = _locationManager.requestPermission()

        return when (result) {
            true -> PromptActionResult.PERMISSION_GRANTED
            false -> PromptActionResult.PERMISSION_DENIED
            null -> null
        }
    }

    override val promptKey: String
        get() = InAppMessagePromptTypes.LOCATION_PROMPT_KEY
}
