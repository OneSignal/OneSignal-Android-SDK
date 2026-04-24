package com.onesignal.user.internal.jwt

/** Notified by [JwtTokenStore] on put/invalidate. Null [jwt] means invalidated. */
internal interface IJwtUpdateListener {
    fun onJwtUpdated(externalId: String, jwt: String?)
}
