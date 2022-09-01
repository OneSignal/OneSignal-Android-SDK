package com.onesignal.core.internal.database.impl

import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.database.IDatabase
import com.onesignal.core.internal.database.IDatabaseProvider
import com.onesignal.core.internal.outcomes.impl.OSOutcomeTableProvider

internal class DatabaseProvider(
    private val _application: IApplicationService
) : IDatabaseProvider {

    override fun get(): IDatabase {
        return OSDatabase(OSOutcomeTableProvider(), _application.appContext)
    }
}
