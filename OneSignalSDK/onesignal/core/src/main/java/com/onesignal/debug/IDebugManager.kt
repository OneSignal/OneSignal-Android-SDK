package com.onesignal.debug

/**
 * Access to debug the SDK in the event additional information is required to diagnose any
 * SDK-related issues.
 */
interface IDebugManager {
    /**
     * The log level the OneSignal SDK should be writing to the Android log. Defaults to [LogLevel.WARN].
     * WARNING: This should not be set higher than LogLevel.WARN in a production setting.
     */
    var logLevel: LogLevel

    /**
     * The log level the OneSignal SDK should be showing as a modal. Defaults to [LogLevel.NONE].
     * WARNING: This should not be used in a production setting.
     */
    var alertLevel: LogLevel

    /**
     * Add a listener to receive all logging messages the SDK produces.
     * Useful to capture and send logs to your server.
     * NOTE: All log messages are always passed, logLevel has no effect on this.
     */
    fun addLogListener(listener: ILogListener)

    /**
     * Removes a listener added by addLogListener
     */
    fun removeLogListener(listener: ILogListener)
}
