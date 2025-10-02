package com.onesignal.core.internal.preferences

/**
 * Returns the cached app ID from v4 of the SDK, if available.
 */
fun IPreferencesService.getLegacyAppId(): String? {
    return getString(
        PreferenceStores.ONESIGNAL,
        PreferenceOneSignalKeys.PREFS_LEGACY_APP_ID,
    )
}
