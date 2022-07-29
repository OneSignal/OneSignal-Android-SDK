package com.onesignal.onesignal.core.internal.outcomes.data

import com.onesignal.onesignal.core.internal.influence.InfluenceChannel

open class CachedUniqueOutcome(private val influenceId: String,
                               private val channel: InfluenceChannel
) {
    open fun getInfluenceId() = influenceId
    open fun getChannel() = channel
}