package com.onesignal.core.internal.models

import com.onesignal.core.internal.modeling.Model

internal class TriggerModel : Model() {
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
