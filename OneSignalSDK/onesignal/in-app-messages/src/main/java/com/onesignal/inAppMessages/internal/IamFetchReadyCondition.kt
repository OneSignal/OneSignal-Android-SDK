package com.onesignal.inAppMessages.internal

import com.onesignal.common.consistency.enums.IamFetchRywTokenKey
import com.onesignal.common.consistency.impl.ConsistencyManager
import com.onesignal.common.consistency.models.ICondition
import com.onesignal.common.consistency.models.IConsistencyKeyEnum

/**
 * Used for read your write consistency when fetching In-App Messages.
 *
 * Params:
 *  id : String - the index of the RYW token map
 */
class IamFetchReadyCondition(
    private val id: String,
) : ICondition {
    override fun isMet(indexedTokens: Map<String, Map<IConsistencyKeyEnum, String?>>): Boolean {
        val tokenMap = indexedTokens[id] ?: return false
        val userUpdateTokenSet = tokenMap[IamFetchRywTokenKey.USER] != null
        val subscriptionUpdateTokenSet = tokenMap[IamFetchRywTokenKey.SUBSCRIPTION] != null

        /**
         * If the RYW token was not included in one of the 4 endpoints (PATCH & POST * user,
         * subscription), we should resolve the condition so as to not block IAM fetch unnecessarily
         * This can happen if there is a regression where we stop including the tokens in the res
         * body or we turn off the feature temporarily
         */
        val subscriptionOrUserTokenWasNotSet =
            tokenMap[IamFetchRywTokenKey.SUBSCRIPTION] == ConsistencyManager.RYW_TOKEN_NOT_PROVIDED ||
                tokenMap[IamFetchRywTokenKey.USER] == ConsistencyManager.RYW_TOKEN_NOT_PROVIDED

        if (subscriptionOrUserTokenWasNotSet) {
            return true
        }

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
        val tokenMap = indexedTokens[id] ?: return null
        // maxOrNull compares lexicographically
        return listOfNotNull(tokenMap[IamFetchRywTokenKey.USER], tokenMap[IamFetchRywTokenKey.SUBSCRIPTION]).maxOrNull()
    }
}
