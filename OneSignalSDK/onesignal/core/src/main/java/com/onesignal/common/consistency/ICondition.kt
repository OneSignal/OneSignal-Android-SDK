package com.onesignal.common.consistency

interface ICondition {
    fun isMet(indexedOffsets: Map<String, Map<OffsetKey, Long?>>): Boolean
    fun getNewestOffset(offsets: Map<String, Map<OffsetKey, Long?>>): Long?
}
