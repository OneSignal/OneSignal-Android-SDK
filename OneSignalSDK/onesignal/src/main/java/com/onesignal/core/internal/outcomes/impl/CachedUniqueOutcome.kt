package com.onesignal.core.internal.outcomes.impl

import com.onesignal.core.internal.influence.InfluenceChannel

internal open class CachedUniqueOutcome(
    private val influenceId: String,
    private val channel: InfluenceChannel
) {
    open fun getInfluenceId() = influenceId
    open fun getChannel() = channel
}
