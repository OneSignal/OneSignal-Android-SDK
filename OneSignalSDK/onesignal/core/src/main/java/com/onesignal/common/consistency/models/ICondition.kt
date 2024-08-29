package com.onesignal.common.consistency.models

interface ICondition<K : Enum<K>> {
    fun isMet(indexedOffsets: Map<String, Map<K, Long?>>): Boolean

    fun getNewestOffset(offsets: Map<String, Map<K, Long?>>): Long?
}
