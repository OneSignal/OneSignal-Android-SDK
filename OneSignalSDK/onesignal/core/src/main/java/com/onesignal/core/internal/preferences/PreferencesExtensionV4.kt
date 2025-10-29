package com.onesignal.core.internal.preferences

/**
 * Returns the cached app ID from v4 of the SDK, if available.
 * This is to maintain compatibility with apps that have not updated to the latest app ID.
 */
fun IPreferencesService.getLegacyAppId() =
    getString(
        PreferenceStores.ONESIGNAL,
        PreferenceOneSignalKeys.PREFS_LEGACY_APP_ID,
    )

/**
 * Returns the cached legacy player ID from v4 of the SDK, if available.
 * Used to determine if migration from v4 to v5 is needed.
 */
fun IPreferencesService.getLegacyPlayerId() =
    getString(
        PreferenceStores.ONESIGNAL,
        PreferenceOneSignalKeys.PREFS_LEGACY_PLAYER_ID,
    )

/**
 * Returns the cached Legacy User Sync Values from v4 of the SDK, if available.
 * This maintains compatibility with apps upgrading from v4 to v5.
 */
fun IPreferencesService.getLegacyUserSyncValues() =
    getString(
        PreferenceStores.ONESIGNAL,
        PreferenceOneSignalKeys.PREFS_LEGACY_USER_SYNCVALUES,
    )

/**
 * Clears the legacy player ID from v4 of the SDK.
 * Called after successfully migrating user data to v5 format.
 */
fun IPreferencesService.clearLegacyPlayerId() =
    saveString(
        PreferenceStores.ONESIGNAL,
        PreferenceOneSignalKeys.PREFS_LEGACY_PLAYER_ID,
        null,
    )
