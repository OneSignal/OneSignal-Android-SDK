package com.onesignal.core.internal.permissions

/**
 * A service for requesting permission from the user.
 */
internal interface IRequestPermissionService {
    interface PermissionCallback {
        fun onAccept()
        fun onReject(fallbackToSettings: Boolean)
    }

    /**
     * Register a new permission type and the receiver of the callback once the user
     * has made a decision.
     *
     * @param permissionType The type of permission that will be requested. Can be any unique string.
     * @param callback The [PermissionCallback] that will be called when the user has granted/denied
     * permission for that permission.
     */
    fun registerAsCallback(
        permissionType: String,
        callback: PermissionCallback
    )

    /**
     * Prompt the user for the permission being requested.
     *
     * @param fallbackCondition Whether the user should be directed to app settings in the event
     * permission cannot be granted/denied inline.
     * @param permissionRequestType The type of permission to request. Drives the callback, should align
     * with a permissionType registered via [registerAsCallback].
     * @param androidPermissionString The Android permission string being requested.
     * @param callbackClass The name of the class that will receive the callback. Required in case the app
     * is killed when the permission prompt is showing.
     */
    fun startPrompt(
        fallbackCondition: Boolean,
        permissionRequestType: String?,
        androidPermissionString: String?,
        callbackClass: Class<*>
    )
}
