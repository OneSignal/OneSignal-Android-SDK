package com.onesignal.notification.internal.open

import android.app.Activity
import android.content.Intent

internal interface INotificationOpenedProcessorHMS {
    suspend fun handleHMSNotificationOpenIntent(activity: Activity, intent: Intent?)
}
