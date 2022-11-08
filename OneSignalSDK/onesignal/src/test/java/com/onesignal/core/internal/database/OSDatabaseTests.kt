package com.onesignal.core.internal.database

import android.content.ContentValues
import androidx.test.core.app.ApplicationProvider
import com.onesignal.core.internal.database.impl.OSDatabase
import com.onesignal.core.internal.database.impl.OneSignalDbContract
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.extensions.RobolectricTest
import com.onesignal.session.internal.outcomes.impl.OutcomeTableProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.runner.junit4.KotestTestRunner
import io.mockk.mockk
import org.junit.runner.RunWith

@RobolectricTest
@RunWith(KotestTestRunner::class)
class OSDatabaseTests : FunSpec({

    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    beforeTest {
        var initialDb = InitialOSDatabase(ApplicationProvider.getApplicationContext())

        initialDb.writableDatabase.use {
            it.beginTransaction()
            val values = ContentValues()
            values.put(
                OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID,
                1
            )
            it.insertOrThrow(
                OneSignalDbContract.NotificationTable.TABLE_NAME,
                null,
                values
            )
            it.setTransactionSuccessful()
            it.endTransaction()
        }
        initialDb.close()
    }

    test("upgrade database from v1 To v3") {
        /* Given */
        val outcomeTableProvider = mockk<OutcomeTableProvider>()
        val db = OSDatabase(outcomeTableProvider, ApplicationProvider.getApplicationContext(), 3)

        /* When */
        var createdTime: Long = 0
        var expireTime: Long = 0
        db.query(OneSignalDbContract.NotificationTable.TABLE_NAME) {
            it.moveToFirst()
            createdTime = it.getLong(OneSignalDbContract.NotificationTable.COLUMN_NAME_CREATED_TIME)
            expireTime = it.getLong(OneSignalDbContract.NotificationTable.COLUMN_NAME_EXPIRE_TIME)
        }

        /* Then */
        expireTime shouldBe createdTime + (72L * (60 * 60))
    }
})
