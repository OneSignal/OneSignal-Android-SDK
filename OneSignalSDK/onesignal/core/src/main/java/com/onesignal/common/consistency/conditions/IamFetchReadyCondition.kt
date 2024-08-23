package com.onesignal.common.consistency.conditions

import com.onesignal.common.consistency.enums.IamFetchOffsetKey
import com.onesignal.common.consistency.models.ICondition

/**
 * Used for read your write consistency when fetching In-App Messages. We have both offsets
 * in memory.
 *
 * Params:
 *  id : String - the index of the offset map
 */
class IamFetchReadyCondition(
    private val id: String,
) : ICondition<IamFetchOffsetKey> {
    override fun isMet(indexedOffsets: Map<String, Map<IamFetchOffsetKey, Long?>>): Boolean {
        val offsetMap = indexedOffsets[id] ?: return false
        return offsetMap[IamFetchOffsetKey.USER_UPDATE] != null && offsetMap[IamFetchOffsetKey.SUBSCRIPTION_UPDATE] != null
    }

    override fun getNewestOffset(offsets: Map<String, Map<IamFetchOffsetKey, Long?>>): Long? {
        val offsetMap = offsets[id] ?: return null
        return listOfNotNull(offsetMap[IamFetchOffsetKey.USER_UPDATE], offsetMap[IamFetchOffsetKey.SUBSCRIPTION_UPDATE]).maxOrNull()
    }
}
