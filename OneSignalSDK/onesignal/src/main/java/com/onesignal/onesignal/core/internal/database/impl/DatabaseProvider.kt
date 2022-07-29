package com.onesignal.onesignal.core.internal.database.impl

import com.onesignal.onesignal.core.internal.application.IApplicationService
import com.onesignal.onesignal.core.internal.database.IDatabase
import com.onesignal.onesignal.core.internal.database.IDatabaseProvider
import com.onesignal.outcomes.data.OSOutcomeTableProvider

internal class DatabaseProvider(
    private val _application: IApplicationService
) : IDatabaseProvider {

    override fun get() : IDatabase {
        return OSDatabase(OSOutcomeTableProvider(), _application.appContext!!)
    }
}