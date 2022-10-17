package com.onesignal

import android.annotation.TargetApi
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.DeadSystemException
import android.util.AndroidException

class ApplicationInfoHelper {
    companion object {
        // Safe to cache as nothing can change the app while it is running.
        private var cachedInfo: ApplicationInfo? = null

        @TargetApi(24)
        fun getInfo(context: Context): ApplicationInfo? {
            if (cachedInfo != null) {
                return cachedInfo
            }

            val packageManager = context.packageManager
            return try {
                // Using this instead of context.applicationInfo as it's metaData is always null
                cachedInfo = packageManager.getApplicationInfo(
                    context.packageName,
                    PackageManager.GET_META_DATA,
                )
                cachedInfo
            } catch (e: AndroidException) {
                // Suppressing DeadSystemException as the app is already dying for
                // another reason and allowing this exception to bubble up would
                // create a red herring for app developers. We still re-throw
                // others, as we don't want to silently hide other issues.
                if (e !is DeadSystemException) {
                    throw e
                }
                null
            }
        }
    }
}
