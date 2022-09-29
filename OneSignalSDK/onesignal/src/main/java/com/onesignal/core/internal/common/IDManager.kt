package com.onesignal.core.internal.common

import java.util.*

/**
 * Manages IDs that are initially generated locally, then regenerated on the backend. Generally when
 * resources are created they are first created with a locally generated ID.  Once the resource
 * has been created on the backend that local ID is replaced with the backend one.
 *
 * The [IDManager] is able to handle IDs created locally in one application instance, and detected
 * as local/translated in a subsequent application instance.
 */
internal object IDManager {
    private const val LOCAL_PREFIX = "local-"
    private val _localIdMap = mutableMapOf<String, String?>()

    /**
     * Create a new local ID to be used temporarily prior to backend generation.
     *
     * @return A new locally generated ID.
     */
    fun createLocalId(): String {
        val newID = "$LOCAL_PREFIX${UUID.randomUUID()}"
        _localIdMap[newID] = null
        return newID
    }

    /**
     * Set the backend-generated ID for the previously locally-generated ID.
     *
     * @param localId The locally-generated ID previously generated via [createLocalId].
     * @param backendId The backend-generated ID.
     */
    fun setLocalToBackendIdTranslation(localId: String, backendId: String) {
        _localIdMap[localId] = backendId
    }

    /**
     * Determine whether the ID provided is locally generated, and has yet to have a backend-generated ID.
     *
     * @param id The ID to test.
     *
     * @return true if the [id] provided was created via [createLocalId], but has not yet had
     * a backend-generated ID set via [setLocalToBackendIdTranslation].
     */
    fun isIdLocalOnly(id: String): Boolean = id.startsWith(LOCAL_PREFIX)

    /**
     * Retrieve the provided ID.  Will retrieve the backend-generated ID if there is one, falling
     * back to the provided ID if there is not.
     *
     * @param id The ID to retrieve.
     *
     * @return The backend-generated ID, or the ID provided if there is no backend-generated ID.
     */
    fun retrieveId(id: String): String = _localIdMap[id] ?: id
}
