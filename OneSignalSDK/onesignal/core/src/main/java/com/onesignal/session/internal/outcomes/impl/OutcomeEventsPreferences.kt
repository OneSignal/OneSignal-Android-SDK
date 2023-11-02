package com.onesignal.session.internal.outcomes.impl

import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores

internal class OutcomeEventsPreferences(
    private val preferences: IPreferencesService,
) : IOutcomeEventsPreferences {
    override var unattributedUniqueOutcomeEventsSentByChannel: Set<String>?
        get() =
            preferences.getStringSet(
                PreferenceStores.ONESIGNAL,
                PreferenceOneSignalKeys.PREFS_OS_UNATTRIBUTED_UNIQUE_OUTCOME_EVENTS_SENT,
                null,
            )
        set(value) {
            preferences.saveStringSet(
                PreferenceStores.ONESIGNAL,
                // Post success, store unattributed unique outcome event names
                PreferenceOneSignalKeys.PREFS_OS_UNATTRIBUTED_UNIQUE_OUTCOME_EVENTS_SENT,
                value,
            )
        }
}
