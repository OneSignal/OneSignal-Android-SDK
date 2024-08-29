package com.onesignal.common.consistency.models

interface ICondition {
    fun isMet(indexedOffsets: Map<String, Map<IConsistencyKeyEnum, Long?>>): Boolean

    fun getNewestOffset(offsets: Map<String, Map<IConsistencyKeyEnum, Long?>>): Long?
}
