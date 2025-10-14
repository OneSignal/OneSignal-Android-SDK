package com.onesignal.core.internal.preferences

/**
 * Returns the cached app ID from v4 of the SDK, if available.
 * This is to maintain compatibility with apps that have not updated to the latest app ID.
 */
fun IPreferencesService.getLegacyAppId(): String? {
    return getString(
        PreferenceStores.ONESIGNAL,
        PreferenceOneSignalKeys.PREFS_LEGACY_APP_ID,
    )
}
