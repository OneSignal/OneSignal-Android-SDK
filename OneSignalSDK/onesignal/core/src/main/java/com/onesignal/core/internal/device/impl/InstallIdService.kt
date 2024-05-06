package com.onesignal.core.internal.device.impl

import com.onesignal.core.internal.device.IInstallIdService
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import java.util.UUID

/**
 * Manages a persistent UUIDv4, generated once when app is first opened.
 * Value is for a HTTP header, OneSignal-Install-Id, added on all calls made
 * to OneSignal's backend. This allows the OneSignal's backend know where
 * traffic is coming from, no matter if the SubscriptionId or OneSignalId
 * changes or isn't available yet.
 */
internal class InstallIdService(
    private val _prefs: IPreferencesService,
) : IInstallIdService {
    private val currentId: UUID by lazy {
        val idFromPrefs = _prefs.getString(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_OS_INSTALL_ID)
        if (idFromPrefs != null) {
            UUID.fromString(idFromPrefs)
        } else {
            val newId = UUID.randomUUID()
            _prefs.saveString(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_OS_INSTALL_ID, newId.toString())
            newId
        }
    }

    /**
     * WARNING: This may do disk I/O on the first call, so never call this from
     * the main thread. Disk I/O is done inside of "currentId by lazy".
     */
    override suspend fun getId() = currentId
}
