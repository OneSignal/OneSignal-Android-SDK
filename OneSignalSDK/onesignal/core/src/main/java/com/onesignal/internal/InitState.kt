package com.onesignal.internal

/**
 * Represents the current initialization state of the OneSignal SDK.
 *
 * This enum is used to track the lifecycle of SDK initialization, ensuring that operations like `login`,
 * `logout`, or accessing services are only allowed when the SDK is fully initialized.
 */
internal enum class InitState {
    /**
     * SDK initialization has not yet started.
     * SDK-dependent calls log and return a default; they never throw.
     */
    NOT_STARTED,

    /**
     * SDK initialization is currently in progress.
     * Calls that require initialization wait (block or suspend) until this completes.
     */
    IN_PROGRESS,

    /**
     * SDK initialization completed successfully.
     * All SDK-dependent operations can proceed safely.
     */
    SUCCESS,

    /**
     * SDK initialization has failed (e.g., no usable app ID, locked storage).
     * SDK-dependent calls log and return a default until a retry of initWithContext succeeds.
     */
    FAILED,

    ;

    fun isSDKAccessible(): Boolean {
        return this == IN_PROGRESS || this == SUCCESS
    }
}
