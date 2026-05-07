package com.onesignal.user.internal.jwt

/**
 * SDK-internal notifications from [JwtTokenStore] about JWT state changes for an [externalId].
 * Fires when a JWT is added/refreshed via `putJwt` or stale entries are pruned. The
 * developer-facing 401-invalidation event is delivered separately via
 * [com.onesignal.IUserJwtInvalidatedListener] (see [JwtTokenStore.addUserJwtInvalidatedListener]).
 */
interface IJwtUpdateListener {
    /** Fired when a JWT was added or refreshed (`putJwt`), or when stale entries are pruned. */
    fun onJwtUpdated(externalId: String)
}
