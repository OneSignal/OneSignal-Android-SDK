package com.onesignal

object LocationPermissionController : PermissionsActivity.PermissionCallback {
    private const val PERMISSION_TYPE = "LOCATION"

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

    private fun onResponse(result: OneSignal.PromptActionResult) {
        LocationController.sendAndClearPromptHandlers(
            true,
            result
        )
    }

    override fun onAccept() {
        onResponse(OneSignal.PromptActionResult.PERMISSION_GRANTED)
        LocationController.startGetLocation()
    }

    override fun onReject(fallbackToSettings: Boolean) {
        onResponse(OneSignal.PromptActionResult.PERMISSION_DENIED)
        if (fallbackToSettings) showFallbackAlertDialog()
        LocationController.fireFailedComplete()
    }

    private fun showFallbackAlertDialog() {
        val activity = OneSignal.getCurrentActivity() ?: return
        AlertDialogPrepromptForAndroidSettings.show(
            activity,
            activity.getString(R.string.location_permission_name_for_title),
            activity.getString(R.string.location_permission_settings_message),
            object : AlertDialogPrepromptForAndroidSettings.Callback {
                override fun onAccept() {
                    NavigateToAndroidSettingsForLocation.show(activity)
                    LocationController.sendAndClearPromptHandlers(
                        true,
                        OneSignal.PromptActionResult.PERMISSION_DENIED
                    )
                }
                override fun onDecline() {
                    LocationController.sendAndClearPromptHandlers(
                        true,
                        OneSignal.PromptActionResult.PERMISSION_DENIED
                    )
                }
            }
        )
    }
}
