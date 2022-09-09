package com.onesignal.core.internal.models

import com.onesignal.core.internal.modeling.Model
import java.util.Date

internal class SessionModel : Model() {
    var startTime: Long
        get() = get(::startTime.name)
        set(value) { set(::startTime.name, value) }

    var unfocusedDuration: Double
        get() = get(::unfocusedDuration.name) { 0.0 }
        set(value) { set(::unfocusedDuration.name, value) }
}
