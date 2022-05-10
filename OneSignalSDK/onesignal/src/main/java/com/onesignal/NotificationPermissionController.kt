package com.onesignal

object NotificationPermissionController : PermissionsActivity.PermissionCallback {
    private const val PERMISSION_TYPE = "NOTIFICATION"

    init {
        PermissionsActivity.registerAsCallback(PERMISSION_TYPE, this)
    }

    fun prompt(
        fallbackToSettings: Boolean,
        androidPermissionString: String,
    ) {
        PermissionsActivity.startPrompt(
            fallbackToSettings,
            PERMISSION_TYPE,
            androidPermissionString,
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
