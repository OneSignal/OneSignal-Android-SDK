package com.onesignal.core.internal.models

import com.onesignal.core.internal.modeling.Model

internal class SessionModel : Model() {
    var startTime: Long
        get() = getProperty(::startTime.name)
        set(value) { setProperty(::startTime.name, value) }

    var unfocusedDuration: Double
        get() = getProperty(::unfocusedDuration.name) { 0.0 }
        set(value) { setProperty(::unfocusedDuration.name, value) }
}
