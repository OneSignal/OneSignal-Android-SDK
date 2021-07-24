package com.onesignal

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

class GenerateNotificationOpenIntent(
    private val context: Context,
    private val intent: Intent?,
    private val startApp: Boolean
) {

    private val notificationOpenedClass: Class<*> = NotificationOpenedReceiver::class.java

    fun getNewBaseIntent(
        notificationId: Int,
    ): Intent {
        // We use SINGLE_TOP and CLEAR_TOP as we don't want more than one OneSignal invisible click
        //   tracking Activity instance around.
        var intentFlags =
            Intent.FLAG_ACTIVITY_SINGLE_TOP or
            Intent.FLAG_ACTIVITY_CLEAR_TOP
        if (!startApp) {
            // If we don't want the app to launch we put OneSignal's invisible click tracking Activity on it's own task
            //   so it doesn't resume an existing one once it closes.
            intentFlags =
                intentFlags or (
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET
                )
        }

        return Intent(
            context,
            notificationOpenedClass
        )
        .putExtra(
            GenerateNotification.BUNDLE_KEY_ANDROID_NOTIFICATION_ID,
            notificationId
        )
        .addFlags(intentFlags)
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
        // OneSignal's invisible Activity to get the notification open event
        val oneSignalActivityIntent = PendingIntent.getActivity(
            context,
            requestCode,
            oneSignalIntent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )

        val launchIntent =
            getIntentVisible() ?:
            // Even though the default app open action is disabled we still need to attach OneSignal's
            // invisible Activity to capture click event to report click counts and etc.
            // You may be thinking why not use a BroadcastReceiver instead of an invisible
            // Activity? This could be done in a 5.0.0 release but can't be changed now as it is
            // unknown if the app developer will be starting there own Activity from their
            // OSNotificationOpenedHandler and that would have side-effects.
            return oneSignalActivityIntent

        // Launch desired Activity we want the user to be take to the followed by
        //   OneSignal's invisible notification open tracking Activity
        // This allows OneSignal to track the click, fire OSNotificationOpenedHandler, etc while allowing
        //   the app developer to set the Activity they want at notification creation time. (FUTURE API FEATURE)
        // AKA "Reverse Activity Trampolining"
        return PendingIntent.getActivities(
            context,
            requestCode,
            arrayOf(launchIntent, oneSignalIntent),
            PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    // Return the provide intent if one was set, otherwise default to opening the app.
    private fun getIntentVisible(): Intent? {
        // 1. Check if the App developer disabled the default action of opening / resuming the app.
        if (!startApp) return null

        if (intent != null) return intent
        return getIntentAppOpen()
    }

    // Provides the default launcher Activity, if the app has one.
    //   - This is almost always true, one of the few exceptions being an app that is only a widget.
    private fun getIntentAppOpen(): Intent? {
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
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED

        return launchIntent
    }
}