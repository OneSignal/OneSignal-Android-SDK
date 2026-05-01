package com.onesignal.user.internal.jwt

import com.onesignal.IUserJwtInvalidatedListener
import com.onesignal.UserJwtInvalidatedEvent
import com.onesignal.common.events.EventProducer
import com.onesignal.common.threading.OneSignalDispatchers
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
 *
 * Notifies two distinct audiences on JWT changes:
 *  - SDK-internal subscribers via [IJwtUpdateListener] ([addInternalUpdateListener]).
 *  - Developer-facing subscribers via [IUserJwtInvalidatedListener]
 *    ([addUserJwtInvalidatedListener]). Pure pub/sub: only listeners subscribed at the time
 *    of [invalidateJwt] receive the event. Matches iOS — no buffering for late subscribers.
 */
class JwtTokenStore(
    private val _prefs: IPreferencesService,
) {
    private val tokens: MutableMap<String, String> = mutableMapOf()
    private var isLoaded: Boolean = false
    private val internalUpdateListeners = EventProducer<IJwtUpdateListener>()
    private val publicInvalidatedListeners = EventProducer<IUserJwtInvalidatedListener>()

    fun addInternalUpdateListener(listener: IJwtUpdateListener) = internalUpdateListeners.subscribe(listener)

    fun removeInternalUpdateListener(listener: IJwtUpdateListener) = internalUpdateListeners.unsubscribe(listener)

    fun addUserJwtInvalidatedListener(listener: IUserJwtInvalidatedListener) =
        publicInvalidatedListeners.subscribe(listener)

    fun removeUserJwtInvalidatedListener(listener: IUserJwtInvalidatedListener) =
        publicInvalidatedListeners.unsubscribe(listener)

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
            internalUpdateListeners.fire { it.onJwtUpdated(externalId) }
        }
    }

    /**
     * Removes the JWT for [externalId] and notifies developer-facing subscribers via
     * [IUserJwtInvalidatedListener]. Surfaced to the developer as "your JWT is no
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
            // Dispatch developer-facing event on a background thread so the SDK's internal
            // thread (op-repo / HYDRATE paths) doesn't run app code synchronously.
            // Per-subscriber try/catch so one throwing listener doesn't break others or
            // propagate up into the operation queue (would otherwise drop the failing op).
            OneSignalDispatchers.launchOnDefault {
                publicInvalidatedListeners.fire { listener ->
                    runCatching { listener.onUserJwtInvalidated(UserJwtInvalidatedEvent(externalId)) }
                        .onFailure { ex ->
                            Logging.warn("JwtTokenStore: IUserJwtInvalidatedListener threw for externalId=$externalId", ex)
                        }
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
            internalUpdateListeners.fire { it.onJwtUpdated(externalId) }
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
