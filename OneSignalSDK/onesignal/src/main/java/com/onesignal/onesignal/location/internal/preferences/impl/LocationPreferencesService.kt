package com.onesignal.onesignal.location.internal.preferences.impl

import com.onesignal.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.onesignal.location.internal.common.LocationConstants
import com.onesignal.onesignal.location.internal.preferences.ILocationPreferencesService

internal class LocationPreferencesService(
    private val _prefs: IPreferencesService,
) : ILocationPreferencesService {
    override var lastLocationTime: Long
        get() = _prefs.getLong(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_OS_LAST_LOCATION_TIME, LocationConstants.TIME_BACKGROUND_SEC * -1000)!!
        set(time) {
            _prefs.saveLong(
                PreferenceStores.ONESIGNAL,
                PreferenceOneSignalKeys.PREFS_OS_LAST_LOCATION_TIME, time
            )
        }
}
