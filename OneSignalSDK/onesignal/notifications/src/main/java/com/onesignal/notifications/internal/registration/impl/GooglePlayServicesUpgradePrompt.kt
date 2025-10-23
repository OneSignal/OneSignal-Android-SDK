package com.onesignal.notifications.internal.registration.impl

import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent.CanceledException
import android.content.pm.PackageManager
import com.google.android.gms.common.GoogleApiAvailability
import com.onesignal.common.AndroidUtils
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.device.IDeviceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class GooglePlayServicesUpgradePrompt(
    private val _applicationService: IApplicationService,
    private val _deviceService: IDeviceService,
    private val _configModelStore: ConfigModelStore,
) {
    // Google Play Store might not be installed, ignore exception if so
    private val isGooglePlayStoreInstalled: Boolean
        private get() {
            try {
                val pm = _applicationService.appContext.packageManager
                val info =
                    pm.getPackageInfo(
                        GoogleApiAvailability.GOOGLE_PLAY_SERVICES_PACKAGE,
                        PackageManager.GET_META_DATA,
                    )
                val label = info.applicationInfo?.loadLabel(pm) as String
                return label != "Market"
            } catch (e: PackageManager.NameNotFoundException) {
                // Google Play Store might not be installed, ignore exception if so
            }
            return false
        }

    suspend fun showUpdateGPSDialog() {
        if (!_deviceService.isAndroidDeviceType) {
            return
        }

        if (!isGooglePlayStoreInstalled || _configModelStore.model.disableGMSMissingPrompt || _configModelStore.model.userRejectedGMSUpdate) {
            return
        }

        withContext(Dispatchers.Main) {
            val activity = _applicationService.current ?: return@withContext

            // Load resource strings so a developer can customize this dialog
            val alertBodyText =
                AndroidUtils.getResourceString(
                    activity,
                    "onesignal_gms_missing_alert_text",
                    "To receive push notifications please press 'Update' to enable 'Google Play services'.",
                )
            val alertButtonUpdate =
                AndroidUtils.getResourceString(
                    activity,
                    "onesignal_gms_missing_alert_button_update",
                    "Update",
                )
            val alertButtonSkip =
                AndroidUtils.getResourceString(
                    activity,
                    "onesignal_gms_missing_alert_button_skip",
                    "Skip",
                )
            val alertButtonClose =
                AndroidUtils.getResourceString(
                    activity,
                    "onesignal_gms_missing_alert_button_close",
                    "Close",
                )

            val builder = AlertDialog.Builder(activity)
            builder.setMessage(alertBodyText)
                .setPositiveButton(alertButtonUpdate) { dialog, which -> openPlayStoreToApp(activity) }
                .setNegativeButton(alertButtonSkip) { dialog, which ->
                    _configModelStore.model.userRejectedGMSUpdate = true
                }.setNeutralButton(alertButtonClose, null).create().show()
        }
    }

    // Take the user to the Google Play store to update or enable the Google Play Services app
    private fun openPlayStoreToApp(activity: Activity) {
        try {
            val apiAvailability = GoogleApiAvailability.getInstance()
            val resultCode = apiAvailability.isGooglePlayServicesAvailable(_applicationService.appContext)
            // Send the Intent to trigger opening the store
            val pendingIntent =
                apiAvailability.getErrorResolutionPendingIntent(
                    activity,
                    resultCode,
                    PLAY_SERVICES_RESOLUTION_REQUEST,
                )
            pendingIntent?.send()
        } catch (e: CanceledException) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val PLAY_SERVICES_RESOLUTION_REQUEST = 9000
    }
}
