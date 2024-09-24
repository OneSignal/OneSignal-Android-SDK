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
        val subscriptionUpdateTokenSet = tokenMap[IamFetchRywTokenKey.SUBSCRIPTION] != null

        /**
         * We always update the session count so we know we will have a userUpdateToken. We don't
         * necessarily make a subscriptionUpdate call on every session. The following logic
         * is written in a way so that if somehow the subscriptionUpdateToken is set *before* the
         * userUpdateToken, we will wait for the userUpdateToken to also be set. This is because
         * we know that a userUpdate call was made and both user & subscription properties are
         * considered during segment calculations.
         */
        return (userUpdateTokenSet && subscriptionUpdateTokenSet) || userUpdateTokenSet
    }

    override fun getNewestToken(indexedTokens: Map<String, Map<IConsistencyKeyEnum, String?>>): String? {
        val tokenMap = indexedTokens[key] ?: return null
        // maxOrNull compares lexicographically
        return listOfNotNull(tokenMap[IamFetchRywTokenKey.USER], tokenMap[IamFetchRywTokenKey.SUBSCRIPTION]).maxOrNull()
    }
}
