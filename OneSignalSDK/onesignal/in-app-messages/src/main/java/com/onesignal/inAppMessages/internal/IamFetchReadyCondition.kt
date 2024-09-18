package com.onesignal.inAppMessages.internal

import com.onesignal.common.consistency.enums.IamFetchRywTokenKey
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
    override fun isMet(indexedTokens: Map<String, Map<IConsistencyKeyEnum, Long?>>): Boolean {
        val tokenMap = indexedTokens[id] ?: return false
        val userUpdateTokenSet = tokenMap[IamFetchRywTokenKey.USER_UPDATE] != null
        val subscriptionUpdateTokenSet = tokenMap[IamFetchRywTokenKey.SUBSCRIPTION_UPDATE] != null
        return (userUpdateTokenSet && subscriptionUpdateTokenSet) || userUpdateTokenSet
    }

    override fun getNewestToken(indexedTokens: Map<String, Map<IConsistencyKeyEnum, Long?>>): Long? {
        val tokenMap = indexedTokens[id] ?: return null
        return listOfNotNull(tokenMap[IamFetchRywTokenKey.USER_UPDATE], tokenMap[IamFetchRywTokenKey.SUBSCRIPTION_UPDATE]).maxOrNull()
    }
}
