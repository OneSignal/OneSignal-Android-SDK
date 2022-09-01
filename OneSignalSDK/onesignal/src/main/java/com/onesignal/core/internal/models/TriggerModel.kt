package com.onesignal.core.internal.models

import com.onesignal.core.internal.modeling.Model

internal class TriggerModel : Model() {
    /**
     * The key of this trigger
     */
    var key: String
        get() = get(::key.name) { "" }
        set(value) { set(::key.name, value) }

    /**
     * The value of this trigger
     */
    var value: Any
        get() = get(::value.name) { "" }
        set(value) { set(::value.name, value) }
}
