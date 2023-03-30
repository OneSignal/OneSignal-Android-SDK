package com.onesignal.core.internal.database.impl

import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.database.IDatabase
import com.onesignal.core.internal.database.IDatabaseProvider
import com.onesignal.session.internal.outcomes.impl.OutcomeTableProvider

internal class DatabaseProvider(
    private val _application: IApplicationService,
) : IDatabaseProvider {

    private val _lock = Any()
    private var _osDatabase: OSDatabase? = null

    override val os: IDatabase
        get() {
            if (_osDatabase == null) {
                synchronized(_lock) {
                    if (_osDatabase == null) {
                        _osDatabase = OSDatabase(OutcomeTableProvider(), _application.appContext)
                    }
                }
            }

            return _osDatabase!!
        }
}
