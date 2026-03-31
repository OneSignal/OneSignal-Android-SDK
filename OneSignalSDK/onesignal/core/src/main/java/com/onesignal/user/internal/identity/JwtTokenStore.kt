package com.onesignal.user.internal.identity

import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.debug.internal.logging.Logging
import org.json.JSONObject

/**
 * Persistent store mapping externalId -> JWT token. Supports multiple users simultaneously
 * so that queued operations for a previous user can still resolve their JWT at execution time.
 *
 * Storage is unconditional (callers store JWTs regardless of the identity-verification flag).
 * Only *usage* of JWTs (Authorization header, gating, alias resolution) is gated on
 * [com.onesignal.core.internal.config.ConfigModel.useIdentityVerification].
 */
class JwtTokenStore(
    private val _prefs: IPreferencesService,
) {
    private val tokens: MutableMap<String, String> = mutableMapOf()
    private var isLoaded = false

    /** Not thread-safe; callers must hold `synchronized(tokens)`. */
    private fun ensureLoaded() {
        if (isLoaded) return
        val json =
            _prefs.getString(
                PreferenceStores.ONESIGNAL,
                PreferenceOneSignalKeys.PREFS_OS_JWT_TOKENS,
            )
        if (json != null) {
            try {
                val obj = JSONObject(json)
                for (key in obj.keys()) {
                    tokens[key] = obj.getString(key)
                }
            } catch (e: Exception) {
                Logging.warn("JwtTokenStore: failed to parse persisted tokens, starting fresh", e)
            }
        }
        isLoaded = true
    }

    /** Not thread-safe; callers must hold `synchronized(tokens)`. */
    private fun persist() {
        _prefs.saveString(
            PreferenceStores.ONESIGNAL,
            PreferenceOneSignalKeys.PREFS_OS_JWT_TOKENS,
            JSONObject(tokens.toMap()).toString(),
        )
    }

    /**
     * Returns the JWT for the given [externalId], or null if none is stored.
     */
    fun getJwt(externalId: String): String? {
        synchronized(tokens) {
            ensureLoaded()
            return tokens[externalId]
        }
    }

    /**
     * Stores (or replaces) the JWT for [externalId]. Passing a null [jwt] is a no-op;
     * use [invalidateJwt] to remove a token.
     */
    fun putJwt(
        externalId: String,
        jwt: String?,
    ) {
        if (jwt == null) return
        synchronized(tokens) {
            ensureLoaded()
            tokens[externalId] = jwt
            persist()
        }
    }

    /**
     * Removes the JWT for [externalId], marking it as invalid. Operations for this user
     * will be held until a new JWT is provided via [putJwt].
     */
    fun invalidateJwt(externalId: String) {
        synchronized(tokens) {
            ensureLoaded()
            if (tokens.remove(externalId) != null) {
                persist()
            }
        }
    }

    /**
     * Removes all stored JWTs whose externalId is NOT in [activeIds].
     * Called on cold start after loading persisted operations to prevent unbounded growth.
     */
    fun pruneToExternalIds(activeIds: Set<String>) {
        synchronized(tokens) {
            ensureLoaded()
            val removed = tokens.keys.retainAll(activeIds)
            if (removed) {
                persist()
            }
        }
    }
}
