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

package com.onesignal.onesignal.location.internal.permissions

import com.onesignal.R
import com.onesignal.onesignal.core.internal.application.IApplicationService
import com.onesignal.onesignal.core.internal.common.suspendifyOnThread
import com.onesignal.onesignal.core.internal.permissions.IRequestPermissionService
import com.onesignal.onesignal.core.internal.permissions.impl.AlertDialogPrepromptForAndroidSettings
import com.onesignal.onesignal.core.internal.startup.IStartableService
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking

internal class LocationPermissionController(
    private val _application: IApplicationService,
    private val _requestPermission: IRequestPermissionService,
) : IRequestPermissionService.PermissionCallback,
    IStartableService {
    companion object {
        private const val PERMISSION_TYPE = "LOCATION"
    }

    private val _commChannel = Channel<Boolean?>()

    override fun start() {
        _requestPermission.registerAsCallback(PERMISSION_TYPE, this)
    }

    suspend fun prompt(fallbackToSettings: Boolean, androidPermissionString: String, ) : Boolean? {
        _requestPermission.startPrompt(
            fallbackToSettings,
            PERMISSION_TYPE,
            androidPermissionString,
            this::class.java
        )

        // this won't return until onAccept or onReject sends the response on the channel (either
        // through the native prompt or through the fallback)
        return _commChannel.receive()
    }

    override fun onAccept() {
        suspendifyOnThread {
            _commChannel.send(true)
        }
    }

    override fun onReject(fallbackToSettings: Boolean) {
        val fallbackShown =
            if (fallbackToSettings)
                showFallbackAlertDialog()
            else
                false

        if (!fallbackShown) {
            suspendifyOnThread {
                _commChannel.send(false)
            }
        }
    }

    private fun showFallbackAlertDialog() : Boolean {
        val activity = _application.current ?: return false
        AlertDialogPrepromptForAndroidSettings.show(
            activity,
            activity.getString(R.string.location_permission_name_for_title),
            activity.getString(R.string.location_permission_settings_message),
            object : AlertDialogPrepromptForAndroidSettings.Callback {
                override fun onAccept() {
                    NavigateToAndroidSettingsForLocation.show(activity)
                    runBlocking {
                        _commChannel.send(null)
                    }
                }
                override fun onDecline() {
                    runBlocking {
                        _commChannel.send(false)
                    }
                }
            }
        )
        return true
    }
}
