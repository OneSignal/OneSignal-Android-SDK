package com.onesignal.onesignal.notification.internal

import android.app.Activity
import com.onesignal.onesignal.core.internal.application.IApplicationLifecycleHandler
import com.onesignal.onesignal.core.internal.application.IApplicationService
import com.onesignal.onesignal.core.internal.common.*
import com.onesignal.onesignal.core.internal.common.time.ITime
import com.onesignal.onesignal.core.internal.device.IDeviceService
import com.onesignal.onesignal.core.LogLevel
import com.onesignal.onesignal.core.internal.common.events.*
import com.onesignal.onesignal.core.internal.logging.Logging
import com.onesignal.onesignal.core.internal.params.IParamsChangedHandler
import com.onesignal.onesignal.core.internal.params.IParamsService
import com.onesignal.onesignal.core.internal.startup.IStartableService
import com.onesignal.onesignal.core.user.IUserManager
import com.onesignal.onesignal.notification.*
import com.onesignal.onesignal.notification.internal.analytics.IAnalyticsTracker
import com.onesignal.onesignal.notification.internal.channels.INotificationChannelManager
import com.onesignal.onesignal.notification.internal.common.GenerateNotificationOpenIntentFromPushPayload
import com.onesignal.onesignal.notification.internal.common.NotificationConstants
import com.onesignal.onesignal.notification.internal.common.NotificationGenerationJob
import com.onesignal.onesignal.notification.internal.common.NotificationHelper
import com.onesignal.onesignal.notification.internal.lifecycle.INotificationLifecycleEventHandler
import com.onesignal.onesignal.notification.internal.lifecycle.INotificationLifecycleService
import com.onesignal.onesignal.notification.internal.permissions.INotificationPermissionController
import com.onesignal.onesignal.notification.internal.receivereceipt.IReceiveReceiptWorkManager
import com.onesignal.onesignal.notification.internal.registration.IPushRegistrator
import com.onesignal.onesignal.notification.internal.registration.impl.PushRegistratorADM
import com.onesignal.onesignal.notification.internal.registration.impl.PushRegistratorFCM
import com.onesignal.onesignal.notification.internal.registration.impl.PushRegistratorHMS
import com.onesignal.onesignal.notification.internal.restoration.INotificationRestoreWorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

interface INotificationActivityOpener {
    suspend fun openDestinationActivity(activity: Activity, pushPayloads: JSONArray)
}

interface IPushTokenManager : IEventNotifier<IPushTokenChangedHandler> {
    /** The push token for this device **/
    val pushToken: String?
}

interface IPushTokenChangedHandler {
    fun onPushTokenChanged(pushToken: String?)
}

/**
 * The notification manager is responsible for the management of notifications
 * on the current device (not the user).
 */
internal class NotificationsManager(
    private val _deviceService: IDeviceService,
    private val _applicationService: IApplicationService,
    private val _paramsService: IParamsService,
    private val _notificationPermissionController: INotificationPermissionController,
    private val _userManager: IUserManager,
    private val _notificationRestoreWorkManager: INotificationRestoreWorkManager,
    private val _notificationLifecycleService: INotificationLifecycleService,
    private val _analyticsTracker: IAnalyticsTracker,
    private val _channelManager: INotificationChannelManager,
    private val _receiveReceiptWorkManager: IReceiveReceiptWorkManager,
    private val _time: ITime

) : INotificationsManager,
    IStartableService,
    INotificationActivityOpener,
    IPushTokenManager,
    INotificationLifecycleEventHandler,
    IApplicationLifecycleHandler,
    IParamsChangedHandler {

    override var permissionStatus: IPermissionState = PermissionState(false)
    override var unsubscribeWhenNotificationsAreDisabled: Boolean = false
    override var pushToken: String? = null

    private val _permissionChangedNotifier = EventProducer<IPermissionChangedHandler>()
    private val _willShowNotificationNotifier = CallbackProducer<INotificationWillShowInForegroundHandler>()
    private val _notificationOpenedNotifier = CallbackProducer<INotificationOpenedHandler>()
    private val _pushTokenChangedNotifier = EventProducer<IPushTokenChangedHandler>()

    private val _unprocessedOpenedNotifs: ArrayDeque<JSONArray> = ArrayDeque()

    override fun start() {
        _notificationLifecycleService.subscribe(this)
        _applicationService.addApplicationLifecycleHandler(this)
        _paramsService.subscribe(this)
    }

    override fun onParamsChanged() {
        // Refresh the notification permissions whenever we come back into focus
        _channelManager.processChannelList(_paramsService.notificationChannels)

        suspendifyOnThread {
            retrievePushToken()
            refreshNotificationState()
        }
    }

    override fun onFocus() = suspendifyOnThread {
        refreshNotificationState()
    }

    override fun onUnfocused() { }

    private suspend fun refreshNotificationState() {
        // ensure all notifications for this app have been restored to the notification panel
        _notificationRestoreWorkManager.beginEnqueueingWork(_applicationService.appContext!!, false)

        val isEnabled = NotificationHelper.areNotificationsEnabled(_applicationService.appContext!!)
        setPermissionStatusAndFire(isEnabled)
    }

    override suspend fun requestPermission() : Boolean? {
        Logging.log(LogLevel.DEBUG, "promptForPushPermissionStatus()")

        // TODO: We do not yet handle the case where the activity is shown to the user, the application
        //       is killed, the app is re-opened (showing the permission activity), and the
        //       user makes a decision. Because the app is killed this flow is dead.
        //       NotificationPermissionController does still get the callback, the way it is structured,
        //       so we just need to figure out how to get it to tell us outside of us calling (weird).
        val result = _notificationPermissionController.prompt(true)

        // if result is null that means the user has gone to app settings and may or may not do
        // something there.  However when they come back the application will be brought into
        // focus and our application lifecycle handler will pick up any change that could have
        // occurred.
        if(result != null) {
            setPermissionStatusAndFire(result)
        }

        return true
    }

    private suspend fun setPermissionStatusAndFire(isEnabled: Boolean) {
        val oldPermissionStatus = permissionStatus
        permissionStatus = PermissionState(isEnabled)

        if(oldPermissionStatus.notificationsEnabled != isEnabled) {
            // switch over to the main thread for the firing of the event
            withContext(Dispatchers.Main) {
                val changes = PermissionStateChanges(oldPermissionStatus, permissionStatus)
                _permissionChangedNotifier.fire { it.onPermissionChanged(changes) }
            }
        }
    }

    private suspend fun retrievePushToken() {
        // if there's already a push token, nothing to do.
        if (pushToken != null)
            return;

        val pushRegistrator = if (_deviceService.isFireOSDeviceType)
            PushRegistratorADM()
        else if (_deviceService.isAndroidDeviceType) {
            if (_deviceService.hasFCMLibrary())
                PushRegistratorFCM(_paramsService, _applicationService, _deviceService)
            else
                null
        } else {
            PushRegistratorHMS(_deviceService)
        }

        if (pushRegistrator == null)
            return

        val registerResult = pushRegistrator.registerForPush(_applicationService.appContext!!)

        if (registerResult.status < IPushRegistrator.RegisterStatus.PUSH_STATUS_SUBSCRIBED) {
            // Only allow errored subscribableStatuses if we have never gotten a token.
            //   This ensures the device will not later be marked unsubscribed due to a
            //   any inconsistencies returned by Google Play services.
            // Also do not override a config error status if we got a runtime error
//  TODO          if (OneSignalStateSynchronizer.getRegistrationId() == null &&
//                (OneSignal.subscribableStatus == UserState.PUSH_STATUS_SUBSCRIBED ||
//                        OneSignal.pushStatusRuntimeError(OneSignal.subscribableStatus))
//            ) OneSignal.subscribableStatus = status
        }
//  TODO    else if (OneSignal.pushStatusRuntimeError(OneSignal.subscribableStatus))
//            OneSignal.subscribableStatus = status

        // TODO: What if no result or the push registration fails?
        if (registerResult.status == IPushRegistrator.RegisterStatus.PUSH_STATUS_SUBSCRIBED) {
            pushToken = registerResult.id!!
            _pushTokenChangedNotifier.fire { it.onPushTokenChanged(pushToken) }
        }
    }

    /**
     * Allows you to send notifications from user to user or schedule ones in the future to be delivered
     * to the current device.
     *
     *
     * *Note:* You can only use `include_player_ids` as a targeting parameter from your app.
     * Other target options such as `tags` and `included_segments` require your OneSignal
     * App REST API key which can only be used from your server.
     *
     * @param json Contains notification options, see <a href="https://documentation.onesignal.com/reference#create-notification">OneSignal | Create Notification</a>
     *              POST call for all options.
     * @param callback a {@link PostNotificationResponseHandler} object to receive the request result
     *
     * TODO: CAN WE SUPPLY A NOTIFICATION OBJECT
     */
    override suspend fun postNotification(json: JSONObject) : JSONObject {
        Logging.log(LogLevel.DEBUG, "postNotification(json: $json)")

        //TODO()
        return JSONObject()
    }

    override suspend fun postNotification(json: String) : JSONObject {
        Logging.log(LogLevel.DEBUG, "postNotification(json: $json)")
//        TODO()
        return JSONObject()
    }

    /**
     * Cancels a single OneSignal notification based on its Android notification integer ID. Use
     * instead of Android's [android.app.NotificationManager.cancel}, otherwise the notification will be restored
     * when your app is restarted.
     *
     * @param id
     */
    override fun removeNotification(id: Int) {
        Logging.log(LogLevel.DEBUG, "removeNotification(id: $id)")
    }

    override fun removeGroupedNotifications(group: String) {
        Logging.log(LogLevel.DEBUG, "removeGroupedNotifications(group: $group)")
    }

    /**
     * Removes all OneSignal notifications from the Notification Shade. If you just use
     * [android.app.NotificationManager.cancelAll], OneSignal notifications will be restored when
     * your app is restarted.
     */
    override fun clearAll() {
        Logging.log(LogLevel.DEBUG, "clearAll()")
    }

    override fun subscribe(handler: IPushTokenChangedHandler) = _pushTokenChangedNotifier.subscribe(handler)
    override fun unsubscribe(handler: IPushTokenChangedHandler) = _pushTokenChangedNotifier.unsubscribe(handler)

    /**
     * The [com.onesignal.OSPermissionObserver.onOSPermissionChanged]
     * method will be fired on the passed-in object when a notification permission setting changes.
     * This happens when the user enables or disables notifications for your app from the system
     * settings outside of your app. Disable detection is supported on Android 4.4+
     *
     *
     * *Keep a reference<* - Make sure to hold a reference to your observable at the class level,
     * otherwise it may not fire
     *
     * *Leak Safe* - OneSignal holds a weak reference to your observer so it's guaranteed not to
     * leak your `Activity`
     *
     * @param handler the instance of [com.onesignal.OSPermissionObserver] that you want to process
     *                 the permission changes within
     */
    override fun addPushPermissionHandler(handler: IPermissionChangedHandler) {
        Logging.log(LogLevel.DEBUG, "addPushPermissionHandler(handler: $handler)")
        _permissionChangedNotifier.subscribe(handler)
    }

    override fun addPushPermissionHandler(handler: (IPermissionStateChanges?) -> Unit) {
        Logging.log(LogLevel.DEBUG, "addPushPermissionHandler(handler: $handler)")
        //TODO("Not yet implemented")
    }

    /**
     * Remove a push permission handler that has been previously added.
     *
     * @param handler The previously added handler that should be removed.
     */
    override fun removePushPermissionHandler(handler: IPermissionChangedHandler) {
        Logging.log(LogLevel.DEBUG, "removePushPermissionHandler(handler: $handler)")
        _permissionChangedNotifier.unsubscribe(handler)
    }

    override fun removePushPermissionHandler(handler: (IPermissionStateChanges?) -> Unit) {
        Logging.log(LogLevel.DEBUG, "removePushPermissionHandler(handler: $handler)")
        //TODO("Not yet implemented")
    }

    /**
     * Sets a handler to run before displaying a notification while the app is in focus. Use this handler to read
     * notification data and change it or decide if the notification should show or not.
     *
     * *Note:* this runs after the Notification Service Extension which can be used to modify the notification
     * before showing it.
     *
     * @param handler: The handler that is to be called when the even occurs.
     */
    override fun setNotificationWillShowInForegroundHandler(handler: INotificationWillShowInForegroundHandler) {
        Logging.log(LogLevel.DEBUG, "setNotificationWillShowInForegroundHandler(handler: $handler)")
        _willShowNotificationNotifier.set(handler)
    }

    /**
     * Sets a handler that will run whenever a notification is tapped on by the user.
     *
     * @param handler The handler that is to be called when the event occurs.
     */
    override fun setNotificationOpenedHandler(handler: INotificationOpenedHandler) {
        Logging.log(LogLevel.DEBUG, "setNotificationOpenedHandler(handler: $handler)")
        _notificationOpenedNotifier.set(handler)

        // Ensure we process any queued up notifications that came in prior to this being set.
        if(_notificationOpenedNotifier.hasCallback && _unprocessedOpenedNotifs.any()) {
            for(data in _unprocessedOpenedNotifs) {
                val openedResult = generateNotificationOpenedResult(data)
                _notificationOpenedNotifier.fire { it.notificationOpened(openedResult) }
            }
        }
    }

    override suspend fun onNotificationGenerated(notificationJob: NotificationGenerationJob) {
        _receiveReceiptWorkManager.enqueueReceiveReceipt(notificationJob.apiNotificationId)

        // TODO: Implement
//        OneSignal.getSessionManager().onNotificationReceived(notificationId)

        try {
            val jsonObject = JSONObject(notificationJob.jsonPayload.toString())
            jsonObject.put(NotificationConstants.BUNDLE_KEY_ANDROID_NOTIFICATION_ID, notificationJob.androidId)
            val openResult = generateNotificationOpenedResult(JSONUtils.wrapInJsonArray(jsonObject))

            _analyticsTracker.trackReceivedEvent(openResult.notification.notificationId!!, NotificationHelper.getCampaignNameFromNotification(openResult.notification))
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    override suspend fun onNotificationOpened(activity: Activity, data: JSONArray, notificationId: String) {
        // TODO: Implement call to backend
//        OneSignal.notificationOpenedRESTCall(context, data)

        val openResult = generateNotificationOpenedResult(data)
        _analyticsTracker.trackOpenedEvent(openResult.notification.notificationId!!, NotificationHelper.getCampaignNameFromNotification(openResult.notification))


        // TODO: Implement
//        if (OneSignal.shouldInitDirectSessionFromNotificationOpen(context, data)) {
//            OneSignal.applicationOpenedByNotification(notificationId)
//        }

        openDestinationActivity(activity, data)

        // queue up the opened notification in case the handler hasn't been set yet. Once set,
        // we will immediately fire the handler.
        if(_notificationOpenedNotifier.hasCallback) {
            val openedResult = generateNotificationOpenedResult(data)
            _notificationOpenedNotifier.fire { it.notificationOpened(openedResult) }
        }
        else {
            _unprocessedOpenedNotifs.add(data)
        }
    }

    // Also called for received but OSNotification is extracted from it.
    private fun generateNotificationOpenedResult(jsonArray: JSONArray): NotificationOpenedResult {
        val jsonArraySize = jsonArray.length()
        var firstMessage = true
        val androidNotificationId = jsonArray.optJSONObject(0)
                                             .optInt(NotificationConstants.BUNDLE_KEY_ANDROID_NOTIFICATION_ID)
        val groupedNotifications: MutableList<Notification> = ArrayList()
        var actionSelected: String? = null
        var payload: JSONObject? = null

        for (i in 0 until jsonArraySize) {
            try {
                payload = jsonArray.getJSONObject(i)
                if (actionSelected == null && payload.has(NotificationConstants.GENERATE_NOTIFICATION_BUNDLE_KEY_ACTION_ID))
                    actionSelected = payload.optString(NotificationConstants.GENERATE_NOTIFICATION_BUNDLE_KEY_ACTION_ID, null)

                if (firstMessage)
                    firstMessage = false
                else {
                    groupedNotifications.add(Notification(payload, _time))
                }

            } catch (t: Throwable) {
                Logging.error("Error parsing JSON item $i/$jsonArraySize for callback.", t)
            }
        }

        val actionType =
            if (actionSelected != null) INotificationAction.ActionType.ActionTaken else INotificationAction.ActionType.Opened
        val notificationAction = NotificationAction(actionType, actionSelected)

        val notification = Notification(groupedNotifications, payload!!, androidNotificationId, _time)
        return NotificationOpenedResult(notification, notificationAction)
    }

    override suspend fun openDestinationActivity(activity: Activity, pushPayloads: JSONArray) {
        try {
            // Always use the top most notification if user tapped on the summary notification
            val firstPayloadItem = pushPayloads.getJSONObject(0)
            //val isHandled = _notificationLifecycleService.canOpenNotification(activity, firstPayloadItem)
            val intentGenerator = GenerateNotificationOpenIntentFromPushPayload.create(activity, firstPayloadItem)

            val intent = intentGenerator.getIntentVisible()
            if (intent != null) {
                Logging.info("SDK running startActivity with Intent: $intent")
                activity.startActivity(intent)
            } else {
                Logging.info("SDK not showing an Activity automatically due to it's settings.")
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }
}
