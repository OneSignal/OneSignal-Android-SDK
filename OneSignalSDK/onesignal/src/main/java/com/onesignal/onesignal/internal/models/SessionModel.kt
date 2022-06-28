package com.onesignal.onesignal.internal.models

import com.onesignal.onesignal.internal.modeling.Model
import java.util.*

class SessionModel : Model() {
    var startTime: Date
        get() = get(::startTime.name)
        set(value) { set(::startTime.name, value) }

    var unfocusedDuration: Double
        get() = get(::unfocusedDuration.name) { 0.0 }
        set(value) { set(::unfocusedDuration.name, value) }
}