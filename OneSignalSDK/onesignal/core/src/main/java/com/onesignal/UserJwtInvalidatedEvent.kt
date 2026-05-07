package com.onesignal

/**
 * The event passed into [IUserJwtInvalidatedListener.onUserJwtInvalidated].
 * Delivery occurs on a background thread.
 */
class UserJwtInvalidatedEvent(
    val externalId: String,
)
