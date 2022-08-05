package com.onesignal.onesignal.notification.internal.open

import android.app.Activity
import android.content.Intent

interface INotificationOpenedProcessorHMS {
    fun handleHMSNotificationOpenIntent(activity: Activity, intent: Intent?)
}