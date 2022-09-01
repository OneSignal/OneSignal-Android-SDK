package com.onesignal.core.internal.application

import android.app.Activity

internal interface IActivityLifecycleHandler {
    fun onActivityAvailable(activity: Activity)
    fun onActivityStopped(activity: Activity)
}
