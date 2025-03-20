package com.onesignal.user.internal.migrations

import com.onesignal.core.internal.startup.IStartableService

/**
 * Purpose: allow to identify and take corrective action for some specific bad states during migration
 *
 * Implement these properties to:
 *  - isInBadState(): return true the condition for the bad state has met
 *  - recover(): take a recovery action if the bad state has been identified
 *  - recoveryMessage: log a message after the bad state is found and the corrective action is taken
 */
interface IMigrationRecovery : IStartableService {
    fun isInBadState(): Boolean

    fun recover()

    fun recoveryMessage(): String
}
