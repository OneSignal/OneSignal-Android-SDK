package com.onesignal

/**
 * Implement this interface and provide an instance to [OneSignal.addUserJwtInvalidatedListener]
 * to be notified when the JWT for a user is invalidated.
 *
 * Callbacks are delivered on a background thread.
 */
interface IUserJwtInvalidatedListener {
    /**
     * Called when the JWT is invalidated for [UserJwtInvalidatedEvent.externalId].
     * Invoked on a background thread; see [IUserJwtInvalidatedListener] class documentation.
     *
     * @param event Describes which user's JWT was invalidated.
     */
    fun onUserJwtInvalidated(event: UserJwtInvalidatedEvent)
}
