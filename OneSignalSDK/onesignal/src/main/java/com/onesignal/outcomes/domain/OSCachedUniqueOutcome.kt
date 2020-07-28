package com.onesignal.outcomes.domain

import com.onesignal.influence.domain.OSInfluenceChannel

open class OSCachedUniqueOutcome(private val influenceId: String,
                                 private val channel: OSInfluenceChannel) {
    open fun getInfluenceId() = influenceId
    open fun getChannel() = channel
}