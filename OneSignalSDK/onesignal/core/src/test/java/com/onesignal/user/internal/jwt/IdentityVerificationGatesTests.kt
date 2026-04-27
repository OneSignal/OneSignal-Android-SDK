package com.onesignal.user.internal.jwt

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class IdentityVerificationGatesTests : FunSpec({
    // Singleton state leaks across tests; reset before each.
    beforeEach {
        IdentityVerificationGates.update(false, JwtRequirement.UNKNOWN, "test-reset")
    }

    test("defaults to newCodePathsRun=false and ivBehaviorActive=false") {
        IdentityVerificationGates.newCodePathsRun shouldBe false
        IdentityVerificationGates.ivBehaviorActive shouldBe false
    }

    test("featureFlagOn=false, jwtRequirement=UNKNOWN: both gates are false (safe default)") {
        IdentityVerificationGates.update(
            featureFlagOn = false,
            jwtRequirement = JwtRequirement.UNKNOWN,
            source = "test",
        )
        IdentityVerificationGates.newCodePathsRun shouldBe false
        IdentityVerificationGates.ivBehaviorActive shouldBe false
    }

    test("featureFlagOn=false, jwtRequirement=NOT_REQUIRED: both gates are false") {
        IdentityVerificationGates.update(
            featureFlagOn = false,
            jwtRequirement = JwtRequirement.NOT_REQUIRED,
            source = "test",
        )
        IdentityVerificationGates.newCodePathsRun shouldBe false
        IdentityVerificationGates.ivBehaviorActive shouldBe false
    }

    test("ERROR STATE — featureFlagOn=false, jwtRequirement=REQUIRED: both gates true (customer config wins)") {
        IdentityVerificationGates.update(
            featureFlagOn = false,
            jwtRequirement = JwtRequirement.REQUIRED,
            source = "test",
        )
        IdentityVerificationGates.newCodePathsRun shouldBe true
        IdentityVerificationGates.ivBehaviorActive shouldBe true
    }

    test("featureFlagOn=true, jwtRequirement=UNKNOWN: newCodePathsRun true, ivBehaviorActive false") {
        IdentityVerificationGates.update(
            featureFlagOn = true,
            jwtRequirement = JwtRequirement.UNKNOWN,
            source = "test",
        )
        IdentityVerificationGates.newCodePathsRun shouldBe true
        IdentityVerificationGates.ivBehaviorActive shouldBe false
    }

    test("featureFlagOn=true, jwtRequirement=NOT_REQUIRED: newCodePathsRun true, ivBehaviorActive false (Phase 3)") {
        IdentityVerificationGates.update(
            featureFlagOn = true,
            jwtRequirement = JwtRequirement.NOT_REQUIRED,
            source = "test",
        )
        IdentityVerificationGates.newCodePathsRun shouldBe true
        IdentityVerificationGates.ivBehaviorActive shouldBe false
    }

    test("featureFlagOn=true, jwtRequirement=REQUIRED: both gates true (full IV)") {
        IdentityVerificationGates.update(
            featureFlagOn = true,
            jwtRequirement = JwtRequirement.REQUIRED,
            source = "test",
        )
        IdentityVerificationGates.newCodePathsRun shouldBe true
        IdentityVerificationGates.ivBehaviorActive shouldBe true
    }

    test("updating to the same values is a no-op but still reflects in reads") {
        IdentityVerificationGates.update(true, JwtRequirement.REQUIRED, "first")
        IdentityVerificationGates.update(true, JwtRequirement.REQUIRED, "second")
        IdentityVerificationGates.newCodePathsRun shouldBe true
        IdentityVerificationGates.ivBehaviorActive shouldBe true
    }

    test("transition: non-IV → IV-active → off") {
        IdentityVerificationGates.update(false, JwtRequirement.NOT_REQUIRED, "phase-1-non-iv")
        IdentityVerificationGates.newCodePathsRun shouldBe false
        IdentityVerificationGates.ivBehaviorActive shouldBe false

        IdentityVerificationGates.update(true, JwtRequirement.REQUIRED, "phase-2-iv-on")
        IdentityVerificationGates.newCodePathsRun shouldBe true
        IdentityVerificationGates.ivBehaviorActive shouldBe true

        IdentityVerificationGates.update(false, JwtRequirement.NOT_REQUIRED, "kill-switch")
        IdentityVerificationGates.newCodePathsRun shouldBe false
        IdentityVerificationGates.ivBehaviorActive shouldBe false
    }
})
