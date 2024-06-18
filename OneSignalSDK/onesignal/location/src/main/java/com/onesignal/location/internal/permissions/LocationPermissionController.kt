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

package com.onesignal.location.internal.permissions

import com.onesignal.common.AndroidUtils
import com.onesignal.common.events.EventProducer
import com.onesignal.common.events.IEventNotifier
import com.onesignal.common.threading.WaiterWithValue
import com.onesignal.core.internal.application.ApplicationLifecycleHandlerBase
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.permissions.AlertDialogPrepromptForAndroidSettings
import com.onesignal.core.internal.permissions.IRequestPermissionService
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.location.R

internal interface ILocationPermissionChangedHandler {
    fun onLocationPermissionChanged(enabled: Boolean)
}

internal class LocationPermissionController(
    private val _requestPermission: IRequestPermissionService,
    private val _applicationService: IApplicationService,
) : IRequestPermissionService.PermissionCallback,
    IStartableService,
    IEventNotifier<ILocationPermissionChangedHandler> {
    companion object {
        private const val PERMISSION_TYPE = "LOCATION"
    }

    private val waiter = WaiterWithValue<Boolean>()
    private val events = EventProducer<ILocationPermissionChangedHandler>()
    private var currPermission: String = ""

    override fun start() {
        _requestPermission.registerAsCallback(PERMISSION_TYPE, this)
    }

    suspend fun prompt(
        fallbackToSettings: Boolean,
        androidPermissionString: String,
    ): Boolean {
        currPermission = androidPermissionString
        _requestPermission.startPrompt(
            fallbackToSettings,
            PERMISSION_TYPE,
            androidPermissionString,
            this::class.java,
        )

        // this won't return until onAccept or onReject sends the response on the channel (either
        // through the native prompt or through the fallback)
        return waiter.waitForWake()
    }

    override fun onAccept() {
        waiter.wake(true)
        events.fire { it.onLocationPermissionChanged(true) }
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
            events.fire { it.onLocationPermissionChanged(false) }
        }
    }

    private fun showFallbackAlertDialog(): Boolean {
        val activity = _applicationService.current ?: return false
        AlertDialogPrepromptForAndroidSettings.show(
            activity,
            activity.getString(R.string.location_permission_name_for_title),
            activity.getString(R.string.location_permission_settings_message),
            object : AlertDialogPrepromptForAndroidSettings.Callback {
                override fun onAccept() {
                    // wait for focus to be regained, and check the current permission status.
                    _applicationService.addApplicationLifecycleHandler(
                        object : ApplicationLifecycleHandlerBase() {
                            override fun onFocus(firedOnSubscribe: Boolean) {
                                // Triggered by subscribing, wait for lifecycle callback
                                if (firedOnSubscribe) {
                                    return
                                }
                                super.onFocus(false)
                                _applicationService.removeApplicationLifecycleHandler(this)
                                val hasPermission = AndroidUtils.hasPermission(currPermission, true, _applicationService)
                                waiter.wake(hasPermission)
                                events.fire { it.onLocationPermissionChanged(hasPermission) }
                            }
                        },
                    )
                    NavigateToAndroidSettingsForLocation.show(activity)
                }

                override fun onDecline() {
                    waiter.wake(false)
                    events.fire { it.onLocationPermissionChanged(false) }
                }
            },
        )
        return true
    }

    override fun subscribe(handler: ILocationPermissionChangedHandler) = events.subscribe(handler)

    override fun unsubscribe(handler: ILocationPermissionChangedHandler) = events.subscribe(handler)

    override val hasSubscribers: Boolean
        get() = events.hasSubscribers
}
