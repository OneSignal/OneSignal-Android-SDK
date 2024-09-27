package com.onesignal.common.consistency

import com.onesignal.common.consistency.enums.IamFetchRywTokenKey
import com.onesignal.common.consistency.models.ICondition
import com.onesignal.common.consistency.models.IConsistencyKeyEnum

/**
 * Used for read your write consistency when fetching In-App Messages.
 *
 * Params:
 *  key : String - the index of the RYW token map
 */
class IamFetchReadyCondition(
    private val key: String,
) : ICondition {
    companion object {
        const val ID = "IamFetchReadyCondition"
    }

    override val id: String
        get() = ID

    override fun isMet(indexedTokens: Map<String, Map<IConsistencyKeyEnum, String>>): Boolean {
        val tokenMap = indexedTokens[key] ?: return false
        val userUpdateTokenSet = tokenMap[IamFetchRywTokenKey.USER] != null

        /**
         * We always update the session count so we know we will have a userUpdateToken. We don't
         * necessarily make a subscriptionUpdate call on every session. The following logic
         * doesn't consider tokenMap[IamFetchRywTokenKey.SUBSCRIPTION] for this reason. This doesn't
         * mean it isn't considered if present when doing the token comparison.
         */
        return userUpdateTokenSet
    }

    override fun getNewestToken(indexedTokens: Map<String, Map<IConsistencyKeyEnum, String?>>): String? {
        val tokenMap = indexedTokens[key] ?: return null
        // maxOrNull compares lexicographically
        return listOfNotNull(tokenMap[IamFetchRywTokenKey.USER], tokenMap[IamFetchRywTokenKey.SUBSCRIPTION]).maxOrNull()
    }
}
