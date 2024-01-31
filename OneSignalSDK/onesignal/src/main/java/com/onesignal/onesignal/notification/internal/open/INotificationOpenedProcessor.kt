package com.onesignal.onesignal.notification.internal.open

import android.content.Intent
import android.content.Context

interface INotificationOpenedProcessor {
    /**
     * Process the opening or dismissing of a notification.
     *
     * @param context Either the open activity or the dismiss receiver context.
     * @param intent The user intent that drove the open/dismiss.
     */
    suspend fun processFromContext(context: Context, intent: Intent)
}