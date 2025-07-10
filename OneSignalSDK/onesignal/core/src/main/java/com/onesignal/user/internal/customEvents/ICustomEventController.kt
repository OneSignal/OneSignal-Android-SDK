package com.onesignal.user.internal.customEvents

/**
 * The gateway to user custom event logic.
 */
interface ICustomEventController {
    /**
     * Enqueue an custom event with optional properties to send to the backend.
     * The event will be sent immediately if a backend onesignal ID is present; otherwise, it will
     *  be saved into a queue and will be sent once the onesignal ID is fetched
     */
    fun enqueueCustomEvent(
        name: String,
        properties: Map<String, Any>? = null,
    )
}
