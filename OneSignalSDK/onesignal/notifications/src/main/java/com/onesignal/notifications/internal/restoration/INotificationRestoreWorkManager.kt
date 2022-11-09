package com.onesignal.notifications.internal.restoration

import android.content.Context

internal interface INotificationRestoreWorkManager {
    fun beginEnqueueingWork(context: Context, shouldDelay: Boolean)
}
