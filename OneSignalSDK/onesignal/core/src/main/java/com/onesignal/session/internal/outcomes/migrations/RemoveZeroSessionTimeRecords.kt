package com.onesignal.session.internal.outcomes.migrations

import com.onesignal.core.internal.database.IDatabaseProvider
import com.onesignal.session.internal.outcomes.impl.OutcomeEventsTable

/**
 * Purpose: Clean up invalid cached os__session_duration outcome records
 * with zero session_time produced in SDK versions 5.1.15 to 5.1.20 so we stop
 * sending these requests to the backend.
 *
 * Issue: SessionService.backgroundRun() didn't account for it being run more
 * than one time in the background, when this happened it would create a
 * outcome record with zero time which is invalid.
 */
object RemoveZeroSessionTimeRecords {
    fun run(databaseProvider: IDatabaseProvider) {
        databaseProvider.os.delete(
            OutcomeEventsTable.TABLE_NAME,
            OutcomeEventsTable.COLUMN_NAME_NAME + " = \"os__session_duration\"" +
                " AND " + OutcomeEventsTable.COLUMN_NAME_SESSION_TIME + " = 0",
            null,
        )
    }
}
