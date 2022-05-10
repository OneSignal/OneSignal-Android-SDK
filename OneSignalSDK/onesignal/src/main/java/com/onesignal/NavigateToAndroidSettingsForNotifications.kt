package com.onesignal

import android.content.Context
import android.content.Intent

object NavigateToAndroidSettingsForNotifications {
    fun show(context: Context) {
        val intent = Intent()
        intent.action = "android.settings.APP_NOTIFICATION_SETTINGS"
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        //for Android 5-7
        intent.putExtra("app_package", context.getPackageName())
        intent.putExtra("app_uid", context.getApplicationInfo().uid)

        // for Android 8 and above
        intent.putExtra("android.provider.extra.APP_PACKAGE", context.getPackageName())
        context.startActivity(intent)
    }
}
