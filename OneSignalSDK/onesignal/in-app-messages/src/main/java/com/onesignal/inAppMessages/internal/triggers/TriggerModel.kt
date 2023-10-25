package com.onesignal.inAppMessages.internal.triggers

import com.onesignal.common.modeling.Model

class TriggerModel : Model() {
    /**
     * The key of this trigger
     */
    var key: String
        get() = getStringProperty(::key.name) { "" }
        set(value) {
            setStringProperty(::key.name, value)
        }

    /**
     * The value of this trigger
     */
    var value: Any
        get() = getAnyProperty(::value.name) { "" }
        set(value) {
            setAnyProperty(::value.name, value)
        }
}
