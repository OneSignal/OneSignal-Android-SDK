package com.onesignal.user.internal.jwt

/**
 * Wake-up notification from [JwtTokenStore] when the JWT for [externalId] changes.
 * Listeners must call [JwtTokenStore.getJwt] for the current value — event delivery
 * order is not guaranteed to match mutation order across concurrent writers.
 */
internal interface IJwtUpdateListener {
    fun onJwtUpdated(externalId: String)
}
