package com.onesignal.sdktest.application

import android.os.StrictMode
import androidx.multidex.MultiDexApplication
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel
import com.onesignal.sdktest.util.LogManager
import com.onesignal.sdktest.util.LogLevel as AppLogLevel
import com.onesignal.inAppMessages.IInAppMessageClickEvent
import com.onesignal.inAppMessages.IInAppMessageClickListener
import com.onesignal.inAppMessages.IInAppMessageDidDismissEvent
import com.onesignal.inAppMessages.IInAppMessageDidDisplayEvent
import com.onesignal.inAppMessages.IInAppMessageLifecycleListener
import com.onesignal.inAppMessages.IInAppMessageWillDismissEvent
import com.onesignal.inAppMessages.IInAppMessageWillDisplayEvent
import com.onesignal.notifications.IDisplayableNotification
import com.onesignal.notifications.INotificationClickEvent
import com.onesignal.notifications.INotificationClickListener
import com.onesignal.notifications.INotificationLifecycleListener
import com.onesignal.notifications.INotificationWillDisplayEvent
import com.onesignal.sdktest.R
import com.onesignal.sdktest.data.network.OneSignalService
import com.onesignal.sdktest.util.SharedPreferenceUtil
import com.onesignal.sdktest.util.TooltipHelper
import com.onesignal.user.state.IUserStateObserver
import com.onesignal.user.state.UserChangedState

class MainApplication : MultiDexApplication() {

    companion object {
        private const val TAG = "OneSignalExample"
        private const val SLEEP_TIME_TO_MIMIC_ASYNC_OPERATION = 2000L
    }

    init {
        // Run strict mode to surface any potential issues easier
        StrictMode.enableDefaults()
    }

    override fun onCreate() {
        super.onCreate()
        
        OneSignal.Debug.logLevel = LogLevel.VERBOSE
        
        // Add SDK log listener BEFORE init to capture all SDK logs in UI
        OneSignal.Debug.addLogListener { event ->
            val level = when (event.level) {
                LogLevel.VERBOSE, LogLevel.DEBUG -> AppLogLevel.DEBUG
                LogLevel.INFO -> AppLogLevel.INFO
                LogLevel.WARN -> AppLogLevel.WARN
                LogLevel.ERROR, LogLevel.FATAL -> AppLogLevel.ERROR
                LogLevel.NONE -> return@addLogListener
            }
            LogManager.log("SDK", event.entry, level)
        }

        // Get or set the OneSignal App ID
        var appId = SharedPreferenceUtil.getOneSignalAppId(this)
        if (appId == null) {
            appId = getString(R.string.onesignal_app_id)
            SharedPreferenceUtil.cacheOneSignalAppId(this, appId)
        }

        // Initialize OneSignal Service with app ID and REST API key
        OneSignalService.setAppId(appId)
        
        // Initialize tooltip helper
        TooltipHelper.init(this)

        // Initialize OneSignal on main thread (required)
        OneSignal.initWithContext(this, appId)
        LogManager.i(TAG, "OneSignal init completed")

        // Set up all OneSignal listeners
        setupOneSignalListeners()
        
        // Note: Notification permission is automatically requested when MainActivity loads.
        // This ensures the prompt appears after the user sees the app UI.
    }

    private fun setupOneSignalListeners() {
        OneSignal.InAppMessages.addLifecycleListener(object : IInAppMessageLifecycleListener {
            override fun onWillDisplay(event: IInAppMessageWillDisplayEvent) {
                LogManager.d(TAG, "onWillDisplayInAppMessage")
            }

            override fun onDidDisplay(event: IInAppMessageDidDisplayEvent) {
                LogManager.d(TAG, "onDidDisplayInAppMessage")
            }

            override fun onWillDismiss(event: IInAppMessageWillDismissEvent) {
                LogManager.d(TAG, "onWillDismissInAppMessage")
            }

            override fun onDidDismiss(event: IInAppMessageDidDismissEvent) {
                LogManager.d(TAG, "onDidDismissInAppMessage")
            }
        })

        OneSignal.InAppMessages.addClickListener(object : IInAppMessageClickListener {
            override fun onClick(event: IInAppMessageClickEvent) {
                LogManager.d(TAG, "IInAppMessageClickListener.onClick")
            }
        })

        OneSignal.Notifications.addClickListener(object : INotificationClickListener {
            override fun onClick(event: INotificationClickEvent) {
                LogManager.d(TAG, "INotificationClickListener.onClick fired with event: $event")
            }
        })

        OneSignal.Notifications.addForegroundLifecycleListener(object : INotificationLifecycleListener {
            override fun onWillDisplay(event: INotificationWillDisplayEvent) {
                LogManager.d(TAG, "INotificationLifecycleListener.onWillDisplay fired with event: $event")

                val notification: IDisplayableNotification = event.notification

                // Prevent OneSignal from displaying the notification immediately on return.
                // Spin up a new thread to mimic some asynchronous behavior.
                event.preventDefault()
                Thread {
                    try {
                        Thread.sleep(SLEEP_TIME_TO_MIMIC_ASYNC_OPERATION)
                    } catch (ignored: InterruptedException) {
                    }
                    notification.display()
                }.start()
            }
        })

        OneSignal.User.addObserver(object : IUserStateObserver {
            override fun onUserStateChange(state: UserChangedState) {
                LogManager.i(TAG, "User state changed: onesignalId=${state.current.onesignalId}, externalId=${state.current.externalId}")
            }
        })

        // Restore cached states
        OneSignal.InAppMessages.paused = SharedPreferenceUtil.getCachedInAppMessagingPausedStatus(this)
        OneSignal.Location.isShared = SharedPreferenceUtil.getCachedLocationSharedStatus(this)
    }
}
