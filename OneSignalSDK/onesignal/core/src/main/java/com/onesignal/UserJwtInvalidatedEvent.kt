package com.onesignal

/**
 * The event passed into [IUserJwtInvalidatedListener.onUserJwtInvalidated], it provides access
 * to the external ID whose JWT has just been invalidated.
 *
 * For more information https://documentation.onesignal.com/docs/identity-verification#4-handle-jwt-lifecycle-events
 */
class UserJwtInvalidatedEvent(
    val externalId: String,
)
