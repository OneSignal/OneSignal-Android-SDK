package com.onesignal.notifications.internal.lifecycle.impl

import android.app.Activity
import android.content.Context
import com.onesignal.common.AndroidUtils
import com.onesignal.common.events.CallbackProducer
import com.onesignal.common.events.EventProducer
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.time.ITime
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.notifications.INotificationClickListener
import com.onesignal.notifications.INotificationLifecycleListener
import com.onesignal.notifications.INotificationReceivedEvent
import com.onesignal.notifications.INotificationServiceExtension
import com.onesignal.notifications.INotificationWillDisplayEvent
import com.onesignal.notifications.internal.common.NotificationConstants
import com.onesignal.notifications.internal.common.NotificationGenerationJob
import com.onesignal.notifications.internal.common.NotificationHelper
import com.onesignal.notifications.internal.lifecycle.INotificationLifecycleCallback
import com.onesignal.notifications.internal.lifecycle.INotificationLifecycleEventHandler
import com.onesignal.notifications.internal.lifecycle.INotificationLifecycleService
import org.json.JSONArray
import org.json.JSONObject

internal class NotificationLifecycleService(
    applicationService: IApplicationService,
    private val _time: ITime,
) : INotificationLifecycleService {
    private val intLifecycleHandler = EventProducer<INotificationLifecycleEventHandler>()
    private val intLifecycleCallback = CallbackProducer<INotificationLifecycleCallback>()
    private val extRemoteReceivedCallback = CallbackProducer<INotificationServiceExtension>()
    private val extWillShowInForegroundCallback = EventProducer<INotificationLifecycleListener>()
    private val extOpenedCallback = EventProducer<INotificationClickListener>()
    private val unprocessedOpenedNotifs: ArrayDeque<JSONArray> = ArrayDeque()

    override fun addInternalNotificationLifecycleEventHandler(handler: INotificationLifecycleEventHandler) =
        intLifecycleHandler.subscribe(
            handler,
        )

    override fun removeInternalNotificationLifecycleEventHandler(handler: INotificationLifecycleEventHandler) =
        intLifecycleHandler.unsubscribe(
            handler,
        )

    override fun setInternalNotificationLifecycleCallback(callback: INotificationLifecycleCallback?) = intLifecycleCallback.set(callback)

    override fun addExternalForegroundLifecycleListener(listener: INotificationLifecycleListener) =
        extWillShowInForegroundCallback.subscribe(
            listener,
        )

    override fun removeExternalForegroundLifecycleListener(listener: INotificationLifecycleListener) =
        extWillShowInForegroundCallback.unsubscribe(
            listener,
        )

    override fun addExternalClickListener(callback: INotificationClickListener) {
        extOpenedCallback.subscribe(callback)

        // Ensure we process any queued up notifications that came in prior to this being set.
        if (extOpenedCallback.hasSubscribers && unprocessedOpenedNotifs.any()) {
            for (data in unprocessedOpenedNotifs) {
                val openedResult = NotificationHelper.generateNotificationOpenedResult(data, _time)
                extOpenedCallback.fireOnMain { it.onClick(openedResult) }
            }
        }
    }

    override fun removeExternalClickListener(listener: INotificationClickListener) = extOpenedCallback.unsubscribe(listener)

    init {
        setupNotificationServiceExtension(applicationService.appContext)
    }

    override suspend fun canReceiveNotification(jsonPayload: JSONObject): Boolean {
        var canReceive = true
        intLifecycleCallback.suspendingFire { canReceive = it.canReceiveNotification(jsonPayload) }
        return canReceive
    }

    override suspend fun notificationReceived(notificationJob: NotificationGenerationJob) {
        intLifecycleHandler.suspendingFire { it.onNotificationReceived(notificationJob) }
    }

    override suspend fun canOpenNotification(
        activity: Activity,
        data: JSONObject,
    ): Boolean {
        var canOpen = true
        intLifecycleCallback.suspendingFire { canOpen = it.canOpenNotification(activity, data) }
        return canOpen
    }

    override suspend fun notificationOpened(
        activity: Activity,
        data: JSONArray,
    ) {
        intLifecycleHandler.suspendingFire { it.onNotificationOpened(activity, data) }

        // queue up the opened notification in case the handler hasn't been set yet. Once set,
        // we will immediately fire the handler.
        if (extOpenedCallback.hasSubscribers) {
            val openResult = NotificationHelper.generateNotificationOpenedResult(data, _time)
            extOpenedCallback.fireOnMain { it.onClick(openResult) }
        } else {
            unprocessedOpenedNotifs.add(data)
        }
    }

    override fun externalRemoteNotificationReceived(notificationReceivedEvent: INotificationReceivedEvent) {
        extRemoteReceivedCallback.fire { it.onNotificationReceived(notificationReceivedEvent) }
    }

    override fun externalNotificationWillShowInForeground(willDisplayEvent: INotificationWillDisplayEvent) {
        extWillShowInForegroundCallback.fire { it.onWillDisplay(willDisplayEvent) }
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
        val className = AndroidUtils.getManifestMeta(context, NotificationConstants.EXTENSION_SERVICE_META_DATA_TAG_NAME)

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
            if (clazzInstance is INotificationServiceExtension && !extRemoteReceivedCallback.hasCallback) {
                extRemoteReceivedCallback.set(clazzInstance)
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
