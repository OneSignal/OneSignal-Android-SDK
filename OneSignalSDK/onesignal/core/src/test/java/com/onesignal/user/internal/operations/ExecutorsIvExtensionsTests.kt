package com.onesignal.user.internal.operations

import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.MockPreferencesService
import com.onesignal.user.internal.backend.IdentityConstants
import com.onesignal.user.internal.jwt.JwtTokenStore
import com.onesignal.user.internal.operations.impl.executors.IvBackendParams
import com.onesignal.user.internal.operations.impl.executors.resolveIvBackendParams
import com.onesignal.user.internal.operations.impl.executors.resolveIvJwt
import com.onesignal.user.internal.operations.impl.executors.shouldFailLoginUserFromSubscription
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ExecutorsIvExtensionsTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("resolveIvBackendParams returns legacy values when ivBehaviorActive is false (Phase 3)") {
        // Given: new code path on but behavior inactive
        val jwtStore = JwtTokenStore(MockPreferencesService())
        jwtStore.putJwt("ext-1", "jwt-value")
        val op = LoginUserOperation("app", "os-1", "ext-1", null)

        // When
        val params = resolveIvBackendParams(op, "os-1", jwtStore, ivBehaviorActive = false)

        // Then: onesignal_id alias, no JWT
        params shouldBe IvBackendParams(IdentityConstants.ONESIGNAL_ID, "os-1", null)
    }

    test("resolveIvBackendParams returns external_id alias + JWT when IV active and externalId present") {
        // Given
        val jwtStore = JwtTokenStore(MockPreferencesService())
        jwtStore.putJwt("ext-1", "jwt-value")
        val op = LoginUserOperation("app", "os-1", "ext-1", null)

        // When
        val params = resolveIvBackendParams(op, "os-1", jwtStore, ivBehaviorActive = true)

        // Then
        params shouldBe IvBackendParams(IdentityConstants.EXTERNAL_ID, "ext-1", "jwt-value")
    }

    test("resolveIvBackendParams falls back to onesignal_id when IV active but externalId null (defensive)") {
        // Given: IV active, op has no externalId (shouldn't happen per hasValidJwtIfRequired gating)
        val jwtStore = JwtTokenStore(MockPreferencesService())
        val op = LoginUserOperation("app", "os-1", null, null)

        // When
        val params = resolveIvBackendParams(op, "os-1", jwtStore, ivBehaviorActive = true)

        // Then
        params shouldBe IvBackendParams(IdentityConstants.ONESIGNAL_ID, "os-1", null)
    }

    test("resolveIvBackendParams returns null JWT when IV active but no JWT stored (defensive)") {
        // Given: IV active, externalId set, but no JWT in store
        val jwtStore = JwtTokenStore(MockPreferencesService())
        val op = LoginUserOperation("app", "os-1", "ext-1", null)

        // When
        val params = resolveIvBackendParams(op, "os-1", jwtStore, ivBehaviorActive = true)

        // Then: alias still switches to external_id but jwt is null
        params shouldBe IvBackendParams(IdentityConstants.EXTERNAL_ID, "ext-1", null)
    }

    test("resolveIvJwt returns null when ivBehaviorActive is false") {
        val jwtStore = JwtTokenStore(MockPreferencesService())
        jwtStore.putJwt("ext-1", "jwt-value")
        val op = LoginUserOperation("app", "os-1", "ext-1", null)

        resolveIvJwt(op, jwtStore, ivBehaviorActive = false) shouldBe null
    }

    test("resolveIvJwt returns stored JWT when IV active and externalId present") {
        val jwtStore = JwtTokenStore(MockPreferencesService())
        jwtStore.putJwt("ext-1", "jwt-value")
        val op = LoginUserOperation("app", "os-1", "ext-1", null)

        resolveIvJwt(op, jwtStore, ivBehaviorActive = true) shouldBe "jwt-value"
    }

    test("resolveIvJwt returns null when IV active but op is anonymous") {
        val jwtStore = JwtTokenStore(MockPreferencesService())
        val op = LoginUserOperation("app", "os-1", null, null)

        resolveIvJwt(op, jwtStore, ivBehaviorActive = true) shouldBe null
    }

    test("shouldFailLoginUserFromSubscription returns false when ivBehaviorActive is false") {
        shouldFailLoginUserFromSubscription(ivBehaviorActive = false) shouldBe false
    }

    test("shouldFailLoginUserFromSubscription returns true when ivBehaviorActive is true") {
        shouldFailLoginUserFromSubscription(ivBehaviorActive = true) shouldBe true
    }
})
