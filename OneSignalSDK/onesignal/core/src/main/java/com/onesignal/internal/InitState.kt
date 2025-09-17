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
     * Calling SDK-dependent methods in this state will throw an exception.
     */
    NOT_STARTED,

    /**
     * SDK initialization is currently in progress.
     * Calls that require initialization will block (via a latch) until this completes.
     */
    IN_PROGRESS,

    /**
     * SDK initialization completed successfully.
     * All SDK-dependent operations can proceed safely.
     */
    SUCCESS,

    /**
     * SDK initialization has failed due to an unrecoverable error (e.g., missing app ID).
     * All dependent operations should fail fast or throw until re-initialized.
     */
    FAILED;

    fun isSDKAccessible() : Boolean {
        return this == IN_PROGRESS || this == SUCCESS
    }
}