package com.onesignal.session.internal.session

/**
 * Implement this interface and subscribe via [ISessionService.subscribe] to
 * react to session lifecycle events.
 */
interface ISessionLifecycleHandler {
    /**
     * Called when a session has been started.
     */
    fun onSessionStarted()

    /**
     * Called when a session is again active.
     */
    fun onSessionActive()

    /**
     * Called when a session has ended.
     *
     * @param duration The active duration of the session, in milliseconds.
     */
    fun onSessionEnded(duration: Long)
}
