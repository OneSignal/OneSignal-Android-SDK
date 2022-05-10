package com.onesignal

import android.os.Build

object NotificationPermissionController : PermissionsActivity.PermissionCallback {
    private const val PERMISSION_TYPE = "NOTIFICATION"
    private const val ANDROID_PERMISSION_STRING = "android.permission.POST_NOTIFICATIONS"

    init {
        PermissionsActivity.registerAsCallback(PERMISSION_TYPE, this)
    }

    fun prompt(fallbackToSettings: Boolean) {
        // TODO: Android 13 Beta 1 reports as 32 instead of 33, update to 33 once Google fixes this
        if (Build.VERSION.SDK_INT < 32)
            return

        PermissionsActivity.startPrompt(
            fallbackToSettings,
            PERMISSION_TYPE,
            ANDROID_PERMISSION_STRING,
            this::class.java
        )
    }

    override fun onAccept() {
        OneSignal.refreshNotificationPermissionState()
    }

    override fun onReject(fallbackToSettings: Boolean) {
        if (fallbackToSettings) showFallbackAlertDialog()
    }

    private fun showFallbackAlertDialog() {
        val activity = OneSignal.getCurrentActivity() ?: return
        AlertDialogPrepromptForAndroidSettings.show(
            activity,
            activity.getString(R.string.notification_permission_name_for_title),
            activity.getString(R.string.notification_permission_settings_message),
            object : AlertDialogPrepromptForAndroidSettings.Callback {
                override fun onAccept() {
                    NavigateToAndroidSettingsForNotifications.show(activity)
                }
                override fun onDecline() {
                }
            }
        )
    }
}
