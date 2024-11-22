package com.onesignal

/**
 * Implement this interface and provide an instance to [OneSignal.addUserJwtInvalidatedListener]
 * in order to receive control when the JWT for the current user is invalidated.
 *
 * @see [User JWT Invalidated Event | OneSignal Docs](https://documentation.onesignal.com/docs/identity-verification)
 */
interface IUserJwtInvalidatedListener {
    /**
     * Called when the JWT is invalidated
     *
     * @param event The user JWT that expired.
     */
    fun onUserJwtInvalidated(event: UserJwtInvalidatedEvent)
}
