package com.onesignal.session.internal.outcomes.impl

/**
 * The preferences service for outcomes.
 */
internal interface IOutcomeEventsPreferences {
    /**
     * Unique outcome events sent for UNATTRIBUTED sessions on a per session level
     */
    var unattributedUniqueOutcomeEventsSentByChannel: Set<String>?
}
