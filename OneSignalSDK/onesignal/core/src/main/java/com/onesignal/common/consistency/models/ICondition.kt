package com.onesignal.common.consistency.models

interface ICondition {
    /**
     * Every implementation should define a unique ID & make available via a companion object for
     * ease of use
     */
    val id: String

    /**
     * Define a condition that "unblocks" execution
     * e.g. we have token (A && B) || A
     */
    fun isMet(indexedTokens: Map<String, Map<IConsistencyKeyEnum, String>>): Boolean

    /**
     * Used to process tokens according to their format & return the newest token.
     * e.g. numeric strings would be compared differently from JWT tokens
     */
    fun getNewestToken(indexedTokens: Map<String, Map<IConsistencyKeyEnum, String?>>): String?
}
