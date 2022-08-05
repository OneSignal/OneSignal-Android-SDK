package com.onesignal.onesignal.notification.internal.generation.impl

import android.content.Context
import com.onesignal.onesignal.core.internal.application.IApplicationService
import com.onesignal.onesignal.core.internal.common.AndroidUtils
import com.onesignal.onesignal.core.internal.time.ITime
import com.onesignal.onesignal.notification.internal.Notification
import com.onesignal.onesignal.notification.internal.common.NotificationConstants
import com.onesignal.onesignal.notification.internal.NotificationReceivedEvent
import com.onesignal.onesignal.notification.internal.data.INotificationDataController
import com.onesignal.onesignal.core.internal.params.IParamsService
import com.onesignal.onesignal.core.internal.logging.Logging
import com.onesignal.onesignal.core.internal.session.ISessionService
import com.onesignal.onesignal.notification.INotificationWillShowInForegroundHandler
import com.onesignal.onesignal.notification.IRemoteNotificationReceivedHandler
import com.onesignal.onesignal.notification.internal.common.NotificationGenerationJob
import com.onesignal.onesignal.notification.internal.display.INotificationDisplayer
import com.onesignal.onesignal.notification.internal.generation.INotificationGenerationProcessor
import com.onesignal.onesignal.notification.internal.lifecycle.INotificationLifecycleService
import com.onesignal.onesignal.notification.internal.summary.INotificationSummaryManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.json.JSONException
import org.json.JSONObject

/**
 * The [NotificationGenerationProcessor] is responsible for driving the displaying of a notification
 * to the user.
 */
internal class NotificationGenerationProcessor(
    private val _applicationService: IApplicationService,
    private val _notificationDisplayer: INotificationDisplayer,
    private val _paramsService: IParamsService,
    private val _dataController: INotificationDataController,
    private val _notificationSummaryManager: INotificationSummaryManager,
    private val _lifecycleService: INotificationLifecycleService,
    private val _sessionService: ISessionService,
    private val _time: ITime,
) : INotificationGenerationProcessor {

    // TODO: Implement callbacks
    val remoteNotificationReceivedHandler: IRemoteNotificationReceivedHandler? = null
    val notificationWillShowInForegroundHandler: INotificationWillShowInForegroundHandler? = null

    override suspend fun processNotificationData(
                    context: Context,
                    androidNotificationId: Int,
                    jsonPayload: JSONObject,
                    isRestoring: Boolean,
                    timestamp: Long) {

        if(!_lifecycleService.canReceiveNotification(jsonPayload)) {
            // Return early, we don't want the extender service or etc. to fire for IAM previews
            return
        }

        var notification = Notification(null, jsonPayload, androidNotificationId, _time)

        // When restoring it will be seen as a duplicate, because we are restoring...
        if (!isRestoring && isDuplicateNotification(notification))
            return

        val notificationJob = NotificationGenerationJob()
        notificationJob.shownTimeStamp = timestamp
        notificationJob.isRestoring = isRestoring
        notificationJob.notification = notification
        notificationJob.jsonPayload = jsonPayload

        var shouldDisplay = true
        var didDisplay = false

        if (remoteNotificationReceivedHandler == null) {
            Logging.warn("remoteNotificationReceivedHandler not setup, displaying normal OneSignal notification")
            shouldDisplay = processHandlerResponse(notificationJob, notification, isRestoring)
                ?: return
        }
        else {
            Logging.info("Fire remoteNotificationReceived")
            val serviceExtensionReceivedEvent = NotificationReceivedEvent(notification)

            try {
                withTimeout(30000L) {
                    remoteNotificationReceivedHandler.remoteNotificationReceived(context, serviceExtensionReceivedEvent)
                }
            } catch (t: Throwable) {
                Logging.error("remoteNotificationReceived throw an exception. Displaying normal OneSignal notification.", t)
            }

            shouldDisplay = processHandlerResponse(notificationJob, serviceExtensionReceivedEvent.notification?.copy(), isRestoring)
                ?: return
        }

        if(shouldDisplay) {
            if (shouldFireForegroundHandlers(notificationJob)) {
                Logging.info("Fire notificationWillShowInForegroundHandler")

                val foregroundReceivedEvent = NotificationReceivedEvent(notificationJob.notification!!)

                try {
                    withTimeout(30000L) {
                        notificationWillShowInForegroundHandler!!.notificationWillShowInForeground(foregroundReceivedEvent)
                    }
                } catch (t: Throwable) {
                    Logging.error("Exception thrown while notification was being processed for display by notificationWillShowInForegroundHandler, showing notification in foreground!", t)
                }

                shouldDisplay = processHandlerResponse(notificationJob, foregroundReceivedEvent.notification?.copy(), isRestoring)
                    ?: return
            }

            if(shouldDisplay ) {
                // display the notification
                // Notification might end not displaying because the channel for that notification has notification disable
                didDisplay = _notificationDisplayer.displayNotification(notificationJob)
            }
        }

        // finish up
        if (!notificationJob.isRestoring) {
            postProcessNotification(notificationJob, false, didDisplay)
        }

        // Delay to prevent CPU spikes
        // Normally more than one notification is restored at a time
        if (isRestoring)
            delay(100)
    }


    /**
     * Process the response to the external handler (either the foreground handler or the service extension).
     *
     * @param notificationJob The notification job covering the context the handler was called under.
     * @param notification The notification that is to be displayed, as determined by the handler.
     * @param isRestoring Whether this notification is being processed because of a restore.
     *
     * @return true if the job should continue display, false if the job should continue but not display, null if processing should stop.
     */
    private suspend fun processHandlerResponse(notificationJob: NotificationGenerationJob, notification: Notification?, isRestoring: Boolean) : Boolean? {
        if (notification != null) {
            val canDisplay = AndroidUtils.isStringNotEmpty(notification.body)
            val withinTtl: Boolean = isNotificationWithinTTL(notification)

            if (canDisplay && withinTtl) {
                // Update the job to use the new notification
                notificationJob.notification = notification

                processCollapseKey(notificationJob)

                var shouldDisplay = shouldDisplayNotification(notificationJob)

                if (shouldDisplay) {
                    notificationJob.isNotificationToDisplay = true;
                    return true
                }

                return false
            }
        }

        // Processing should stop, save the notification as processed to prevent possible duplicate
        // calls from canonical ids.
        if (isRestoring) {
            // If we are not displaying a restored notification make sure we mark it as dismissed
            // This will prevent it from being restored again
            markNotificationAsDismissed(notificationJob)
        } else {
            // indicate the notification job did not display. We process it as "opened" to prevent
            // a duplicate from coming in and us having to process it again.
            notificationJob.isNotificationToDisplay = false
            postProcessNotification(notificationJob, true, false)
        }

        return null
    }

    // If available TTL times comes in seconds, by default is 3 days in seconds
    private fun isNotificationWithinTTL(notification: Notification): Boolean {
        val useTtl = _paramsService.restoreTTLFilter
        if (!useTtl) return true
        val currentTimeInSeconds = _time.currentTimeMillis / 1000
        val sentTime = notification.sentTime
        // If available TTL times comes in seconds, by default is 3 days in seconds
        val ttl = notification.ttl
        return sentTime + ttl > currentTimeInSeconds
    }

    private suspend fun isDuplicateNotification(notification: Notification) : Boolean {
        return _dataController.doesNotificationExist(notification.notificationId)
    }

    private fun shouldDisplayNotification(notificationJob: NotificationGenerationJob): Boolean {
        return notificationJob.hasExtender() || AndroidUtils.isStringNotEmpty(
            notificationJob.jsonPayload!!.optString("alert")
        )
    }

    /**
     * Post process the notification: Save notification, updates Outcomes, and sends Received Receipt if they are enabled.
     */
    private suspend fun postProcessNotification(
        notificationJob: NotificationGenerationJob,
        wasOpened: Boolean,
        wasDisplayed: Boolean
    ) {
        saveNotification(notificationJob, wasOpened)
        if (!wasDisplayed) {
            // Notification channel disable or not displayed
            // save notification as dismissed to avoid user re-enabling channel and notification being displayed due to restore
            markNotificationAsDismissed(notificationJob)
            return
        }

        _lifecycleService.notificationGenerated(notificationJob)
        _sessionService.onNotificationReceived(notificationJob.apiNotificationId)
    }

    // Saving the notification provides the following:
    //   * Prevent duplicates
    //   * Build summary notifications
    //   * Collapse key / id support - Used to lookup the android notification id later
    //   * Redisplay notifications after reboot, upgrade of app, or cold boot after a force kill.
    //   * Future - Public API to get a list of notifications
    private suspend fun saveNotification(notificationJob: NotificationGenerationJob, opened: Boolean) {
        Logging.debug("Saving Notification job: $notificationJob")

        val jsonPayload = notificationJob.jsonPayload!!
        try {
            val customJSON = getCustomJSONObject(jsonPayload)

            val collapseKey: String? = if(jsonPayload.has("collapse_key") && "do_not_collapse" != jsonPayload.optString("collapse_key")) jsonPayload.optString("collapse_key") else null

            // Set expire_time
            val sentTime = jsonPayload.optLong(
                NotificationConstants.GOOGLE_SENT_TIME_KEY,
                _time.currentTimeMillis
            ) / 1000L
            val ttl = jsonPayload.optInt(
                NotificationConstants.GOOGLE_TTL_KEY,
                NotificationConstants.DEFAULT_TTL_IF_NOT_IN_PAYLOAD
            )
            val expireTime = sentTime + ttl

            _dataController.createNotification(
                customJSON.optString("i"),
                jsonPayload.optString("grp"),
                collapseKey,
                notificationJob.isNotificationToDisplay,         // When notification was displayed, count any notifications with duplicated android notification ids as dismissed.
                opened,
                notificationJob.androidId,
                if (notificationJob.title != null) notificationJob.title.toString() else null,
                if (notificationJob.body != null) notificationJob.body.toString() else null,
                expireTime,
                jsonPayload.toString())
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    private suspend fun markNotificationAsDismissed(notifiJob: NotificationGenerationJob) {
        if (!notifiJob.isNotificationToDisplay)
            return

        Logging.debug("Marking restored or disabled notifications as dismissed: $notifiJob")

        val didDismiss = _dataController.markAsDismissed(notifiJob.androidId)

        if(didDismiss) {
            _notificationSummaryManager.updatePossibleDependentSummaryOnDismiss(notifiJob.androidId)
        }
    }

    private suspend fun processCollapseKey(notificationJob: NotificationGenerationJob) {
        if (notificationJob.isRestoring) return
        if (!notificationJob.jsonPayload!!.has("collapse_key") || "do_not_collapse" == notificationJob.jsonPayload!!.optString("collapse_key"))
            return

        val collapseId = notificationJob.jsonPayload!!.optString("collapse_key")

        val androidNotificationId = _dataController.getAndroidIdFromCollapseKey(collapseId)

        if(androidNotificationId != null) {
            notificationJob.notification?.androidNotificationId = androidNotificationId
        }
    }

    @Throws(JSONException::class)
    fun getCustomJSONObject(jsonObject: JSONObject): JSONObject {
        return JSONObject(jsonObject.optString("custom"))
    }

    private fun shouldFireForegroundHandlers(notificationJob: NotificationGenerationJob): Boolean {
        if (!_applicationService.isInForeground) {
            Logging.info("App is in background, show notification")
            return false
        }
        if (notificationWillShowInForegroundHandler == null) {
            Logging.info("No NotificationWillShowInForegroundHandler setup, show notification")
            return false
        }

        // Notification is restored. Don't fire for restored notifications.
        if (notificationJob.isRestoring) {
            Logging.info("Not firing notificationWillShowInForegroundHandler for restored notifications")
            return false
        }
        return true
    }
}