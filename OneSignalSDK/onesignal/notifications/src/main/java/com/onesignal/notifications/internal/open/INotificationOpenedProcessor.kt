package com.onesignal.notifications.internal.open

import android.content.Context
import android.content.Intent

internal interface INotificationOpenedProcessor {
    /**
     * Process the opening or dismissing of a notification.
     *
     * @param context Either the open activity or the dismiss receiver context.
     * @param intent The user intent that drove the open/dismiss.
     */
    suspend fun processFromContext(
        context: Context,
        intent: Intent,
    )
}
