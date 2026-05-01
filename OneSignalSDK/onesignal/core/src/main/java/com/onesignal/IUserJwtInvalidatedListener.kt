package com.onesignal

/**
 * Implement this interface and provide an instance to
 * [IOneSignal.addUserJwtInvalidatedListener] to be notified when the SDK has
 * detected that the JWT for a user is no longer valid (typically a 401 from
 * the OneSignal backend on a request signed with that JWT).
 *
 * Threading: regular fire delivery happens on a background dispatcher. Replay
 * delivery (when an invalidation occurred before any listener was subscribed)
 * happens synchronously on the thread that calls
 * [IOneSignal.addUserJwtInvalidatedListener]. Implementations should not assume
 * a specific thread.
 */
fun interface IUserJwtInvalidatedListener {
    /**
     * Called when the JWT is invalidated for [UserJwtInvalidatedEvent.externalId].
     * Apps should use this signal to fetch a fresh JWT from their backend and
     * supply it via [IOneSignal.updateUserJwt].
     *
     * @param event Describes which user's JWT was invalidated.
     */
    fun onUserJwtInvalidated(event: UserJwtInvalidatedEvent)
}
