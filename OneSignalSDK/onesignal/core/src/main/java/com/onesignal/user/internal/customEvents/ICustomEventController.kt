package com.onesignal.user.internal.customEvents

/**
 * Interface for sending custom events to track user behavior.
 */
interface ICustomEventController {
    /**
     * Sends a custom event with optional properties.
     *
     * @param name The name of the custom event
     * @param properties Optional map of event properties. Can contain nested maps, lists, and null values.
     *                   Properties will be converted to JSON format.
     */
    fun sendCustomEvent(
        name: String,
        properties: Map<String, Any?>?,
    )
}
