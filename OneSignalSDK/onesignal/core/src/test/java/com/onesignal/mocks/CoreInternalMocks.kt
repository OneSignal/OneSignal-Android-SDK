package com.onesignal.mocks

import com.onesignal.core.internal.config.impl.IdentityVerificationService
import io.mockk.every
import io.mockk.mockk

/**
 * Helpers for core-internal types that can't live in [MockHelper] (testhelpers module can't see
 * `internal` declarations from core).
 */
internal object CoreInternalMocks {
    fun identityVerificationService(
        newCodePathsRun: Boolean = false,
        ivBehaviorActive: Boolean = false,
    ): IdentityVerificationService {
        val mock = mockk<IdentityVerificationService>(relaxed = true)
        every { mock.newCodePathsRun } returns newCodePathsRun
        every { mock.ivBehaviorActive } returns ivBehaviorActive
        return mock
    }
}
