package com.onesignal

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.RequiresApi

class IntentGeneratorForAttachingToNotifications(
    val context: Context
) {
    private val notificationOpenedClassAndroid23Plus: Class<*> = NotificationOpenedReceiver::class.java
    private val notificationOpenedClassAndroid22AndOlder: Class<*> = NotificationOpenedReceiverAndroid22AndOlder::class.java

    fun getNewBaseIntent(
        notificationId: Int
    ): Intent {
        val intent =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                getNewBaseIntentAndroidAPI23Plus()
            } else {
                getNewBaseIntentAndroidAPI22AndOlder()
            }

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

    fun getNewActionPendingIntent(
        requestCode: Int,
        oneSignalIntent: Intent
    ): PendingIntent? {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(
            context,
            requestCode,
            oneSignalIntent,
            flags
        )
    }
}
