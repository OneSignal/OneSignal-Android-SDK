package com.onesignal

/**
 * The event passed into [IUserJwtInvalidatedListener.onUserJwtInvalidated], it provides access
 * to the external ID whose JWT has just been invalidated.
 *
 */
class UserJwtInvalidatedEvent(
    val externalId: String,
)
