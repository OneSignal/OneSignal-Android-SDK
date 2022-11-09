package com.onesignal.inAppMessages.internal.triggers

import com.onesignal.common.modeling.Model

class TriggerModel : Model() {
    /**
     * The key of this trigger
     */
    var key: String
        get() = getProperty(::key.name) { "" }
        set(value) { setProperty(::key.name, value) }

    /**
     * The value of this trigger
     */
    var value: Any
        get() = getProperty(::value.name) { "" }
        set(value) { setProperty(::value.name, value) }
}
