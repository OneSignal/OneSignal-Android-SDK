package com.onesignal.debug

/**
 * Access to debug the SDK in the event additional information is required to diagnose any
 * SDK-related issues.
 *
 * WARNING: This should not be used in a production setting.
 */
interface IDebugManager {
    /**
     * The log level the OneSignal SDK should be writing to the Android log. Defaults to [LogLevel.WARN].
     */
    var logLevel: LogLevel

    /**
     * The log level the OneSignal SDK should be showing as a modal. Defaults to [LogLevel.NONE].
     */
    var alertLevel: LogLevel
}
