package com.onesignal

/**
 * Implement this interface and provide an instance to
 * [IOneSignal.addUserJwtInvalidatedListener] to be notified when the SDK has
 * detected that the JWT for a user is no longer valid (typically a 401 from
 * the OneSignal backend on a request signed with that JWT).
 *
 * Callbacks are delivered on a background thread.
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
