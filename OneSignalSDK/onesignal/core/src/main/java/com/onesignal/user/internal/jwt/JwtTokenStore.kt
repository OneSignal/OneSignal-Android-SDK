package com.onesignal.user.internal.jwt

import com.onesignal.common.events.EventProducer
import com.onesignal.common.events.IEventNotifier
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.debug.internal.logging.Logging
import org.json.JSONException
import org.json.JSONObject

/**
 * Persistent store mapping externalId -> JWT. Multi-user so ops queued under a previous user
 * can still resolve their JWT at execution time. Storage is unconditional; *usage* of JWTs is
 * gated on `IdentityVerificationService.ivBehaviorActive`.
 */
class JwtTokenStore(
    private val _prefs: IPreferencesService,
) : IEventNotifier<IJwtUpdateListener> {
    private val tokens: MutableMap<String, String> = mutableMapOf()
    private var isLoaded: Boolean = false
    private val updates = EventProducer<IJwtUpdateListener>()

    override val hasSubscribers: Boolean
        get() = updates.hasSubscribers

    override fun subscribe(handler: IJwtUpdateListener) = updates.subscribe(handler)

    override fun unsubscribe(handler: IJwtUpdateListener) = updates.unsubscribe(handler)

    fun getJwt(externalId: String): String? {
        synchronized(tokens) {
            ensureLoaded()
            return tokens[externalId]
        }
    }

    /** Null [jwt] is a no-op; call [invalidateJwt] to remove a token. */
    fun putJwt(
        externalId: String,
        jwt: String?,
    ) {
        if (jwt == null) return
        val changed: Boolean
        synchronized(tokens) {
            ensureLoaded()
            changed = tokens[externalId] != jwt
            tokens[externalId] = jwt
            if (changed) {
                persist()
            }
        }
        if (changed) {
            updates.fire { it.onJwtUpdated(externalId) }
        }
    }

    /**
     * Removes the JWT for [externalId] and notifies subscribers via
     * [IJwtUpdateListener.onJwtInvalidated]. Surfaced to the developer as "your JWT is no
     * longer valid; please refresh." Don't call from internal cleanup paths (logout, user
     * switch) — use a different mechanism if you need to clear without notifying the app.
     */
    fun invalidateJwt(externalId: String) {
        val existed: Boolean
        synchronized(tokens) {
            ensureLoaded()
            existed = tokens.remove(externalId) != null
            if (existed) {
                persist()
            }
        }
        if (existed) {
            // Per-subscriber try/catch so one throwing listener doesn't break others or
            // propagate up into the operation queue (would otherwise drop the failing op).
            updates.fire { listener ->
                runCatching { listener.onJwtInvalidated(externalId) }
                    .onFailure { ex ->
                        Logging.warn("JwtTokenStore: subscriber threw on onJwtInvalidated for externalId=$externalId: ${ex.message}")
                    }
            }
        }
    }

    /** Drops JWTs whose externalId isn't in [activeIds]. Call on cold start to bound growth. */
    fun pruneToExternalIds(activeIds: Set<String>) {
        val removed: Set<String>
        synchronized(tokens) {
            ensureLoaded()
            val toRemove = tokens.keys - activeIds
            removed = toRemove.toSet()
            if (removed.isNotEmpty()) {
                tokens.keys.removeAll(removed)
                persist()
            }
        }
        for (externalId in removed) {
            updates.fire { it.onJwtUpdated(externalId) }
        }
    }

    /** Caller must hold `synchronized(tokens)`. */
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
            } catch (e: JSONException) {
                Logging.warn("JwtTokenStore: failed to parse persisted tokens, starting fresh: ${e.message}")
            }
        }
        isLoaded = true
    }

    /** Caller must hold `synchronized(tokens)`. */
    private fun persist() {
        _prefs.saveString(
            PreferenceStores.ONESIGNAL,
            PreferenceOneSignalKeys.PREFS_OS_JWT_TOKENS,
            JSONObject(tokens.toMap()).toString(),
        )
    }
}
