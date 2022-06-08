package com.onesignal.onesignal.internal.push

import com.onesignal.*
import com.onesignal.onesignal.notification.INotificationReceivedEvent
import org.json.JSONException
import org.json.JSONObject

class NotificationReceivedEvent internal constructor(
    private val controller: OSNotificationController,
    override val notification: OSNotification
) : INotificationReceivedEvent {
    private val timeoutHandler: OSTimeoutHandler
    private val timeoutRunnable: Runnable

    // Used to toggle when complete is called so it can not be called more than once
    private var isComplete = false

    /**
     * Method to continue with notification processing.
     * User must call complete within 25 seconds or the original notification will be displayed.
     *
     * @param notification can be null to omit displaying the notification,
     * or OSMutableNotification to modify the notification to display
     */
    @Synchronized
    override fun complete(notification: OSNotification?) {
        timeoutHandler.destroyTimeout(timeoutRunnable)
        if (isComplete) {
            OneSignal.onesignalLog(
                OneSignal.LOG_LEVEL.DEBUG,
                "OSNotificationReceivedEvent already completed"
            )
            return
        }
        isComplete = true
        if (isRunningOnMainThread) {
            Thread({ processNotification(notification) }, "OS_COMPLETE_NOTIFICATION").start()
            return
        }
        processNotification(notification)
    }

    private fun processNotification(notification: OSNotification?) {
        // Pass copies to controller, to avoid modifying objects accessed by the user
        controller.processNotification(this.notification.copy(), notification?.copy())
    }

    fun toJSONObject(): JSONObject {
        val mainObj = JSONObject()
        try {
            mainObj.put("notification", notification.toJSONObject())
            mainObj.put("isComplete", isComplete)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return mainObj
    }

    override fun toString(): String {
        return "OSNotificationReceivedEvent{" +
                "isComplete=" + isComplete +
                ", notification=" + notification +
                '}'
    }

    companion object {
        // Timeout time in seconds before auto calling
        private const val PROCESS_NOTIFICATION_TIMEOUT = 25 * 1000L
        val isRunningOnMainThread: Boolean
            get() = OSUtils.isRunningOnMainThread()
    }

    init {
        timeoutHandler = OSTimeoutHandler.getTimeoutHandler()
        timeoutRunnable = Runnable {
            OneSignal.Log(
                OneSignal.LOG_LEVEL.DEBUG,
                "Running complete from OSNotificationReceivedEvent timeout runnable!"
            )
            complete(notification)
        }
        timeoutHandler.startTimeout(PROCESS_NOTIFICATION_TIMEOUT, timeoutRunnable)
    }
}