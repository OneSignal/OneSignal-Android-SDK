package com.onesignal

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

object NavigateToAndroidSettingsForLocation {
    fun show(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.parse("package:" + context.packageName)
        context.startActivity(intent)
    }
}