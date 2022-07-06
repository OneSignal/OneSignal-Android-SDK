package com.onesignal.onesignal.internal.notification.work

import android.content.Context
import com.onesignal.onesignal.internal.common.AndroidUtils
import com.onesignal.onesignal.internal.common.time.ITime
import com.onesignal.onesignal.internal.notification.Notification
import com.onesignal.onesignal.internal.notification.NotificationReceivedEvent
import com.onesignal.onesignal.internal.params.IParamsService
import com.onesignal.onesignal.logging.Logging
import com.onesignal.onesignal.notification.IRemoteNotificationReceivedHandler
import org.json.JSONObject

internal class NotificationController(
            private val _notificationProcessor: NotificationBundleProcessor,
            val notificationJob: NotificationGenerationJob,
            private val _paramsService: IParamsService,
            private val _time: ITime,
            private var isRestoring: Boolean = false,
            var isFromBackgroundLogic: Boolean = false) {

    constructor(
        notificationProcessor: NotificationBundleProcessor,
        context: Context,
        notification: Notification,
        jsonPayload: JSONObject,
        restoring: Boolean,
        fromBackgroundLogic: Boolean,
        timestamp: Long,
        paramsService: IParamsService,
        time: ITime,
    ) : this(notificationProcessor, createNotificationJobFromCurrent(context, notification, jsonPayload, restoring, timestamp), paramsService, time, restoring, fromBackgroundLogic)

    /**
     * Called from [OSNotificationReceivedEvent.complete] class
     * If the notification modified by the user is null, the notification will be silent, otherwise will be displayed
     * <br></br><br></br>
     * @param originalNotification the notification received
     * @param notification the notification sent by the user, might be modified
     * @see OSNotificationReceivedEvent.complete
     */
    fun processNotification(originalNotification: Notification, notification: Notification?) {
        if (notification != null) {
            val display = AndroidUtils.isStringNotEmpty(notification.body)
            val withinTtl = isNotificationWithinTTL
            if (display && withinTtl) {
                // Set modified notification
                notificationJob.notification = notification
                _notificationProcessor.processJobForDisplay(this, isFromBackgroundLogic)
            } else {
                // Save as processed to prevent possible duplicate calls from canonical ids
                notDisplayNotificationLogic(originalNotification)
            }
            // Delay to prevent CPU spikes
            // Normally more than one notification is restored at a time
            if (isRestoring) AndroidUtils.sleep(100)
        } else {
            notDisplayNotificationLogic(originalNotification)
        }
    }

    private fun notDisplayNotificationLogic(originalNotification: Notification) {
        notificationJob.notification = originalNotification
        // Save as processed to prevent possible duplicate calls from canonical ids
        if (isRestoring) {
            // If we are not displaying a restored notification make sure we mark it as dismissed
            // This will prevent it from being restored again
            _notificationProcessor.markNotificationAsDismissed(notificationJob)
        } else {
            // indicate the notification job did not display
            notificationJob.isNotificationToDisplay = false
            _notificationProcessor.processNotification(notificationJob, true, false)
            _notificationProcessor.handleNotificationReceived(notificationJob)
        }
    }

    // If available TTL times comes in seconds, by default is 3 days in seconds
    val isNotificationWithinTTL: Boolean
        get() {
            val useTtl = _paramsService.restoreTTLFilter
            if (!useTtl) return true
            val currentTimeInSeconds = _time.currentTimeMillis / 1000
            val sentTime = notificationJob.notification.sentTime
            // If available TTL times comes in seconds, by default is 3 days in seconds
            val ttl = notificationJob.notification.ttl
            return sentTime + ttl > currentTimeInSeconds
        }

    val notificationReceivedEvent: NotificationReceivedEvent
        get() = NotificationReceivedEvent(this, notificationJob.notification)

    override fun toString(): String {
        return "OSNotificationController{" +
                "notificationJob=" + notificationJob +
                ", isRestoring=" + isRestoring +
                ", isBackgroundLogic=" + isFromBackgroundLogic +
                '}'
    }

    companion object {
        // The extension service app AndroidManifest.xml meta data tag key name
        private const val EXTENSION_SERVICE_META_DATA_TAG_NAME =
            "com.onesignal.NotificationServiceExtension"
        const val GOOGLE_SENT_TIME_KEY = "google.sent_time"
        const val GOOGLE_TTL_KEY = "google.ttl"

        /**
         * Using current [NotificationController] class attributes, builds a [OSNotificationGenerationJob]
         * instance and returns it
         * <br></br><br></br>
         * @see OSNotificationGenerationJob
         */
        private fun createNotificationJobFromCurrent(
            context: Context,
            notification: Notification,
            jsonPayload: JSONObject,
            isRestoring: Boolean,
            timestamp: Long
        ): NotificationGenerationJob {
            val notificationJob = NotificationGenerationJob(context)
            notificationJob.jsonPayload = jsonPayload
            notificationJob.shownTimeStamp = timestamp
            notificationJob.isRestoring = isRestoring
            notificationJob.notification = notification
            return notificationJob
        }

        /**
         * In addition to using the setters to set all of the handlers you can also create your own implementation
         * within a separate class and give your AndroidManifest.xml a special meta data tag
         * The meta data tag looks like this:
         * <meta-data android:name="com.onesignal.NotificationServiceExtension" android:value="com.company.ExtensionService"></meta-data>
         * <br></br><br></br>
         * There is only one way to implement the [OneSignal.OSRemoteNotificationReceivedHandler]
         * <br></br><br></br>
         * In the case of the [OneSignal.OSNotificationWillShowInForegroundHandler]
         * there are also setters for these handlers. So why create this new class and implement
         * the same handlers, won't they just overwrite each other?
         * No, the idea here is to keep track of two separate handlers and keep them both
         * 100% optional. The extension handlers are set using the class implementations and the app
         * handlers set through the setter methods.
         * The extension handlers will always be called first and then bubble to the app handlers
         * <br></br><br></br>
         * @see OneSignal.OSRemoteNotificationReceivedHandler
         */
        fun setupNotificationServiceExtension(context: Context) {
            val className = AndroidUtils.getManifestMeta(context, EXTENSION_SERVICE_META_DATA_TAG_NAME)

            // No meta data containing extension service class name
            if (className == null) {
                Logging.verbose("No class found, not setting up OSRemoteNotificationReceivedHandler")
                return
            }

            Logging.verbose("Found class: $className, attempting to call constructor")

            // Pass an instance of the given class to set any overridden handlers
            try {
                val clazz = Class.forName(className)
                val clazzInstance = clazz.newInstance()
                // Make sure a OSRemoteNotificationReceivedHandler exists and remoteNotificationReceivedHandler has not been set yet
                if (clazzInstance is IRemoteNotificationReceivedHandler) {
                    // TODO: Set this into ?? so it will be used by NotificationWorkManager and ??
//                    OneSignal.setRemoteNotificationReceivedHandler(clazzInstance)
                }
            } catch (e: IllegalAccessException) {
                e.printStackTrace()
            } catch (e: InstantiationException) {
                e.printStackTrace()
            } catch (e: ClassNotFoundException) {
                e.printStackTrace()
            }
        }
    }
}