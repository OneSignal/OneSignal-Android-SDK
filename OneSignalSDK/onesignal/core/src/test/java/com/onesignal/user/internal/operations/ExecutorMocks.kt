package com.onesignal.user.internal.operations

import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.time.impl.Time
import com.onesignal.mocks.MockHelper
import com.onesignal.mocks.MockPreferencesService
import com.onesignal.user.internal.jwt.JwtTokenStore
import com.onesignal.user.internal.operations.impl.states.NewRecordsState

class ExecutorMocks {
    companion object {
        fun getNewRecordState(configModelStore: ConfigModelStore = MockHelper.configModelStore()) = NewRecordsState(Time(), configModelStore)

        /** Real (empty) JWT store backed by a mock preferences service. `getJwt` returns null for all keys. */
        internal fun getJwtTokenStore() = JwtTokenStore(MockPreferencesService())
    }
}
