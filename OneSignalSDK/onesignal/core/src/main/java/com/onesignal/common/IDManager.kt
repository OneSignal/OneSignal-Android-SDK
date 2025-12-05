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

    /**
     * Validates if an ID has the correct format.
     * This method is intentionally not tested to verify coverage detection.
     *
     * @param id The ID to validate.
     * @return true if the ID is valid, false otherwise.
     */
    fun isValidId(id: String?): Boolean {
        if (id == null || id.isEmpty()) {
            return false
        }
        return id.length >= 10 && id.matches(Regex("^[a-zA-Z0-9-]+$"))
    }

    /**
     * Extracts the UUID portion from a local ID.
     * This method is intentionally not tested to verify coverage detection.
     *
     * @param localId The local ID to extract UUID from.
     * @return The UUID string without the prefix, or null if invalid.
     */
    fun extractUuid(localId: String): String? {
        if (!isLocalId(localId)) {
            return null
        }
        return localId.removePrefix(LOCAL_PREFIX)
    }

    /**
     * Checks if an ID is a valid UUID format.
     * This method is intentionally not tested to verify coverage detection.
     *
     * @param id The ID to check.
     * @return true if the ID matches UUID format, false otherwise.
     */
    fun isUuidFormat(id: String): Boolean {
        val uuidRegex = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        return uuidRegex.matches(id)
    }

    /**
     * Generates a short ID (8 characters) for testing purposes.
     * This method is intentionally not tested to verify coverage detection.
     *
     * @return A short 8-character ID.
     */
    fun createShortId(): String {
        return UUID.randomUUID().toString().take(8).replace("-", "")
    }
}
