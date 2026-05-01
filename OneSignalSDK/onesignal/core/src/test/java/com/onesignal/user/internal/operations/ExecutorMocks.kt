package com.onesignal.user.internal.operations

import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.config.impl.IdentityVerificationService
import com.onesignal.core.internal.time.impl.Time
import com.onesignal.mocks.MockHelper
import com.onesignal.mocks.MockPreferencesService
import com.onesignal.user.internal.jwt.JwtTokenStore
import com.onesignal.user.internal.operations.impl.states.NewRecordsState
import io.mockk.every
import io.mockk.mockk

class ExecutorMocks {
    companion object {
        fun getNewRecordState(configModelStore: ConfigModelStore = MockHelper.configModelStore()) = NewRecordsState(Time(), configModelStore)

        /** Real (empty) JWT store backed by a mock preferences service. `getJwt` returns null for all keys. */
        internal fun getJwtTokenStore() = JwtTokenStore(MockPreferencesService())

        /** Mocked [IdentityVerificationService] with both gates returning false by default — IV inactive, new code paths off. */
        internal fun getIdentityVerificationService(
            newCodePathsRun: Boolean = false,
            ivBehaviorActive: Boolean = false,
        ): IdentityVerificationService {
            val mock = mockk<IdentityVerificationService>(relaxed = true)
            every { mock.newCodePathsRun } returns newCodePathsRun
            every { mock.ivBehaviorActive } returns ivBehaviorActive
            return mock
        }
    }
}
