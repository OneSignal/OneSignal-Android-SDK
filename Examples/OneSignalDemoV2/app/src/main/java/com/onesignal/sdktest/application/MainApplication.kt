package com.onesignal.sdktest.application

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
import com.onesignal.sdktest.R
import com.onesignal.sdktest.data.network.OneSignalNotificationSender
import com.onesignal.sdktest.util.SharedPreferenceUtil
import com.onesignal.user.state.IUserStateObserver
import com.onesignal.user.state.UserChangedState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainApplication : MultiDexApplication() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

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

        // Get or set the OneSignal App ID
        var appId = SharedPreferenceUtil.getOneSignalAppId(this)
        if (appId == null) {
            appId = getString(R.string.onesignal_app_id)
            SharedPreferenceUtil.cacheOneSignalAppId(this, appId)
        }

        // Initialize notification sender with app ID
        OneSignalNotificationSender.setAppId(appId)

        // Initialize OneSignal on main thread (required)
        OneSignal.initWithContext(this, appId)
        Log.d(TAG, "OneSignal init completed")

        // Set up all OneSignal listeners
        setupOneSignalListeners()
        
        // Note: Permission is requested via the "PROMPT PUSH" button in MainActivity
        // when the user has not yet granted notification permission.
        // This avoids showing the prompt before the user sees the app UI.
    }

    private fun setupOneSignalListeners() {
        OneSignal.InAppMessages.addLifecycleListener(object : IInAppMessageLifecycleListener {
            override fun onWillDisplay(event: IInAppMessageWillDisplayEvent) {
                Log.v(TAG, "onWillDisplayInAppMessage")
            }

            override fun onDidDisplay(event: IInAppMessageDidDisplayEvent) {
                Log.v(TAG, "onDidDisplayInAppMessage")
            }

            override fun onWillDismiss(event: IInAppMessageWillDismissEvent) {
                Log.v(TAG, "onWillDismissInAppMessage")
            }

            override fun onDidDismiss(event: IInAppMessageDidDismissEvent) {
                Log.v(TAG, "onDidDismissInAppMessage")
            }
        })

        OneSignal.InAppMessages.addClickListener(object : IInAppMessageClickListener {
            override fun onClick(event: IInAppMessageClickEvent) {
                Log.v(TAG, "IInAppMessageClickListener.onClick")
            }
        })

        OneSignal.Notifications.addClickListener(object : INotificationClickListener {
            override fun onClick(event: INotificationClickEvent) {
                Log.v(TAG, "INotificationClickListener.onClick fired with event: $event")
            }
        })

        OneSignal.Notifications.addForegroundLifecycleListener(object : INotificationLifecycleListener {
            override fun onWillDisplay(event: INotificationWillDisplayEvent) {
                Log.v(TAG, "INotificationLifecycleListener.onWillDisplay fired with event: $event")

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
                Log.v(TAG, "onUserStateChange fired: ${state.current.toJSONObject()}")
            }
        })

        // Set default states
        OneSignal.InAppMessages.paused = true
        OneSignal.Location.isShared = false
    }
}
