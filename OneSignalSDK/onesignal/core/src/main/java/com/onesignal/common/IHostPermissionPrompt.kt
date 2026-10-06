package com.onesignal.common

/**
 * Shows the Android permission dialog on the host's own activity, for wrappers whose
 * activity is not a `ComponentActivity` and so cannot host an activity result launcher.
 */
interface IHostPermissionPrompt {
    /** Receives the user's answer. May be called from any thread. */
    fun interface Callback {
        /** Delivers the answer. Must be called at most once per request. */
        fun onResult(granted: Boolean)
    }

    /**
     * Implementations must deliver exactly one [Callback.onResult], including when the host
     * activity goes away before the user answers, or return false so the SDK can complete the
     * prompt as denied rather than leaving the caller suspended.
     *
     * @return false when no host activity is currently available to prompt on.
     */
    fun request(
        androidPermission: String,
        callback: Callback,
    ): Boolean
}
