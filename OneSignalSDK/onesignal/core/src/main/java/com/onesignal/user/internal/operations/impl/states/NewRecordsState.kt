package com.onesignal.user.internal.operations.impl.states

import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.time.ITime

/**
 * Purpose: Keeps track of ids that were just created on the backend.
 * This list gets used to delay network calls to ensure upcoming
 * requests are ready to be accepted by the backend. Also used for retries
 * as a fallback if the server is under extra load.
 */
class NewRecordsState(
    private val _time: ITime,
    private val _configModelStore: ConfigModelStore,
) {
    // Shared by the operation repo and the session request queue, which run on different threads.
    // Key = a string id
    // Value = A Timestamp in ms of when the id was created
    private val records: MutableMap<String, Long> = mutableMapOf()

    @Synchronized
    fun add(key: String) {
        records[key] = _time.currentTimeMillis
    }

    @Synchronized
    fun canAccess(key: String): Boolean {
        val timeLastMovedOrCreated = records[key] ?: return true
        return _time.currentTimeMillis - timeLastMovedOrCreated >= _configModelStore.model.opRepoPostCreateDelay
    }

    @Synchronized
    fun isInMissingRetryWindow(key: String): Boolean {
        val timeLastMovedOrCreated = records[key] ?: return false
        return _time.currentTimeMillis - timeLastMovedOrCreated <= _configModelStore.model.opRepoPostCreateRetryUpTo
    }
}
