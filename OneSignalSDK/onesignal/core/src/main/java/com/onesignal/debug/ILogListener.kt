package com.onesignal.debug

/**
 * A listener to receive all logging messages the OneSignal SDK produces.
 * Implement this interface and add it via [IDebugManager.addLogListener] to capture
 * and process SDK logs, for example to send them to your own logging service.
 *
 * Note: All log messages are always passed to this listener regardless of the [IDebugManager.logLevel] setting.
 */
fun interface ILogListener {
    /**
     * Called when a log event occurs in the OneSignal SDK.
     *
     * @param event The [OneSignalLogEvent] containing the log level and message.
     */
    fun onLogEvent(event: OneSignalLogEvent)
}
