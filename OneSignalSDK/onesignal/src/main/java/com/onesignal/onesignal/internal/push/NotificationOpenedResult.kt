/**
 * Modified MIT License
 *
 * Copyright 2020 OneSignal
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
package com.onesignal.onesignal.internal.push

import com.onesignal.OSNotification
import com.onesignal.OSNotificationAction
import com.onesignal.OneSignal.EntryStateListener
import com.onesignal.OSTimeoutHandler
import com.onesignal.OneSignal
import org.json.JSONException
import com.onesignal.onesignal.notification.INotificationOpenedResult
import org.json.JSONObject

/**
 * The information returned from a notification the user received.
 * <br></br><br></br>
 * [.notification] - Notification the user received
 * <br></br>
 * [.action] - The action the user took on the notification
 */
class NotificationOpenedResult(override val notification: OSNotification, override val action: OSNotificationAction) :
    EntryStateListener, INotificationOpenedResult {
    private val timeoutHandler: OSTimeoutHandler
    private val timeoutRunnable: Runnable

    // Used to toggle when complete is called so it can not be called more than once
    private var isComplete = false

    /**
     * Method indicating OneSignal that application was opened by user.
     *
     * @param opened true if application was opened under the OSNotificationOpenedHandler handler, false otherwise
     */
    private fun complete(opened: Boolean) {
        OneSignal.onesignalLog(
            OneSignal.LOG_LEVEL.DEBUG,
            "OSNotificationOpenedResult complete called with opened: $opened"
        )
        timeoutHandler.destroyTimeout(timeoutRunnable)
        if (isComplete) {
            OneSignal.onesignalLog(
                OneSignal.LOG_LEVEL.DEBUG,
                "OSNotificationOpenedResult already completed"
            )
            return
        }
        isComplete = true
        if (opened) OneSignal.applicationOpenedByNotification(notification.notificationId)
        OneSignal.removeEntryStateListener(this)
    }

    override fun onEntryStateChange(appEntryState: OneSignal.AppEntryAction) {
        OneSignal.onesignalLog(
            OneSignal.LOG_LEVEL.DEBUG,
            "OSNotificationOpenedResult onEntryStateChange called with appEntryState: $appEntryState"
        )
        // If application changed state from closed then application was started from an open notification click
        complete(OneSignal.AppEntryAction.APP_CLOSE == appEntryState)
    }

    @Deprecated("As of release 3.4.1, replaced by {@link #toJSONObject()}")
    fun stringify(): String {
        val mainObj = JSONObject()
        try {
            mainObj.put("action", action.toJSONObject())
            mainObj.put("notification", notification.toJSONObject())
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return mainObj.toString()
    }

    fun toJSONObject(): JSONObject {
        val mainObj = JSONObject()
        try {
            mainObj.put("action", action.toJSONObject())
            mainObj.put("notification", notification.toJSONObject())
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return mainObj
    }

    override fun toString(): String {
        return "OSNotificationOpenedResult{" +
                "notification=" + notification +
                ", action=" + action +
                ", isComplete=" + isComplete +
                '}'
    }

    companion object {
        // Timeout time in seconds before ignoring opened track
        private const val PROCESS_NOTIFICATION_TIMEOUT = 5 * 1000L
    }

    init {

        // Configure 5 second timeout - max time we expect the application to call onResume
        // User can disable OneSignal application open by setting on the manifest:
        // <meta-data android:name="com.onesignal.NotificationOpened.DEFAULT" android:value="DISABLE" />
        // User can also disable OneSignal from opening the url on default webview setting on the manifest:
        // <meta-data android:name="com.onesignal.suppressLaunchURLs" android:value="true" />
        // If the user does this, we need to identify if the notification click was the way the user come back to the application (Session tracking)
        timeoutHandler = OSTimeoutHandler.getTimeoutHandler()
        timeoutRunnable = Runnable {
            OneSignal.Log(
                OneSignal.LOG_LEVEL.DEBUG,
                "Running complete from OSNotificationOpenedResult timeout runnable!"
            )
            complete(false)
        }
        // This timer is needed for tracking application coming from background or swiped away when user click on notification
        timeoutHandler.startTimeout(PROCESS_NOTIFICATION_TIMEOUT, timeoutRunnable)
    }
}