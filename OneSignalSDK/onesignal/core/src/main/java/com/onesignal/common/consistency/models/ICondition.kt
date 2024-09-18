package com.onesignal.common.consistency.models

interface ICondition {
    fun isMet(indexedTokens: Map<String, Map<IConsistencyKeyEnum, Long?>>): Boolean

    fun getNewestToken(indexedTokens: Map<String, Map<IConsistencyKeyEnum, Long?>>): Long?
}
