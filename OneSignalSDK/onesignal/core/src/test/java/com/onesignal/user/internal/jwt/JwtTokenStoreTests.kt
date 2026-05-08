package com.onesignal.user.internal.jwt

import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.MockPreferencesService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.json.JSONObject

class JwtTokenStoreTests : FunSpec({
    beforeEach {
        // Silence logging to avoid android.util.Log.w not-mocked failures in Logging.warn
        Logging.logLevel = LogLevel.NONE
    }

    test("getJwt returns null for an externalId never stored") {
        val store = JwtTokenStore(MockPreferencesService())

        store.getJwt("alice") shouldBe null
    }

    test("putJwt stores a token retrievable by externalId") {
        val store = JwtTokenStore(MockPreferencesService())

        store.putJwt("alice", "token-a")

        store.getJwt("alice") shouldBe "token-a"
    }

    test("putJwt replaces an existing token for the same externalId") {
        val store = JwtTokenStore(MockPreferencesService())
        store.putJwt("alice", "token-a1")

        store.putJwt("alice", "token-a2")

        store.getJwt("alice") shouldBe "token-a2"
    }

    test("putJwt with null is a no-op (invalidate is the explicit path)") {
        val store = JwtTokenStore(MockPreferencesService())
        store.putJwt("alice", "token-a")

        store.putJwt("alice", null)

        store.getJwt("alice") shouldBe "token-a"
    }

    test("invalidateJwt removes the token for externalId") {
        val store = JwtTokenStore(MockPreferencesService())
        store.putJwt("alice", "token-a")

        store.invalidateJwt("alice")

        store.getJwt("alice") shouldBe null
    }

    test("invalidateJwt on an absent externalId is a no-op (no crash)") {
        val store = JwtTokenStore(MockPreferencesService())

        store.invalidateJwt("alice")

        store.getJwt("alice") shouldBe null
    }

    test("putJwt persists to preferences and can be recovered by a fresh store instance") {
        val prefs = MockPreferencesService()
        val first = JwtTokenStore(prefs)
        first.putJwt("alice", "token-a")
        first.putJwt("bob", "token-b")

        val second = JwtTokenStore(prefs)

        second.getJwt("alice") shouldBe "token-a"
        second.getJwt("bob") shouldBe "token-b"
    }

    test("invalidateJwt persists so next launch does not see the token") {
        val prefs = MockPreferencesService()
        val first = JwtTokenStore(prefs)
        first.putJwt("alice", "token-a")
        first.invalidateJwt("alice")

        val second = JwtTokenStore(prefs)

        second.getJwt("alice") shouldBe null
    }

    test("pruneToExternalIds removes tokens whose externalId is not in the active set") {
        val store = JwtTokenStore(MockPreferencesService())
        store.putJwt("alice", "token-a")
        store.putJwt("bob", "token-b")
        store.putJwt("chris", "token-c")

        store.pruneToExternalIds(setOf("alice", "chris"))

        store.getJwt("alice") shouldBe "token-a"
        store.getJwt("bob") shouldBe null
        store.getJwt("chris") shouldBe "token-c"
    }

    test("subscribers are notified when a new JWT is put") {
        val store = JwtTokenStore(MockPreferencesService())
        val calls = mutableListOf<String>()
        store.addInternalUpdateListener(
            object : IJwtUpdateListener {
                override fun onJwtUpdated(externalId: String) {
                    calls.add(externalId)
                }
            },
        )

        store.putJwt("alice", "token-a")

        calls shouldBe listOf("alice")
    }

    test("subscribers are NOT notified when putJwt does not change the stored token") {
        val store = JwtTokenStore(MockPreferencesService())
        store.putJwt("alice", "token-a")
        val calls = mutableListOf<String>()
        store.addInternalUpdateListener(
            object : IJwtUpdateListener {
                override fun onJwtUpdated(externalId: String) {
                    calls.add(externalId)
                }
            },
        )

        store.putJwt("alice", "token-a")

        calls.isEmpty() shouldBe true
    }

    test("invalidateJwt does NOT fire onJwtUpdated to internal listeners") {
        val store = JwtTokenStore(MockPreferencesService())
        store.putJwt("alice", "token-a")
        val updatedCalls = mutableListOf<String>()
        store.addInternalUpdateListener(
            object : IJwtUpdateListener {
                override fun onJwtUpdated(externalId: String) {
                    updatedCalls.add(externalId)
                }
            },
        )

        // putJwt above already fired onJwtUpdated once; invalidateJwt should not add another.
        updatedCalls.size shouldBe 0
        store.invalidateJwt("alice")
        updatedCalls.size shouldBe 0
    }

    test("IUserJwtInvalidatedListener is NOT notified when invalidating a non-existent token") {
        val store = JwtTokenStore(MockPreferencesService())
        val invalidatedCalls = mutableListOf<String>()
        store.addUserJwtInvalidatedListener { event -> invalidatedCalls.add(event.externalId) }

        store.invalidateJwt("alice")
        Thread.sleep(50) // allow async dispatch (no-op here, but defensive)

        invalidatedCalls.isEmpty() shouldBe true
    }

    test("throwing IUserJwtInvalidatedListener subscriber is isolated; other subscribers still fire") {
        val store = JwtTokenStore(MockPreferencesService())
        store.putJwt("alice", "token-a")
        val laterCalls = mutableListOf<String>()
        val waiter = com.onesignal.common.threading.Waiter()
        store.addUserJwtInvalidatedListener { _ -> throw RuntimeException("boom") }
        store.addUserJwtInvalidatedListener { event ->
            laterCalls.add(event.externalId)
            waiter.wake()
        }

        store.invalidateJwt("alice")
        waiter.waitForWake()

        laterCalls shouldBe listOf("alice")
        store.getJwt("alice") shouldBe null
    }

    test("pruneToExternalIds fires for each removed externalId") {
        val store = JwtTokenStore(MockPreferencesService())
        store.putJwt("alice", "token-a")
        store.putJwt("bob", "token-b")
        store.putJwt("chris", "token-c")
        val calls = mutableListOf<String>()
        store.addInternalUpdateListener(
            object : IJwtUpdateListener {
                override fun onJwtUpdated(externalId: String) {
                    calls.add(externalId)
                }
            },
        )

        store.pruneToExternalIds(setOf("alice"))

        // Order is not deterministic across JVMs; check set semantics.
        calls.toSet() shouldBe setOf("bob", "chris")
    }

    test("unsubscribed listener is not notified") {
        val store = JwtTokenStore(MockPreferencesService())
        val calls = mutableListOf<String>()
        val listener =
            object : IJwtUpdateListener {
                override fun onJwtUpdated(externalId: String) {
                    calls.add(externalId)
                }
            }
        store.addInternalUpdateListener(listener)
        store.removeInternalUpdateListener(listener)

        store.putJwt("alice", "token-a")

        calls.isEmpty() shouldBe true
    }

    test("persisted JSON is the expected shape") {
        val prefs = MockPreferencesService()
        val store = JwtTokenStore(prefs)

        store.putJwt("alice", "token-a")
        store.putJwt("bob", "token-b")

        val raw = prefs.getString(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_OS_JWT_TOKENS)
        val obj = JSONObject(requireNotNull(raw))
        obj.getString("alice") shouldBe "token-a"
        obj.getString("bob") shouldBe "token-b"
    }

    test("malformed persisted JSON starts fresh without crashing") {
        val prefs =
            MockPreferencesService(
                mapOf(PreferenceOneSignalKeys.PREFS_OS_JWT_TOKENS to "{not valid json"),
            )
        val store = JwtTokenStore(prefs)

        store.getJwt("alice") shouldBe null
        // Can still store new tokens after a malformed load
        store.putJwt("alice", "token-a")
        store.getJwt("alice") shouldBe "token-a"
    }

    test("invalidateJwt fires registered IUserJwtInvalidatedListener with the externalId") {
        val store = JwtTokenStore(MockPreferencesService())
        store.putJwt("alice", "stale-token")

        var firedExternalId: String? = null
        val waiter = com.onesignal.common.threading.Waiter()
        store.addUserJwtInvalidatedListener { event ->
            firedExternalId = event.externalId
            waiter.wake()
        }

        store.invalidateJwt("alice")
        waiter.waitForWake()

        firedExternalId shouldBe "alice"
    }

    test("late IUserJwtInvalidatedListener subscriber does not receive earlier events (pure pub/sub)") {
        // Matches iOS: only listeners subscribed at the time of the fire receive the event.
        val store = JwtTokenStore(MockPreferencesService())
        store.putJwt("alice", "stale-token")

        // Fire before any developer listener is registered.
        store.invalidateJwt("alice")
        Thread.sleep(50) // allow async dispatch

        // Late subscriber must not receive the earlier event.
        var lateFired = false
        store.addUserJwtInvalidatedListener { lateFired = true }
        Thread.sleep(50)
        lateFired shouldBe false
    }

    test("removeUserJwtInvalidatedListener stops further notifications") {
        val store = JwtTokenStore(MockPreferencesService())
        store.putJwt("alice", "token")

        var fireCount = 0
        val listener = com.onesignal.IUserJwtInvalidatedListener { _ -> fireCount++ }
        store.addUserJwtInvalidatedListener(listener)
        store.removeUserJwtInvalidatedListener(listener)

        store.invalidateJwt("alice")
        Thread.sleep(50)

        fireCount shouldBe 0
    }
})
