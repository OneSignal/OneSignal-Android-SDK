package com.onesignal.core.internal.outcomes.impl

import com.onesignal.core.internal.influence.InfluenceChannel

/**
 * A unique outcome that is cached so we are able to ensure uniqueness.  An outcome
 * is unique per influence, per channel.
 */
internal class CachedUniqueOutcome(
    /**
     * The ID of the influence this outcome is associated to.
     */
    val influenceId: String,

    /**
     * The channel this outcome is associated to.
     */
    val channel: InfluenceChannel
)
