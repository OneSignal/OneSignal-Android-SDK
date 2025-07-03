package com.onesignal.user.internal.customEvents

import com.onesignal.common.modeling.ModelStore
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.user.internal.operations.CustomEvent
import org.json.JSONObject

class CustomEventModelStore(prefs: IPreferencesService) : ModelStore<CustomEvent>("custom_events", prefs) {
    override fun create(jsonObject: JSONObject?): CustomEvent? {
        if (jsonObject == null) {
            Logging.error("null jsonObject sent to CustomEventModelStore.create")
            return null
        }

        val customEvent = CustomEvent()
        customEvent.initializeFromJson(jsonObject)
        return customEvent
    }
}
