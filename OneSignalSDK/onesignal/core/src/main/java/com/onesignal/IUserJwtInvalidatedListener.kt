package com.onesignal

/**
 * Implement this interface and provide an instance to
 * [IOneSignal.addUserJwtInvalidatedListener] to be notified when the SDK has
 * detected that the JWT for a user is no longer valid (typically a 401 from
 * the OneSignal backend on a request signed with that JWT).
 *
 * Threading: delivered on a background dispatcher
 * (`OneSignalDispatchers.launchOnDefault`). Implementations should not assume a
 * specific thread and should re-dispatch to the UI thread if needed.
 *
 * Pure pub/sub: only listeners subscribed at the time of the invalidation
 * receive the event. Subscribe early (e.g. in `Application.onCreate`) to avoid
 * missing cold-start 401s.
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
