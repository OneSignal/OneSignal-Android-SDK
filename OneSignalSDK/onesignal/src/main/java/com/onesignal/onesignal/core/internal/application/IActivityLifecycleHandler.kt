package com.onesignal.onesignal.core.internal.application

import android.app.Activity

interface IActivityLifecycleHandler  {
    fun onActivityAvailable(activity: Activity)
    fun onActivityStopped(activity: Activity)
}
