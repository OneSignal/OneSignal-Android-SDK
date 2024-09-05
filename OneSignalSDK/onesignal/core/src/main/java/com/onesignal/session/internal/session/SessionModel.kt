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
        set(value) {
            setStringProperty(::sessionId.name, value)
        }

    /**
     * Indicates if there is an active session.
     * True when app is in the foreground.
     * Also true in the background for a short period of time (default 30s)
     * as a debouncing mechanism.
     */
    var isValid: Boolean
        get() = getBooleanProperty(::isValid.name) { false }
        set(value) {
            setBooleanProperty(::isValid.name, value)
        }

    /**
     * When this session started, in Unix time milliseconds.
     * This is used by In-App Message triggers, and not used in detecting session time.
     */
    var startTime: Long
        get() = getLongProperty(::startTime.name) { System.currentTimeMillis() }
        set(value) {
            setLongProperty(::startTime.name, value)
        }

    /**
     * When this app was last focused, in Unix time milliseconds.
     */
    var focusTime: Long
        get() = getLongProperty(::focusTime.name) { System.currentTimeMillis() }
        set(value) {
            setLongProperty(::focusTime.name, value)
        }

    /**
     * How long this session has spent as active, in milliseconds.
     */
    var activeDuration: Long
        get() = getLongProperty(::activeDuration.name) { 0L }
        set(value) {
            setLongProperty(::activeDuration.name, value)
        }
}
