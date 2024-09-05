package com.onesignal.inAppMessages.internal

import com.onesignal.common.consistency.enums.IamFetchOffsetKey
import com.onesignal.common.consistency.models.ICondition
import com.onesignal.common.consistency.models.IConsistencyKeyEnum

/**
 * Used for read your write consistency when fetching In-App Messages. We have both offsets
 * in memory.
 *
 * Params:
 *  id : String - the index of the offset map
 */
class IamFetchReadyCondition(
    private val id: String,
) : ICondition {
    override fun isMet(indexedOffsets: Map<String, Map<IConsistencyKeyEnum, Long?>>): Boolean {
        val offsetMap = indexedOffsets[id] ?: return false
        val userUpdateOffsetSet = offsetMap[IamFetchOffsetKey.USER_UPDATE] != null
        val subscriptionUpdateOffsetSet = offsetMap[IamFetchOffsetKey.SUBSCRIPTION_UPDATE] != null
        return (userUpdateOffsetSet && subscriptionUpdateOffsetSet) || userUpdateOffsetSet
    }

    override fun getNewestOffset(offsets: Map<String, Map<IConsistencyKeyEnum, Long?>>): Long? {
        val offsetMap = offsets[id] ?: return null
        return listOfNotNull(offsetMap[IamFetchOffsetKey.USER_UPDATE], offsetMap[IamFetchOffsetKey.SUBSCRIPTION_UPDATE]).maxOrNull()
    }
}
