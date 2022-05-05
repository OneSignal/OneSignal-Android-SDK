package com.onesignal

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.RequiresApi

class GenerateNotificationOpenIntent(
    private val context: Context,
    private val intent: Intent?,
    private val startApp: Boolean
) {

    private val notificationOpenedClassAndroid23Plus: Class<*> = NotificationOpenedReceiver::class.java
    private val notificationOpenedClassAndroid22AndOlder: Class<*> = NotificationOpenedReceiverAndroid22AndOlder::class.java

    fun getNewBaseIntent(
        notificationId: Int,
    ): Intent {
        val intent =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M)
                getNewBaseIntentAndroidAPI23Plus()
            else
                getNewBaseIntentAndroidAPI22AndOlder()

        return intent
        .putExtra(
            GenerateNotification.BUNDLE_KEY_ANDROID_NOTIFICATION_ID,
            notificationId
        )
        // We use SINGLE_TOP and CLEAR_TOP as we don't want more than one OneSignal invisible click
        //   tracking Activity instance around.
        .addFlags(
            Intent.FLAG_ACTIVITY_SINGLE_TOP or
            Intent.FLAG_ACTIVITY_CLEAR_TOP
        )
    }

    @RequiresApi(android.os.Build.VERSION_CODES.M)
    private fun getNewBaseIntentAndroidAPI23Plus(): Intent {
        return Intent(
            context,
            notificationOpenedClassAndroid23Plus
        )
    }

    // See NotificationOpenedReceiverAndroid22AndOlder.kt for details
    @Deprecated("Use getNewBaseIntentAndroidAPI23Plus instead for Android 6+")
    private fun getNewBaseIntentAndroidAPI22AndOlder(): Intent {
        val intent = Intent(
            context,
            notificationOpenedClassAndroid22AndOlder
        )
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET
        )
        return intent
    }

    /**
     * Creates a PendingIntent to attach to the notification click and it's action button(s).
     * If the user interacts with the notification this normally starts the app or resumes it
     * unless the app developer disables this via a OneSignal meta-data AndroidManifest.xml setting
     *
     * The default behavior is to open the app in the same way an Android homescreen launcher does.
     * This means we expect the following behavior:
     * 1. Starts the Activity defined in the app's AndroidManifest.xml as "android.intent.action.MAIN"
     * 2. If the app is already running, instead the last activity will be resumed
     * 3. If the app is not running (due to being push out of memory), the last activity will be resumed
     * 4. If the app is no longer in the recent apps list, it is not resumed, same as #1 above.
     * - App is removed from the recent app's list if it is swiped away or "clear all" is pressed.
     */
    fun getNewActionPendingIntent(
        requestCode: Int,
        oneSignalIntent: Intent,
    ): PendingIntent? {
        val flags =  PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(
            context,
            requestCode,
            oneSignalIntent,
            flags
        )
    }

    // Return the provide intent if one was set, otherwise default to opening the app.
    fun getIntentVisible(): Intent? {
        if (intent != null) return intent
        return getIntentAppOpen()
    }

    // Provides the default launcher Activity, if the app has one.
    //   - This is almost always true, one of the few exceptions being an app that is only a widget.
    private fun getIntentAppOpen(): Intent? {
        if (!startApp) return null

        val launchIntent =
            context.packageManager.getLaunchIntentForPackage(
                context.packageName
            )
            ?: return null

        // Removing "package" from the intent treats the app as if it was started externally.
        //   - This is exactly what an Android Launcher does.
        // This prevents another instance of the Activity from being created.
        // Android 11 no longer requires nulling this out to get this behavior.
        launchIntent.setPackage(null)
        launchIntent.flags =
            Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED

        return launchIntent
    }
}
