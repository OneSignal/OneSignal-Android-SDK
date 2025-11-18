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

package com.onesignal.core.internal.permissions

import android.app.Activity
import android.app.AlertDialog
import android.view.WindowManager.BadTokenException
import com.onesignal.core.R
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging

/**
 * A singleton helper which will display the fallback-to-settings alert dialog.
 */
object AlertDialogPrepromptForAndroidSettings {
    interface Callback {
        fun onAccept()

        fun onDecline()
    }

    fun show(
        activity: Activity,
        titlePrefix: String,
        previouslyDeniedPostfix: String,
        callback: Callback,
    ) {
        val titleTemplate = activity.getString(R.string.permission_not_available_title)
        val title = titleTemplate.format(titlePrefix)

        val messageTemplate = activity.getString(R.string.permission_not_available_message)
        val message = messageTemplate.format(previouslyDeniedPostfix)

        // Try displaying the dialog while handling cases where execution is not possible.
        try {
            AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.permission_not_available_open_settings_option) { dialog, which ->
                    callback.onAccept()
                }
                .setNegativeButton(android.R.string.no) { dialog, which ->
                    callback.onDecline()
                }
                .setOnCancelListener {
                    callback.onDecline()
                }
                .show()
        } catch (ex: BadTokenException) {
            // If Android is unable to display the dialog, trigger the onDecline callback to maintain
            // consistency with the behavior when the dialog is canceled or dismissed without a response.
            Logging.log(LogLevel.ERROR, "Alert dialog for Android settings was skipped because the activity was unavailable to display it.")
            callback.onDecline()
        }
    }
}
