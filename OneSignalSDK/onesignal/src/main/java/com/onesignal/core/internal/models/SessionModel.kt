package com.onesignal.core.internal.models

import com.onesignal.core.internal.modeling.Model

/**
 * The session model tracks all data related to the SDK's current session.
 */
internal class SessionModel : Model() {
    /**
     * The ID of the current session.
     */
    var sessionId: String
        get() = getProperty(::sessionId.name)
        set(value) { setProperty(::sessionId.name, value) }

    /**
     * When this session started, in Unix time milliseconds.
     */
    var startTime: Long
        get() = getProperty(::startTime.name)
        set(value) { setProperty(::startTime.name, value) }

    /**
     * How long this session has spent unfocused, in milliseconds.
     */
    var unfocusedDuration: Double
        get() = getProperty(::unfocusedDuration.name) { 0.0 }
        set(value) { setProperty(::unfocusedDuration.name, value) }
}
