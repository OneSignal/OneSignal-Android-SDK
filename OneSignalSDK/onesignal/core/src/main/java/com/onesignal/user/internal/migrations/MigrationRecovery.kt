package com.onesignal.user.internal.migrations

import com.onesignal.debug.internal.logging.Logging

abstract class MigrationRecovery : IMigrationRecovery {
    abstract override fun isInBadState(): Boolean

    abstract override fun recover()

    abstract override fun recoveryMessage(): String

    override fun start() {
        // log a message and take the corrective action if it is determined that app is in a bad state
        if (isInBadState()) {
            Logging.warn(recoveryMessage())
            recover()
        }
    }
}
