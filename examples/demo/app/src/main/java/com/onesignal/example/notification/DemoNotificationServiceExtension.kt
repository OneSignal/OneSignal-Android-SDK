package com.onesignal.example.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.onesignal.example.data.model.NotificationExtensionOptions
import com.onesignal.example.util.DemoLog
import com.onesignal.example.util.SharedPreferenceUtil
import com.onesignal.notifications.IDisplayableMutableNotification
import com.onesignal.notifications.INotificationReceivedEvent
import com.onesignal.notifications.INotificationServiceExtension

/**
 * The demo's notification service extension, registered in AndroidManifest.xml under the
 * `com.onesignal.NotificationServiceExtension` meta-data key. The SDK resolves that string with
 * Class.forName and calls the no-arg constructor, so a typo in the manifest fails silently at
 * runtime instead of at build time. That reflection is also why the class needs the -keep rule
 * in `onesignal/notifications/consumer-rules.pro` to survive R8.
 *
 * Nothing here runs until you turn it on in the demo's Notification Service Extension section.
 * An always-on extension would change the baseline for every notification the demo sends, and
 * anyone chasing a grouping or channel bug would end up debugging this file without knowing it.
 *
 * The switches come from SharedPreferences, not MainViewModel. This is called whether or not the
 * app is open, so there may be no ViewModel yet.
 */
class DemoNotificationServiceExtension : INotificationServiceExtension {
    override fun onNotificationReceived(event: INotificationReceivedEvent) {
        val options = SharedPreferenceUtil.getNotificationExtensionOptions(event.context)
        if (!options.enabled) return

        val notification = event.notification

        if (options.logDetails) {
            // TODO: [SDK-5011] log `event.restoring` here once it ships. Reading it next to the
            // channel below is the whole diagnosis for a notification that re-alerts on reboot.
            DemoLog.d(
                TAG,
                "received androidNotificationId=${notification.androidNotificationId}" +
                    " notificationId=${notification.notificationId}" +
                    " sentTime=${notification.sentTime}" +
                    " title=${notification.title}",
            )
        }

        if (options.discard) {
            if (options.logDetails) {
                DemoLog.d(TAG, "discarding androidNotificationId=${notification.androidNotificationId}")
            }
            event.preventDefault(true)
            return
        }

        // Set an extender only when a switch needs one, rather than installing a no-op
        // whenever the extension is on. This is about not doing work nothing asked for.
        // An extender cannot change what displays: NotificationGenerationProcessor
        // .shouldDisplayNotification does read hasExtender(), but processHandlerResponse
        // has already dropped a push with an empty body on canDisplay by the time it runs.
        if (options.logDetails || options.applyExtender || options.forceHighImportanceChannel) {
            notification.setExtender(buildExtender(event.context, notification, options))
        }

        if (options.delayDisplay) {
            event.preventDefault()
            Thread {
                Thread.sleep(DISPLAY_DELAY_MS)
                notification.display()
            }.start()
        }
    }

    private fun buildExtender(
        context: Context,
        notification: IDisplayableMutableNotification,
        options: NotificationExtensionOptions,
    ) = NotificationCompat.Extender { builder ->
        if (options.logDetails) {
            // Read the channel before anything below overwrites it. This is the only place an
            // extension can see what the SDK picked. A restored notification lands on
            // `restored_OS_notifications` no matter what the payload asked for, and the payload
            // by itself never shows that.
            DemoLog.d(
                TAG,
                "building androidNotificationId=${notification.androidNotificationId}" +
                    " channel=${NotificationCompat.getChannelId(builder.build())}",
            )
        }

        if (options.applyExtender) {
            builder.setContentTitle("[NSE] ${notification.title.orEmpty()}")
        }

        if (options.forceHighImportanceChannel) {
            forceHighImportanceChannel(context, builder)
        }

        builder
    }

    /**
     * Moves the notification onto an IMPORTANCE_HIGH channel the app owns. This is the pattern a
     * customer used to make every push heads-up, and on an SDK without the SDK-5011 fix it also
     * makes every restored notification alert again after a reboot.
     */
    private fun forceHighImportanceChannel(
        context: Context,
        builder: NotificationCompat.Builder,
    ) {
        builder.priority = NotificationCompat.PRIORITY_HIGH

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                HIGH_IMPORTANCE_CHANNEL_ID,
                "Demo high importance",
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
        builder.setChannelId(HIGH_IMPORTANCE_CHANNEL_ID)
    }

    private companion object {
        const val TAG = "NSE"
        const val HIGH_IMPORTANCE_CHANNEL_ID = "demo_nse_high_importance"

        // Well under the SDK's 30 second wait for the extension, and long enough to watch the
        // notification arrive late on a device.
        const val DISPLAY_DELAY_MS = 5_000L
    }
}
