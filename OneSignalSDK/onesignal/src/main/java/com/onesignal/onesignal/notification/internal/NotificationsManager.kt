package com.onesignal.onesignal.notification.internal

import com.onesignal.onesignal.core.internal.application.IApplicationLifecycleHandler
import com.onesignal.onesignal.core.internal.application.IApplicationService
import com.onesignal.onesignal.core.internal.common.*
import com.onesignal.onesignal.core.internal.common.events.CallbackProducer
import com.onesignal.onesignal.core.internal.common.events.EventProducer
import com.onesignal.onesignal.core.internal.common.events.ICallbackProducer
import com.onesignal.onesignal.core.internal.common.events.IEventProducer
import com.onesignal.onesignal.core.internal.device.IDeviceService
import com.onesignal.onesignal.core.internal.logging.LogLevel
import com.onesignal.onesignal.core.internal.logging.Logging
import com.onesignal.onesignal.core.internal.params.IParamsService
import com.onesignal.onesignal.core.internal.service.IStartableService
import com.onesignal.onesignal.core.internal.user.ISubscriptionManager
import com.onesignal.onesignal.notification.*
import com.onesignal.onesignal.notification.internal.permissions.NotificationPermissionController
import com.onesignal.onesignal.notification.internal.registration.IPushRegistrator
import com.onesignal.onesignal.notification.internal.registration.PushRegistratorADM
import com.onesignal.onesignal.notification.internal.registration.PushRegistratorFCM
import com.onesignal.onesignal.notification.internal.registration.PushRegistratorHMS
import com.onesignal.onesignal.notification.internal.restoration.NotificationRestoreWorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * The notification manager is responsible for the management of notifications
 * on the current device (not the user).
 */
internal class NotificationsManager(
    private val _deviceService: IDeviceService,
    private val _applicationService: IApplicationService,
    private val _paramsService: IParamsService,
    private val _notificationPermissionController: NotificationPermissionController,
    private val _pushSubscriber: ISubscriptionManager,
    private val _notificationRestoreWorkManager: NotificationRestoreWorkManager

) : INotificationsManager, IStartableService {
    override var permissionStatus: IPermissionState = PermissionState(false)
    override var unsubscribeWhenNotificationsAreDisabled: Boolean = false
    private var _pushRegistrator: IPushRegistrator? = null
    private val _permissionChangedNotifier: IEventProducer<IPermissionChangedHandler> = EventProducer()
    private val _willShowNotificationNotifier: ICallbackProducer<INotificationWillShowInForegroundHandler> = CallbackProducer()
    private val _notificationOpenedNotifier: ICallbackProducer<INotificationOpenedHandler> = CallbackProducer()

    override suspend fun start() {
        if (_deviceService.isFireOSDeviceType)
            _pushRegistrator = PushRegistratorADM()
        else if (_deviceService.isAndroidDeviceType) {
            if (_deviceService.hasFCMLibrary())
                _pushRegistrator = PushRegistratorFCM(_paramsService, _applicationService, _deviceService)
        } else {
            _pushRegistrator = PushRegistratorHMS(_deviceService)
        }

        // Refresh the notification permissions whenever we come back into focus
        _applicationService.addApplicationLifecycleHandler(object: IApplicationLifecycleHandler {
            override fun onFocus() = runBlocking {
                onRefresh()
            }

            override fun onUnfocused() { }
        })

        onRefresh()
    }

    private suspend fun onRefresh() {
        // ensure all notifications for this app have been restored to the notification panel
        _notificationRestoreWorkManager.beginEnqueueingWork(_applicationService.appContext!!, false)

        val isEnabled = NotificationHelper.areNotificationsEnabled(_applicationService.appContext!!)
        setPermissionStatusAndFire(isEnabled)

        if(isEnabled) {
            registerAndSubscribe()
        }
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

            if(result) {
                registerAndSubscribe()
            }
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

    private suspend fun registerAndSubscribe() {
        // if there's already a subscription, nothing to do.
        if (_pushSubscriber.subscriptions.push != null)
            return;

        val registerResult = _pushRegistrator!!.registerForPush(_applicationService.appContext!!)


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
            _pushSubscriber.addPushSubscription(registerResult.id!!)
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
    }
}
