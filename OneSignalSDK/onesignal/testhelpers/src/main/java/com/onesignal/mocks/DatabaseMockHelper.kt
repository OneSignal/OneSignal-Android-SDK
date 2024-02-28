package com.onesignal.mocks

import com.onesignal.core.internal.database.ICursor
import com.onesignal.core.internal.database.IDatabase
import com.onesignal.core.internal.database.IDatabaseProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk

/**
 * Singleton which provides common mock services.
 */
object DatabaseMockHelper {
    fun databaseProvider(
        tableName: String,
        records: List<Map<String, Any>>? = null,
    ): Pair<IDatabaseProvider, IDatabase> {
        val mockOneSignalDatabase = spyk<IDatabase>()

        if (records != null) {
            val mockCursor = cursor(records!!)
            every {
                mockOneSignalDatabase.query(tableName, any(), any(), any(), any(), any(), any(), any(), any())
            } answers {
                lastArg<(ICursor) -> Unit>().invoke(mockCursor)
            }
        }

        val mockDatabaseProvider = mockk<IDatabaseProvider>()
        every { mockDatabaseProvider.os } returns mockOneSignalDatabase

        return Pair(mockDatabaseProvider, mockOneSignalDatabase)
    }

    fun cursor(records: List<Map<String, Any>>): ICursor {
        val mockCursor = mockk<ICursor>()
        var index = 0
        every { mockCursor.count } returns records.count()
        every { mockCursor.moveToFirst() } answers {
            index = 0
            true
        }
        every { mockCursor.moveToNext() } answers {
            index++
            index < records.count()
        }
        every { mockCursor.getString(any()) } answers { records[index][firstArg()] as String }
        every { mockCursor.getFloat(any()) } answers { records[index][firstArg()] as Float }
        every { mockCursor.getLong(any()) } answers { records[index][firstArg()] as Long }
        every { mockCursor.getInt(any()) } answers { records[index][firstArg()] as Int }
        every { mockCursor.getOptString(any()) } answers { records[index][firstArg()] as String? }
        every { mockCursor.getOptFloat(any()) } answers { records[index][firstArg()] as Float? }
        every { mockCursor.getOptLong(any()) } answers { records[index][firstArg()] as Long? }
        every { mockCursor.getOptInt(any()) } answers { records[index][firstArg()] as Int? }
        return mockCursor
    }
}
