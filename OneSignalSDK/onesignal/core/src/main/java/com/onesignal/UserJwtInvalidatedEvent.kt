package com.onesignal

/** TODO: jwt documentation
 * The event passed into [IUserJwtInvalidatedListener.onUserJwtInvalidated], it provides access
 * to the external ID whose JWT has just been invalidated.
 *
 */
class UserJwtInvalidatedEvent(
        val external_id: String
) {

}