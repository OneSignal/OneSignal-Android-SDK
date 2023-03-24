/**
 * Modified MIT License
 *
 * Copyright 2022 OneSignal
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * 1. The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * 2. All copies of substantial portions of the Software may only be used in connection
 * with services provided by OneSignal.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.onesignal

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

object NotificationPermissionController : PermissionsActivity.PermissionCallback {
    private const val PERMISSION_TYPE = "NOTIFICATION"
    private const val ANDROID_PERMISSION_STRING = "android.permission.POST_NOTIFICATIONS"

    private val callbacks:
        MutableSet<OneSignal.PromptForPushNotificationPermissionResponseHandler> = HashSet()
    private var awaitingForReturnFromSystemSettings = false

    init {
        PermissionsActivity.registerAsCallback(PERMISSION_TYPE, this)
    }

    @ChecksSdkIntAtLeast(api = 33)
    val supportsNativePrompt =
        Build.VERSION.SDK_INT > 32 &&
            OSUtils.getTargetSdkVersion(OneSignal.appContext) > 32

    fun prompt(
        fallbackToSettings: Boolean,
        callback: OneSignal.PromptForPushNotificationPermissionResponseHandler?
    ) {
        if (callback != null) callbacks.add(callback)

        if (notificationsEnabled()) {
            fireCallBacks(true)
            return
        }

        if (!supportsNativePrompt) {
            if (fallbackToSettings) {
                showFallbackAlertDialog()
            } else {
                fireCallBacks(false)
            }
            return
        }

        PermissionsActivity.startPrompt(
            fallbackToSettings,
            PERMISSION_TYPE,
            ANDROID_PERMISSION_STRING,
            this::class.java
        )
    }

    override fun onAccept() {
        OneSignal.refreshNotificationPermissionState()
        fireCallBacks(true)
    }

    override fun onReject(fallbackToSettings: Boolean) {
        val fallbackShown =
            if (fallbackToSettings) {
                showFallbackAlertDialog()
            } else {
                false
            }
        if (!fallbackShown) fireCallBacks(false)
    }

    // Returns true if dialog was shown
    private fun showFallbackAlertDialog(): Boolean {
        val activity = OneSignal.getCurrentActivity() ?: return false
        AlertDialogPrepromptForAndroidSettings.show(
            activity,
            activity.getString(R.string.notification_permission_name_for_title),
            activity.getString(R.string.notification_permission_settings_message),
            object : AlertDialogPrepromptForAndroidSettings.Callback {
                override fun onAccept() {
                    NavigateToAndroidSettingsForNotifications.show(activity)
                    awaitingForReturnFromSystemSettings = true
                }
                override fun onDecline() {
                    fireCallBacks(false)
                }
            }
        )
        return true
    }

    // Fires callbacks and clears them to ensure each is only called once.
    private fun fireCallBacks(accepted: Boolean) {
        callbacks.forEach { it.response(accepted) }
        callbacks.clear()
    }

    fun onAppForegrounded() {
        if (!awaitingForReturnFromSystemSettings) return
        awaitingForReturnFromSystemSettings = false
        fireCallBacks(notificationsEnabled())
    }

    private fun notificationsEnabled() = OSUtils.areNotificationsEnabled(OneSignal.appContext)
}
