package com.onesignal.user.internal.operations

import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.time.impl.Time
import com.onesignal.mocks.MockHelper
import com.onesignal.user.internal.operations.impl.states.NewRecordsState

class ExecutorMocks {
    companion object {
        fun getNewRecordState(configModelStore: ConfigModelStore = MockHelper.configModelStore()) = NewRecordsState(Time(), configModelStore)
    }
}
