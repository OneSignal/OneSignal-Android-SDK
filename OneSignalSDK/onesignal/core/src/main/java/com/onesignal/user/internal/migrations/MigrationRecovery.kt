package com.onesignal.user.internal.migrations

import com.onesignal.debug.internal.logging.Logging

open class MigrationRecovery : IMigrationRecovery {
    override fun isInBadState(): Boolean {
        return false
    }

    override fun recover() {
        // left blank intentionally
    }

    override fun recoveryMessage(): String {
        return ""
    }

    override fun start() {
        // log a message and take the corrective action if it is determined that app is in a bad state
        if (isInBadState()) {
            Logging.warn(recoveryMessage())
            recover()
        }
    }
}
