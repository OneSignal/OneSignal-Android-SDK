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
     * Whether the session is valid.
     */
    var isValid: Boolean
        get() = getProperty(::isValid.name) { true }
        set(value) { setProperty(::isValid.name, value) }

    /**
     * When this session started, in Unix time milliseconds.
     */
    var startTime: Long
        get() = getProperty(::startTime.name)
        set(value) { setProperty(::startTime.name, value) }

    /**
     * When this app was last focused, in Unix time milliseconds.
     */
    var focusTime: Long
        get() = getProperty(::focusTime.name) { 0 }
        set(value) { setProperty(::focusTime.name, value) }

    /**
     * How long this session has spent as active, in milliseconds.
     */
    var activeDuration: Long
        get() = getProperty(::activeDuration.name) { 0L }
        set(value) { setProperty(::activeDuration.name, value) }
}
