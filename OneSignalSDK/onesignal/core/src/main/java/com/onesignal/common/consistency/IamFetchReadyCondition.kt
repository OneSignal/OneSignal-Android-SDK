package com.onesignal.common.consistency

/**
 * Used for read your write consistency when fetching In-App Messages. We have at least one offset
 * in memory.
 *
 * Params:
 *  id : String - the index of the offset map
 */
class IamFetchReadyCondition(private val id: String) : ICondition {
    override fun isMet(indexedOffsets: Map<String, Map<OffsetKey, Long?>>): Boolean {
        val offsetMap = indexedOffsets[id] ?: return false
        return offsetMap[OffsetKey.USER_UPDATE] != null && offsetMap[OffsetKey.SUBSCRIPTION_UPDATE] != null
    }

    override fun getNewestOffset(indexedOffsets: Map<String, Map<OffsetKey, Long?>>): Long? {
        val offsetMap = indexedOffsets[id] ?: return null
        return listOfNotNull(offsetMap[OffsetKey.USER_UPDATE], offsetMap[OffsetKey.SUBSCRIPTION_UPDATE]).maxOrNull()
    }
}
