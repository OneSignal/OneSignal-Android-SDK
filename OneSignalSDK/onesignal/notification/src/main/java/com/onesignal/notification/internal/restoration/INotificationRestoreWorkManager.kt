package com.onesignal.notification.internal.restoration

import android.content.Context

internal interface INotificationRestoreWorkManager {
    fun beginEnqueueingWork(context: Context, shouldDelay: Boolean)
}
