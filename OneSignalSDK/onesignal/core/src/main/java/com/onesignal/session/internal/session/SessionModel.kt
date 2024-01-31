package com.onesignal.session.internal.session

import com.onesignal.common.modeling.Model

/**
 * The session model tracks all data related to the SDK's current session.
 */
class SessionModel : Model() {
    /**
     * The ID of the current session.
     */
    var sessionId: String
        get() = getStringProperty(::sessionId.name)
        set(value) { setStringProperty(::sessionId.name, value) }

    /**
     * Whether the session is valid.
     */
    var isValid: Boolean
        get() = getBooleanProperty(::isValid.name) { true }
        set(value) { setBooleanProperty(::isValid.name, value) }

    /**
     * When this session started, in Unix time milliseconds.
     */
    var startTime: Long
        get() = getLongProperty(::startTime.name)
        set(value) { setLongProperty(::startTime.name, value) }

    /**
     * When this app was last focused, in Unix time milliseconds.
     */
    var focusTime: Long
        get() = getLongProperty(::focusTime.name) { 0 }
        set(value) { setLongProperty(::focusTime.name, value) }

    /**
     * How long this session has spent as active, in milliseconds.
     */
    var activeDuration: Long
        get() = getLongProperty(::activeDuration.name) { 0L }
        set(value) { setLongProperty(::activeDuration.name, value) }
}
