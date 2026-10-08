package com.onesignal.common

// Wrapper SDKs call this from Java, C# bindings, and Unity JNI by name. Tracked by apiCheck.
object OneSignalWrapper {
    /**
     * The type of the wrapper SDK.
     */
    @JvmStatic
    var sdkType: String? = null

    /**
     * The version of the wrapper SDK.
     */
    @JvmStatic
    var sdkVersion: String? = null

    /**
     * Set on activity attach and cleared on detach by wrappers whose host activity cannot
     * host an activity result launcher. Consulted only when `PermissionsActivity` is missing.
     */
    @JvmStatic
    @Volatile
    var hostPermissionPrompt: IHostPermissionPrompt? = null
}
