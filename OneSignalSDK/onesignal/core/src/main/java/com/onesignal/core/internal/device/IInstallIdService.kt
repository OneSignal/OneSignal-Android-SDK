package com.onesignal.core.internal.device

import java.util.UUID

interface IInstallIdService {
    /**
     * WARNING: This may do disk I/O on the first call, so never call this from
     * the main thread.
     */
    suspend fun getId(): UUID
}
