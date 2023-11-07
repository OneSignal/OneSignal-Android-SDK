package com.onesignal.core.internal.preferences

import android.content.Context
import android.os.Build
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import java.io.File

object PreferenceStoreFix {
    /**
     * Ensure the OneSignal preference store is not using the v4 obfuscated version, if one
     * exists.
     */
    fun ensureNoObfuscatedPrefStore(context: Context) {
        try {
            // In the v4 version the OneSignal shared preference name was based on the OneSignal
            // class name, which might be minimized/obfuscated if the app is using ProGuard or
            // similar.  In order for a device to successfully migrate from v4 to v5 picking
            // up the subscription, we need to copy the shared preferences from the obfuscated
            // version to the static "OneSignal" preference name.  We only do this
            // if there isn't already a "OneSignal" preference store.
            val sharedPrefsDir =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    File(context.dataDir, "shared_prefs")
                } else {
                    File(context.filesDir.parentFile, "shared_prefs")
                }

            val osPrefsFile = File(sharedPrefsDir, "OneSignal.xml")

            if (!sharedPrefsDir.exists() || !sharedPrefsDir.isDirectory || osPrefsFile.exists()) {
                return
            }

            val prefsFileList = sharedPrefsDir.listFiles() ?: return

            // Go through every preference file, looking for the OneSignal preference store.
            for (prefsFile in prefsFileList) {
                val prefsStore =
                    context.getSharedPreferences(prefsFile.nameWithoutExtension, Context.MODE_PRIVATE)

                if (prefsStore.contains(PreferenceOneSignalKeys.PREFS_LEGACY_PLAYER_ID)) {
                    prefsFile.renameTo(osPrefsFile)
                    return
                }
            }
        } catch (e: Throwable) {
            Logging.log(LogLevel.ERROR, "error attempting to fix obfuscated preference store", e)
        }
    }
}
