package com.onesignal.common

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
     * Set by wrappers whose host activity cannot host an activity result launcher.
     * Only consulted when [com.onesignal.core.activities.PermissionsActivity] is missing
     * from the merged manifest.
     */
    @JvmStatic
    @Volatile
    var hostPermissionPrompt: IHostPermissionPrompt? = null
}
