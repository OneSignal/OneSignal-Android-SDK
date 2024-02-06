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

package com.onesignal.notifications.internal.permissions.impl

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import com.onesignal.common.AndroidUtils
import com.onesignal.common.events.EventProducer
import com.onesignal.common.threading.WaiterWithValue
import com.onesignal.core.internal.application.ApplicationLifecycleHandlerBase
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.permissions.AlertDialogPrepromptForAndroidSettings
import com.onesignal.core.internal.permissions.IRequestPermissionService
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.notifications.R
import com.onesignal.notifications.internal.common.NotificationHelper
import com.onesignal.notifications.internal.permissions.INotificationPermissionChangedHandler
import com.onesignal.notifications.internal.permissions.INotificationPermissionController

internal class NotificationPermissionController(
    private val _application: IApplicationService,
    private val _requestPermission: IRequestPermissionService,
    private val _applicationService: IApplicationService,
    private val _preferenceService: IPreferencesService,
) : IRequestPermissionService.PermissionCallback,
    INotificationPermissionController {
    private val waiter = WaiterWithValue<Boolean>()
    private val events = EventProducer<INotificationPermissionChangedHandler>()

    override val canRequestPermission: Boolean
        get() =
            !_preferenceService.getBool(
                PreferenceStores.ONESIGNAL,
                "${PreferenceOneSignalKeys.PREFS_OS_USER_RESOLVED_PERMISSION_PREFIX}$ANDROID_PERMISSION_STRING",
                false,
            )!!

    init {
        _requestPermission.registerAsCallback(PERMISSION_TYPE, this)
    }

    @ChecksSdkIntAtLeast(api = 33)
    val supportsNativePrompt =
        Build.VERSION.SDK_INT > 32 &&
            AndroidUtils.getTargetSdkVersion(_application.appContext) > 32

    /**
     * Prompt the user for notification permission.  Note it is possible the application
     * will be killed while the permission prompt is being displayed to the user. When the
     * app restarts it will begin with the permission prompt.  In this case this suspending
     * function has been killed as well, the permission callbacks should be used to cover
     * that case.
     *
     * @return true if permissions are enabled. False if they are not enabled, null if the user
     * was directed to the permission settings and could not be determined at this time. When this
     * does happen, the app will detect the permissions on app focus and drive permission callbacks
     * to notify of the status.
     */
    override suspend fun prompt(fallbackToSettings: Boolean): Boolean {
        if (notificationsEnabled()) {
            return true
        }

        if (supportsNativePrompt) {
            _requestPermission.startPrompt(
                fallbackToSettings,
                PERMISSION_TYPE,
                ANDROID_PERMISSION_STRING,
                this::class.java,
            )
        } else if (fallbackToSettings) {
            showFallbackAlertDialog()
        } else {
            return false
        }

        // this won't return until onAccept or onReject sends the response on the channel (either
        // through the native prompt or through the fallback)
        return waiter.waitForWake()
    }

    override fun subscribe(handler: INotificationPermissionChangedHandler) = events.subscribe(handler)

    override fun unsubscribe(handler: INotificationPermissionChangedHandler) = events.subscribe(handler)

    override val hasSubscribers: Boolean
        get() = events.hasSubscribers

    override fun onAccept() {
        waiter.wake(true)
        events.fire { it.onNotificationPermissionChanged(true) }
    }

    override fun onReject(fallbackToSettings: Boolean) {
        val fallbackShown =
            if (fallbackToSettings) {
                showFallbackAlertDialog()
            } else {
                false
            }

        if (!fallbackShown) {
            waiter.wake(false)
            events.fire { it.onNotificationPermissionChanged(false) }
        }
    }

    // Returns true if dialog was shown
    private fun showFallbackAlertDialog(): Boolean {
        val activity = _application.current ?: return false

        AlertDialogPrepromptForAndroidSettings.show(
            activity,
            activity.getString(R.string.notification_permission_name_for_title),
            activity.getString(R.string.notification_permission_settings_message),
            object : AlertDialogPrepromptForAndroidSettings.Callback {
                override fun onAccept() {
                    // wait for focus to be regained, and check the current permission status.
                    _applicationService.addApplicationLifecycleHandler(
                        object : ApplicationLifecycleHandlerBase() {
                            override fun onFocus() {
                                super.onFocus()
                                _applicationService.removeApplicationLifecycleHandler(this)
                                val hasPermission = AndroidUtils.hasPermission(ANDROID_PERMISSION_STRING, true, _applicationService)
                                waiter.wake(hasPermission)
                                events.fire { it.onNotificationPermissionChanged(hasPermission) }
                            }
                        },
                    )
                    NavigateToAndroidSettingsForNotifications.show(activity)
                }

                override fun onDecline() {
                    waiter.wake(false)
                    events.fire { it.onNotificationPermissionChanged(false) }
                }
            },
        )
        return true
    }

    private fun notificationsEnabled() = NotificationHelper.areNotificationsEnabled(_application.appContext)

    companion object {
        private const val PERMISSION_TYPE = "NOTIFICATION"
        private const val ANDROID_PERMISSION_STRING = "android.permission.POST_NOTIFICATIONS"
    }
}
