package com.onesignal.user.internal.customEvents

/**
 * The gateway to user custom event logic.
 */
interface ICustomEventController {
    /**
     * Send an custom event to the backend with optional properties.
     */
    suspend fun sendCustomEvent(
        name: String,
        properties: Map<String, Any>? = null,
    )
}
