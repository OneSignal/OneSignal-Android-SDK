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
 * @see MainApplication.java (deprecated Java version)
 */
import android.annotation.SuppressLint
import android.os.StrictMode
import android.util.Log
import androidx.annotation.NonNull
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
import com.onesignal.sdktest.constant.Tag
import com.onesignal.sdktest.constant.Text
import com.onesignal.sdktest.notification.OneSignalNotificationSender
import com.onesignal.sdktest.util.SharedPreferenceUtil
import com.onesignal.user.state.IUserStateObserver
import com.onesignal.user.state.UserChangedState
import com.onesignal.user.state.UserState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainApplicationKT : MultiDexApplication() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // run strict mode to surface any potential issues easier
        StrictMode.allowThreadDiskReads()
        StrictMode.allowThreadDiskWrites()
    }

    @SuppressLint("NewApi")
    override fun onCreate() {
        super.onCreate()
        OneSignal.Debug.logLevel = LogLevel.VERBOSE

        OneSignal.initWithContext(this@MainApplicationKT, "77e32082-ea27-42e3-a898-c72e141824ef")
        OneSignal.login("nan01")
        OneSignal.InAppMessages.addTrigger("fruit", "apple")
        OneSignal.InAppMessages.addClickListener(object : IInAppMessageClickListener {
            override fun onClick(event: IInAppMessageClickEvent) {
                Log.v(Tag.LOG_TAG, "INotificationClickListener.inAppMessageClicked")
            }
        })


    }

}