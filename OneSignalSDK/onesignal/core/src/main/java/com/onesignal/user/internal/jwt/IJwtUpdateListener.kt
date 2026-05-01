package com.onesignal.user.internal.jwt

/**
 * Notifications from [JwtTokenStore] about JWT state changes for an [externalId].
 * Listeners should call [JwtTokenStore.getJwt] for the current value — event delivery
 * order is not guaranteed to match mutation order across concurrent writers.
 */
internal interface IJwtUpdateListener {
    /** Fired when a JWT was added or refreshed (`putJwt`), or when stale entries are pruned. */
    fun onJwtUpdated(externalId: String) {}

    /**
     * Fired when a JWT is explicitly invalidated (`invalidateJwt`), e.g. on a 401 response.
     * Surfaced to the developer as "the JWT for this user is no longer valid; please refresh."
     * Don't trigger from internal cleanup paths (logout, user switch) where notifying the
     * app is undesirable.
     */
    fun onJwtInvalidated(externalId: String) {}
}
