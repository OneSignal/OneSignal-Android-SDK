package com.onesignal.sdktest.notification

import android.graphics.Color
import android.util.Log
import androidx.core.app.NotificationCompat
import com.onesignal.notifications.INotificationReceivedEvent
import com.onesignal.notifications.INotificationServiceExtension
import com.onesignal.sdktest.R
import com.onesignal.sdktest.constant.Tag

class NotificationServiceExtension : INotificationServiceExtension {
    override fun onNotificationReceived(event: INotificationReceivedEvent) {
        Log.v(
            Tag.LOG_TAG,
            "IRemoteNotificationReceivedHandler fired with INotificationReceivedEvent: $event"
        )

        val notification = event.notification

        if (notification.actionButtons != null) {
            for (button in notification.actionButtons!!) {
                Log.v(
                    Tag.LOG_TAG,
                    "ActionButton: $button"
                )
            }
        }

        notification.setExtender { builder: NotificationCompat.Builder ->
            builder.setColor(Color.GREEN)
        }
    }
}
