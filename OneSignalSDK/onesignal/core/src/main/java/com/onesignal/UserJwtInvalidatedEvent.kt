package com.onesignal

/**
 * The event passed into [IUserJwtInvalidatedListener.onUserJwtInvalidated]. Delivery occurs on
 * a background thread; see [IUserJwtInvalidatedListener].
 */
class UserJwtInvalidatedEvent(
    val externalId: String,
)
