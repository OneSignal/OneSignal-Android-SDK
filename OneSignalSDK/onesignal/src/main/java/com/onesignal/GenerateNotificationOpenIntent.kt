package com.onesignal

import android.content.Context
import android.content.Intent

class GenerateNotificationOpenIntent(
    private val context: Context,
    private val intent: Intent?,
    private val startApp: Boolean
) {
    fun getIntentVisible(): Intent? {
        if (intent != null) return intent
        return getIntentAppOpen()
    }

    /**
     * This opens the app in the same way an Android homescreen launcher does.
     * This means we expect the following behavior:
     * 1. Starts the Activity defined in the app's AndroidManifest.xml as "android.intent.action.MAIN"
     * 2. If the app is already running, instead the last activity will be resumed
     * 3. If the app is not running (due to being push out of memory), the last activity will be resumed
     * 4. If the app is no longer in the recent apps list, it is not resumed, same as #1 above.
     *   - App is removed from the recent app's list if it is swiped away or "clear all" is pressed.
     */
    private fun getIntentAppOpen(): Intent? {
        if (!startApp) return null

        // Is null for apps that only provide a widget for it's UI.
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
