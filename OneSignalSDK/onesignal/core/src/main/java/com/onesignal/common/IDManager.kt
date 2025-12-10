package com.onesignal.common

import java.util.UUID

/**
 * Manages IDs that are created locally.  Has the ability to generate globally unique identifiers
 * and detect whether a provided ID was generated locally.
 */
object IDManager {
    internal const val LOCAL_PREFIX = "local-"

    /**
     * Create a new local ID to be used temporarily prior to backend generation.
     *
     * @return A new locally generated ID.
     */
    fun createLocalId(): String {
        return "$LOCAL_PREFIX${UUID.randomUUID()}"
    }

    /**
     * Determine whether the ID provided is locally generated.
     *
     * @param id The ID to test.
     *
     * @return true if the [id] provided was created via [createLocalId].
     */
    fun isLocalId(id: String): Boolean = id.startsWith(LOCAL_PREFIX)
}
