package com.onesignal.common

/**
 * Shows the Android permission dialog on the host's own activity.
 *
 * Wrapper SDKs whose host activity is not a `ComponentActivity` (notably Flutter's
 * `FlutterActivity`) register one of these so OneSignal can still prompt when
 * `PermissionsActivity` is absent from the merged manifest.
 */
interface IHostPermissionPrompt {
    fun interface Callback {
        fun onResult(granted: Boolean)
    }

    /**
     * Must deliver exactly one [Callback.onResult], or return false so the SDK can
     * complete the pending prompt as denied rather than leaving the caller suspended.
     *
     * @return false when no host activity is currently available to prompt on.
     */
    fun request(
        androidPermission: String,
        callback: Callback,
    ): Boolean
}
