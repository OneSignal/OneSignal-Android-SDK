package com.onesignal.common

/**
 * Shows the Android permission dialog on the host's own activity, for wrappers whose
 * activity is not a `ComponentActivity` and so cannot host an activity result launcher.
 */
interface IHostPermissionPrompt {
    /** Receives the user's answer. May be called from any thread. */
    fun interface Callback {
        /** Call once after [request] returns true, and do not call it when [request] returns false. */
        fun onResult(granted: Boolean)
    }

    /**
     * Return false, and do not call [Callback], when no activity can prompt.
     * Otherwise call [Callback.onResult] once, even if the host goes away first.
     */
    fun request(
        androidPermission: String,
        callback: Callback,
    ): Boolean
}
