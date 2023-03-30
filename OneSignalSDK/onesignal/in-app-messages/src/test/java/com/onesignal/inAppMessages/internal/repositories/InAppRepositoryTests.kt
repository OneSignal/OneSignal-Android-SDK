package com.onesignal.inAppMessages.internal.repositories

import com.onesignal.core.internal.database.impl.OneSignalDbContract
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.inAppMessages.internal.preferences.IInAppPreferencesController
import com.onesignal.inAppMessages.internal.repositories.impl.InAppRepository
import com.onesignal.inAppMessages.mocks.DatabaseMockHelper
import com.onesignal.inAppMessages.mocks.MockHelper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.runner.junit4.KotestTestRunner
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.runner.RunWith

@RunWith(KotestTestRunner::class)
class InAppRepositoryTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("listInAppMessages returns empty list when database is empty") {
        /* Given */
        val mockDatabasePair = DatabaseMockHelper.databaseProvider(OneSignalDbContract.InAppMessageTable.TABLE_NAME)
        val mockInAppPreferencesController = mockk<IInAppPreferencesController>()

        val inAppRepository = InAppRepository(mockDatabasePair.first, MockHelper.time(1111), mockInAppPreferencesController)

        /* When */
        val response = inAppRepository.listInAppMessages()

        /* Then */
        response.count() shouldBe 0
    }

    test("listInAppMessages returns list of in app messages in database") {
        /* Given */
        val records = listOf(
            mapOf<String, Any>(
                OneSignalDbContract.InAppMessageTable.COLUMN_NAME_MESSAGE_ID to "messageId1",
                OneSignalDbContract.InAppMessageTable.COLUMN_CLICK_IDS to "[clickId1, clickId2]",
                OneSignalDbContract.InAppMessageTable.COLUMN_NAME_DISPLAY_QUANTITY to 1,
                OneSignalDbContract.InAppMessageTable.COLUMN_NAME_LAST_DISPLAY to 1000L,
                OneSignalDbContract.InAppMessageTable.COLUMN_DISPLAYED_IN_SESSION to 1,
            ),
            mapOf<String, Any>(
                OneSignalDbContract.InAppMessageTable.COLUMN_NAME_MESSAGE_ID to "messageId2",
                OneSignalDbContract.InAppMessageTable.COLUMN_CLICK_IDS to "[clickId3, clickId4]",
                OneSignalDbContract.InAppMessageTable.COLUMN_NAME_DISPLAY_QUANTITY to 2,
                OneSignalDbContract.InAppMessageTable.COLUMN_NAME_LAST_DISPLAY to 1100L,
                OneSignalDbContract.InAppMessageTable.COLUMN_DISPLAYED_IN_SESSION to 0,
            ),
        )
        val mockDatabasePair = DatabaseMockHelper.databaseProvider(OneSignalDbContract.InAppMessageTable.TABLE_NAME, records)
        val mockInAppPreferencesController = mockk<IInAppPreferencesController>()

        val inAppRepository = InAppRepository(mockDatabasePair.first, MockHelper.time(1111), mockInAppPreferencesController)

        /* When */
        val response = inAppRepository.listInAppMessages()

        /* Then */
        response.count() shouldBe 2
        response[0].messageId shouldBe "messageId1"
        response[0].clickedClickIds shouldBe setOf("clickId1", "clickId2")
        response[0].redisplayStats.displayQuantity shouldBe 1
        response[0].redisplayStats.lastDisplayTime shouldBe 1000
        response[0].isDisplayedInSession shouldBe true
        response[1].messageId shouldBe "messageId2"
        response[1].clickedClickIds shouldBe setOf("clickId3", "clickId4")
        response[1].redisplayStats.displayQuantity shouldBe 2
        response[1].redisplayStats.lastDisplayTime shouldBe 1100
        response[1].isDisplayedInSession shouldBe false
    }

    test("cleanCachedInAppMessages is a no-op when no older messages") {
        /* Given */
        val mockDatabasePair = DatabaseMockHelper.databaseProvider(OneSignalDbContract.InAppMessageTable.TABLE_NAME)
        val mockInAppPreferencesController = mockk<IInAppPreferencesController>()
        every { mockInAppPreferencesController.cleanInAppMessageIds(any()) } just runs
        every { mockInAppPreferencesController.cleanInAppMessageClickedClickIds(any()) } just runs
        val inAppRepository = InAppRepository(mockDatabasePair.first, MockHelper.time(1111), mockInAppPreferencesController)

        /* When */
        inAppRepository.cleanCachedInAppMessages()

        /* Then */
        verify {
            mockInAppPreferencesController.cleanInAppMessageIds(
                withArg {
                    it.size shouldBe 0
                },
            )
        }

        verify {
            mockInAppPreferencesController.cleanInAppMessageClickedClickIds(
                withArg {
                    it.size shouldBe 0
                },
            )
        }
    }

    test("cleanCachedInAppMessages cleans all old messages and clicks") {
        /* Given */
        val records = listOf(
            mapOf<String, Any>(
                OneSignalDbContract.InAppMessageTable.COLUMN_NAME_MESSAGE_ID to "messageId1",
                OneSignalDbContract.InAppMessageTable.COLUMN_CLICK_IDS to "[clickId1, clickId2]",
            ),
            mapOf<String, Any>(
                OneSignalDbContract.InAppMessageTable.COLUMN_NAME_MESSAGE_ID to "messageId2",
                OneSignalDbContract.InAppMessageTable.COLUMN_CLICK_IDS to "[clickId3, clickId4]",
            ),
        )
        val mockDatabasePair = DatabaseMockHelper.databaseProvider(OneSignalDbContract.InAppMessageTable.TABLE_NAME, records)
        val mockInAppPreferencesController = mockk<IInAppPreferencesController>()
        every { mockInAppPreferencesController.cleanInAppMessageIds(any()) } just runs
        every { mockInAppPreferencesController.cleanInAppMessageClickedClickIds(any()) } just runs
        val inAppRepository = InAppRepository(mockDatabasePair.first, MockHelper.time(1111), mockInAppPreferencesController)

        /* When */
        inAppRepository.cleanCachedInAppMessages()

        /* Then */
        verify {
            mockInAppPreferencesController.cleanInAppMessageIds(
                withArg {
                    it.size shouldBe 2
                    it.containsAll(listOf("messageId1", "messageId2"))
                },
            )
        }

        verify {
            mockInAppPreferencesController.cleanInAppMessageClickedClickIds(
                withArg {
                    it.size shouldBe 4
                    it.containsAll(listOf("clickId1", "clickId2", "clickId3", "clickId4"))
                },
            )
        }
    }
})
