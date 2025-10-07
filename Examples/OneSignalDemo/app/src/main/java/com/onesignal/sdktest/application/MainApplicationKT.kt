package com.onesignal.sdktest.application

/**
 * Modern Kotlin implementation of MainApplication.
 * 
 * This replaces the deprecated MainApplication.java with:
 * - Better async handling using Kotlin Coroutines
 * - Modern OneSignal API usage
 * - Cleaner code structure
 * - Proper ANR prevention
 * 
 * @see MainApplication (deprecated Java version)
 */
import android.annotation.SuppressLint
import android.os.StrictMode
import android.util.Log
import androidx.annotation.NonNull
import androidx.multidex.MultiDexApplication
import com.onesignal.OneSignal
import com.onesignal.common.threading.OneSignalDispatchers
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
import com.onesignal.sdktest.constant.Tag
import com.onesignal.sdktest.constant.Text
import com.onesignal.sdktest.notification.OneSignalNotificationSender
import com.onesignal.sdktest.util.SharedPreferenceUtil
import com.onesignal.user.state.IUserStateObserver
import com.onesignal.user.state.UserChangedState
import com.onesignal.user.state.UserState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainApplicationKT : MultiDexApplication() {
    init {
        // run strict mode to surface any potential issues easier
        StrictMode.enableDefaults()
    }

    @SuppressLint("NewApi")
    override fun onCreate() {
        super.onCreate()
        OneSignal.Debug.logLevel = LogLevel.DEBUG

        // OneSignal Initialization
        var appId = SharedPreferenceUtil.getOneSignalAppId(this)
        // If cached app id is null use the default, otherwise use cached.
        if (appId == null) {
            appId = getString(R.string.onesignal_app_id)
            SharedPreferenceUtil.cacheOneSignalAppId(this, appId)
        }

        OneSignalNotificationSender.setAppId(appId)

        // Initialize OneSignal asynchronously on background thread to avoid ANR
        OneSignalDispatchers.launchOnIO {
            try {
                OneSignal.initWithContextSuspend(this@MainApplicationKT, appId)
                Log.d(Tag.LOG_TAG, "OneSignal async init completed")

                // Set up all OneSignal listeners after successful async initialization
                setupOneSignalListeners()

                // Request permission - this will internally switch to Main thread for UI operations
                OneSignal.Notifications.requestPermission(true)

                Log.d(Tag.LOG_TAG, Text.ONESIGNAL_SDK_INIT)

            } catch (e: Exception) {
                Log.e(Tag.LOG_TAG, "OneSignal initialization error", e)
            }
        }
    }

    private fun setupOneSignalListeners() {
        OneSignal.InAppMessages.addLifecycleListener(object : IInAppMessageLifecycleListener {
            override fun onWillDisplay(@NonNull event: IInAppMessageWillDisplayEvent) {
                Log.v(Tag.LOG_TAG, "onWillDisplayInAppMessage")
            }

            override fun onDidDisplay(@NonNull event: IInAppMessageDidDisplayEvent) {
                Log.v(Tag.LOG_TAG, "onDidDisplayInAppMessage")
            }

            override fun onWillDismiss(@NonNull event: IInAppMessageWillDismissEvent) {
                Log.v(Tag.LOG_TAG, "onWillDismissInAppMessage")
            }

            override fun onDidDismiss(@NonNull event: IInAppMessageDidDismissEvent) {
                Log.v(Tag.LOG_TAG, "onDidDismissInAppMessage")
            }
        })

        OneSignal.InAppMessages.addClickListener(object : IInAppMessageClickListener {
            override fun onClick(event: IInAppMessageClickEvent) {
                Log.v(Tag.LOG_TAG, "INotificationClickListener.inAppMessageClicked")
            }
        })

        OneSignal.Notifications.addClickListener(object : INotificationClickListener {
            override fun onClick(event: INotificationClickEvent) {
                Log.v(Tag.LOG_TAG, "INotificationClickListener.onClick fired" +
                        " with event: " + event)
            }
        })

        OneSignal.Notifications.addForegroundLifecycleListener(object : INotificationLifecycleListener {
            override fun onWillDisplay(@NonNull event: INotificationWillDisplayEvent) {
                Log.v(Tag.LOG_TAG, "INotificationLifecycleListener.onWillDisplay fired" +
                        " with event: " + event)

                val notification: IDisplayableNotification = event.notification

                //Prevent OneSignal from displaying the notification immediately on return. Spin
                //up a new thread to mimic some asynchronous behavior, when the async behavior (which
                //takes 2 seconds) completes, then the notification can be displayed.
                event.preventDefault()
                val r = Runnable {
                    try {
                        Thread.sleep(SLEEP_TIME_TO_MIMIC_ASYNC_OPERATION.toLong())
                    } catch (ignored: InterruptedException) {
                    }

                    notification.display()
                }

                val t = Thread(r)
                t.start()
            }
        })

        OneSignal.User.addObserver(object : IUserStateObserver {
            override fun onUserStateChange(@NonNull state: UserChangedState) {
                val currentUserState: UserState = state.current
                Log.v(Tag.LOG_TAG, "onUserStateChange fired " + currentUserState.toJSONObject())
            }
        })

        OneSignal.InAppMessages.paused = true
        OneSignal.Location.isShared = false
    }

    companion object {
        private const val SLEEP_TIME_TO_MIMIC_ASYNC_OPERATION = 2000
    }
}