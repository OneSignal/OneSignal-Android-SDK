package com.onesignal.user.internal.jwt

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class IdentityVerificationGatesTests : FunSpec({
    // Singleton state leaks across tests; reset before each.
    beforeEach {
        IdentityVerificationGates.update(false, null, "test-reset")
    }

    test("defaults to newCodePathsRun=false and ivBehaviorActive=false") {
        IdentityVerificationGates.newCodePathsRun shouldBe false
        IdentityVerificationGates.ivBehaviorActive shouldBe false
    }

    test("featureFlagOn=false, jwtRequired=null: both gates are false (safe default)") {
        IdentityVerificationGates.update(
            featureFlagOn = false,
            jwtRequired = null,
            source = "test",
        )
        IdentityVerificationGates.newCodePathsRun shouldBe false
        IdentityVerificationGates.ivBehaviorActive shouldBe false
    }

    test("featureFlagOn=false, jwtRequired=false: both gates are false") {
        IdentityVerificationGates.update(
            featureFlagOn = false,
            jwtRequired = false,
            source = "test",
        )
        IdentityVerificationGates.newCodePathsRun shouldBe false
        IdentityVerificationGates.ivBehaviorActive shouldBe false
    }

    test("ERROR STATE — featureFlagOn=false, jwtRequired=true: both gates true (customer config wins)") {
        IdentityVerificationGates.update(
            featureFlagOn = false,
            jwtRequired = true,
            source = "test",
        )
        IdentityVerificationGates.newCodePathsRun shouldBe true
        IdentityVerificationGates.ivBehaviorActive shouldBe true
    }

    test("featureFlagOn=true, jwtRequired=null: newCodePathsRun true, ivBehaviorActive false") {
        IdentityVerificationGates.update(
            featureFlagOn = true,
            jwtRequired = null,
            source = "test",
        )
        IdentityVerificationGates.newCodePathsRun shouldBe true
        IdentityVerificationGates.ivBehaviorActive shouldBe false
    }

    test("featureFlagOn=true, jwtRequired=false: newCodePathsRun true, ivBehaviorActive false (Phase 3)") {
        IdentityVerificationGates.update(
            featureFlagOn = true,
            jwtRequired = false,
            source = "test",
        )
        IdentityVerificationGates.newCodePathsRun shouldBe true
        IdentityVerificationGates.ivBehaviorActive shouldBe false
    }

    test("featureFlagOn=true, jwtRequired=true: both gates true (full IV)") {
        IdentityVerificationGates.update(
            featureFlagOn = true,
            jwtRequired = true,
            source = "test",
        )
        IdentityVerificationGates.newCodePathsRun shouldBe true
        IdentityVerificationGates.ivBehaviorActive shouldBe true
    }

    test("updating to the same values is a no-op but still reflects in reads") {
        IdentityVerificationGates.update(true, true, "first")
        IdentityVerificationGates.update(true, true, "second")
        IdentityVerificationGates.newCodePathsRun shouldBe true
        IdentityVerificationGates.ivBehaviorActive shouldBe true
    }

    test("transition: non-IV → IV-active → off") {
        IdentityVerificationGates.update(false, false, "phase-1-non-iv")
        IdentityVerificationGates.newCodePathsRun shouldBe false
        IdentityVerificationGates.ivBehaviorActive shouldBe false

        IdentityVerificationGates.update(true, true, "phase-2-iv-on")
        IdentityVerificationGates.newCodePathsRun shouldBe true
        IdentityVerificationGates.ivBehaviorActive shouldBe true

        IdentityVerificationGates.update(false, false, "kill-switch")
        IdentityVerificationGates.newCodePathsRun shouldBe false
        IdentityVerificationGates.ivBehaviorActive shouldBe false
    }
})
