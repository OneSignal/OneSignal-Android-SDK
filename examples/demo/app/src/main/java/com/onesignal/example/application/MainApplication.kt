package com.onesignal.example.application

import android.os.StrictMode
import android.util.Log
import androidx.multidex.MultiDexApplication
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel
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
import com.onesignal.example.BuildConfig
import com.onesignal.example.data.network.OneSignalService
import com.onesignal.example.util.SharedPreferenceUtil
import com.onesignal.example.util.TooltipHelper
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

        // Forward SDK logs to logcat so they appear alongside app logs in Android Studio.
        OneSignal.Debug.addLogListener { event ->
            when (event.level) {
                LogLevel.VERBOSE -> Log.v("OneSignalSDK", event.entry)
                LogLevel.DEBUG -> Log.d("OneSignalSDK", event.entry)
                LogLevel.INFO -> Log.i("OneSignalSDK", event.entry)
                LogLevel.WARN -> Log.w("OneSignalSDK", event.entry)
                LogLevel.ERROR, LogLevel.FATAL -> Log.e("OneSignalSDK", event.entry)
                LogLevel.NONE -> Unit
            }
        }

        // App ID comes from BuildConfig, sourced from local.properties / -P override at build
        // time. Update via `examples/demo/local.properties` (ONESIGNAL_APP_ID=...) and rebuild.
        val appId = BuildConfig.ONESIGNAL_APP_ID
        OneSignalService.setAppId(appId)
        
        // Initialize tooltip helper
        TooltipHelper.init(this)

        // Set consent required before init (must be set before initWithContext)
        OneSignal.consentRequired = SharedPreferenceUtil.getCachedConsentRequired(this)
        OneSignal.consentGiven = SharedPreferenceUtil.getUserPrivacyConsent(this)

        // Initialize OneSignal on main thread (required)
        // Crash handler + ANR detector are initialized early inside initWithContext
        OneSignal.initWithContext(this, appId)
        Log.i(TAG, "OneSignal init completed (crash handler, ANR detector, and logging active)")

        // Set up all OneSignal listeners
        setupOneSignalListeners()
        
        // Note: Notification permission is automatically requested when MainActivity loads.
        // This ensures the prompt appears after the user sees the app UI.
    }

    private fun setupOneSignalListeners() {
        OneSignal.InAppMessages.addLifecycleListener(object : IInAppMessageLifecycleListener {
            override fun onWillDisplay(event: IInAppMessageWillDisplayEvent) {
                Log.d(TAG, "onWillDisplayInAppMessage")
            }

            override fun onDidDisplay(event: IInAppMessageDidDisplayEvent) {
                Log.d(TAG, "onDidDisplayInAppMessage")
            }

            override fun onWillDismiss(event: IInAppMessageWillDismissEvent) {
                Log.d(TAG, "onWillDismissInAppMessage")
            }

            override fun onDidDismiss(event: IInAppMessageDidDismissEvent) {
                Log.d(TAG, "onDidDismissInAppMessage")
            }
        })

        OneSignal.InAppMessages.addClickListener(object : IInAppMessageClickListener {
            override fun onClick(event: IInAppMessageClickEvent) {
                Log.d(TAG, "IInAppMessageClickListener.onClick")
            }
        })

        OneSignal.Notifications.addClickListener(object : INotificationClickListener {
            override fun onClick(event: INotificationClickEvent) {
                Log.d(TAG, "INotificationClickListener.onClick fired with event: $event")
            }
        })

        OneSignal.Notifications.addForegroundLifecycleListener(object : INotificationLifecycleListener {
            override fun onWillDisplay(event: INotificationWillDisplayEvent) {
                Log.d(TAG, "INotificationLifecycleListener.onWillDisplay fired with event: $event")

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
                Log.i(TAG, "User state changed: onesignalId=${state.current.onesignalId}, externalId=${state.current.externalId}")
            }
        })

        // Restore cached states
        OneSignal.InAppMessages.paused = SharedPreferenceUtil.getCachedInAppMessagingPausedStatus(this)
        OneSignal.Location.isShared = SharedPreferenceUtil.getCachedLocationSharedStatus(this)
    }
}
