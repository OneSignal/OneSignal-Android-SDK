package com.onesignal.core.internal.database.impl

import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.database.IDatabase
import com.onesignal.core.internal.database.IDatabaseProvider
import com.onesignal.session.internal.outcomes.impl.OutcomeTableProvider

internal class DatabaseProvider(
    private val _application: IApplicationService,
) : IDatabaseProvider {
    private val lock = Any()
    private var osDatabase: OSDatabase? = null

    override val os: IDatabase
        get() {
            if (osDatabase == null) {
                synchronized(lock) {
                    if (osDatabase == null) {
                        osDatabase = OSDatabase(OutcomeTableProvider(), _application.appContext)
                    }
                }
            }

            return osDatabase!!
        }
}
