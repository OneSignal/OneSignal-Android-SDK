package com.onesignal.session.internal.outcomes

import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.DatabaseMockHelper
import com.onesignal.mocks.TestDispatcherProvider
import com.onesignal.session.internal.influence.Influence
import com.onesignal.session.internal.influence.InfluenceChannel
import com.onesignal.session.internal.influence.InfluenceType
import com.onesignal.session.internal.outcomes.impl.CachedUniqueOutcomeTable
import com.onesignal.session.internal.outcomes.impl.OutcomeEventParams
import com.onesignal.session.internal.outcomes.impl.OutcomeEventsRepository
import com.onesignal.session.internal.outcomes.impl.OutcomeEventsTable
import com.onesignal.session.internal.outcomes.impl.OutcomeSource
import com.onesignal.session.internal.outcomes.impl.OutcomeSourceBody
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.verify
import io.mockk.verifyAll
import io.mockk.verifySequence
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONArray

@RobolectricTest
class OutcomeEventsRepositoryTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    // avoids initialization happening too early (before Robolectric’s environment exists).
    lateinit var testDispatcher: TestDispatcher
    lateinit var dispatcherProvider: TestDispatcherProvider

    beforeTest {
        testDispatcher = StandardTestDispatcher()
        dispatcherProvider = TestDispatcherProvider(testDispatcher)
    }

    test("delete outcome event should use the timestamp to delete row from database") {
        runTest(dispatcherProvider.io) {
            val mockDatabasePair = DatabaseMockHelper.databaseProvider(OutcomeEventsTable.TABLE_NAME)
            val outcomeEventsRepository = OutcomeEventsRepository(mockDatabasePair.first, testDispatcher)

            // When
            outcomeEventsRepository.deleteOldOutcomeEvent(OutcomeEventParams("outcomeId", null, 0f, 0, 1111))

            // Then
            verify(exactly = 1) {
                mockDatabasePair.second.delete(
                    OutcomeEventsTable.TABLE_NAME,
                    withArg {
                        it.contains(OutcomeEventsTable.COLUMN_NAME_TIMESTAMP)
                    },
                    withArg { it.contains("1111") },
                )
            }
        }
    }

    test("save outcome event should insert row into database") {
        runTest(dispatcherProvider.io) {
            // Given
            val mockDatabasePair = DatabaseMockHelper.databaseProvider(OutcomeEventsTable.TABLE_NAME)
            val outcomeEventsRepository = OutcomeEventsRepository(mockDatabasePair.first, testDispatcher)

            // When
            outcomeEventsRepository.saveOutcomeEvent(OutcomeEventParams("outcomeId1", null, 0f, 0, 1111))
            outcomeEventsRepository.saveOutcomeEvent(
                OutcomeEventParams(
                    "outcomeId2",
                    OutcomeSource(
                        OutcomeSourceBody(JSONArray().put("notificationId1")),
                        OutcomeSourceBody(null, JSONArray().put("iamId1").put("iamId2")),
                    ),
                    .2f,
                    0,
                    2222,
                ),
            )
            outcomeEventsRepository.saveOutcomeEvent(
                OutcomeEventParams(
                    "outcomeId3",
                    OutcomeSource(
                        OutcomeSourceBody(JSONArray().put("notificationId1"), JSONArray().put("iamId1")),
                        null,
                    ),
                    .4f,
                    0,
                    3333,
                ),
            )
            outcomeEventsRepository.saveOutcomeEvent(
                OutcomeEventParams(
                    "outcomeId4",
                    OutcomeSource(
                        null,
                        OutcomeSourceBody(JSONArray().put("notificationId1"), JSONArray().put("iamId1").put("iamId2")),
                    ),
                    .6f,
                    0,
                    4444,
                ),
            )

            // Then
            verifySequence {
                mockDatabasePair.second.insert(
                    OutcomeEventsTable.TABLE_NAME,
                    null,
                    withArg {
                        it[OutcomeEventsTable.COLUMN_NAME_NAME] shouldBe "outcomeId1"
                        it[OutcomeEventsTable.COLUMN_NAME_WEIGHT] shouldBe 0f
                        it[OutcomeEventsTable.COLUMN_NAME_TIMESTAMP] shouldBe 1111L
                        it[OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_INFLUENCE_TYPE] shouldBe "unattributed"
                        it[OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_IDS] shouldBe JSONArray().toString()
                        it[OutcomeEventsTable.COLUMN_NAME_IAM_INFLUENCE_TYPE] shouldBe "unattributed"
                        it[OutcomeEventsTable.COLUMN_NAME_IAM_IDS] shouldBe JSONArray().toString()
                    },
                )
                mockDatabasePair.second.insert(
                    OutcomeEventsTable.TABLE_NAME,
                    null,
                    withArg {
                        it[OutcomeEventsTable.COLUMN_NAME_NAME] shouldBe "outcomeId2"
                        it[OutcomeEventsTable.COLUMN_NAME_WEIGHT] shouldBe .2f
                        it[OutcomeEventsTable.COLUMN_NAME_TIMESTAMP] shouldBe 2222L
                        it[OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_INFLUENCE_TYPE] shouldBe "direct"
                        it[OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_IDS] shouldBe JSONArray("[\"notificationId1\"]").toString()
                        it[OutcomeEventsTable.COLUMN_NAME_IAM_INFLUENCE_TYPE] shouldBe "indirect"
                        it[OutcomeEventsTable.COLUMN_NAME_IAM_IDS] shouldBe JSONArray("[\"iamId1\", \"iamId2\"]").toString()
                    },
                )
                mockDatabasePair.second.insert(
                    OutcomeEventsTable.TABLE_NAME,
                    null,
                    withArg {
                        it[OutcomeEventsTable.COLUMN_NAME_NAME] shouldBe "outcomeId3"
                        it[OutcomeEventsTable.COLUMN_NAME_WEIGHT] shouldBe .4f
                        it[OutcomeEventsTable.COLUMN_NAME_TIMESTAMP] shouldBe 3333L
                        it[OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_INFLUENCE_TYPE] shouldBe "direct"
                        it[OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_IDS] shouldBe JSONArray("[\"notificationId1\"]").toString()
                        it[OutcomeEventsTable.COLUMN_NAME_IAM_INFLUENCE_TYPE] shouldBe "direct"
                        it[OutcomeEventsTable.COLUMN_NAME_IAM_IDS] shouldBe JSONArray("[\"iamId1\"]").toString()
                    },
                )
                mockDatabasePair.second.insert(
                    OutcomeEventsTable.TABLE_NAME,
                    null,
                    withArg {
                        it[OutcomeEventsTable.COLUMN_NAME_NAME] shouldBe "outcomeId4"
                        it[OutcomeEventsTable.COLUMN_NAME_WEIGHT] shouldBe .6f
                        it[OutcomeEventsTable.COLUMN_NAME_TIMESTAMP] shouldBe 4444L
                        it[OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_INFLUENCE_TYPE] shouldBe "indirect"
                        it[OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_IDS] shouldBe JSONArray("[\"notificationId1\"]").toString()
                        it[OutcomeEventsTable.COLUMN_NAME_IAM_INFLUENCE_TYPE] shouldBe "indirect"
                        it[OutcomeEventsTable.COLUMN_NAME_IAM_IDS] shouldBe JSONArray("[\"iamId1\", \"iamId2\"]").toString()
                    },
                )
            }
        }
    }

    test("get events should retrieve return empty list when database is empty") {
        runTest(dispatcherProvider.io) {
            // Given
            val mockDatabasePair = DatabaseMockHelper.databaseProvider(OutcomeEventsTable.TABLE_NAME)
            val outcomeEventsRepository = OutcomeEventsRepository(mockDatabasePair.first, testDispatcher)

            // When
            val events = outcomeEventsRepository.getAllEventsToSend()

            // Then
            events.count() shouldBe 0
        }
    }

    test("get events should retrieve return an item per row in database") {
        runTest(dispatcherProvider.io) {
            // Given
            val records =
                listOf(
                    mapOf(
                        OutcomeEventsTable.COLUMN_NAME_NAME to "outcomeId1",
                        OutcomeEventsTable.COLUMN_NAME_WEIGHT to 0.2f,
                        OutcomeEventsTable.COLUMN_NAME_TIMESTAMP to 1111L,
                        OutcomeEventsTable.COLUMN_NAME_SESSION_TIME to 1L,
                        OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_INFLUENCE_TYPE to "unattributed",
                        OutcomeEventsTable.COLUMN_NAME_IAM_INFLUENCE_TYPE to "unattributed",
                    ),
                    mapOf(
                        OutcomeEventsTable.COLUMN_NAME_NAME to "outcomeId2",
                        OutcomeEventsTable.COLUMN_NAME_WEIGHT to 0.4f,
                        OutcomeEventsTable.COLUMN_NAME_TIMESTAMP to 2222L,
                        OutcomeEventsTable.COLUMN_NAME_SESSION_TIME to 2L,
                        OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_INFLUENCE_TYPE to "indirect",
                        OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_IDS to "[\"notificationId1\",\"notificationId2\"]",
                        OutcomeEventsTable.COLUMN_NAME_IAM_INFLUENCE_TYPE to "indirect",
                        OutcomeEventsTable.COLUMN_NAME_IAM_IDS to "[\"iamId1\",\"iamId2\"]",
                    ),
                    mapOf(
                        OutcomeEventsTable.COLUMN_NAME_NAME to "outcomeId3",
                        OutcomeEventsTable.COLUMN_NAME_WEIGHT to 0.6f,
                        OutcomeEventsTable.COLUMN_NAME_TIMESTAMP to 3333L,
                        OutcomeEventsTable.COLUMN_NAME_SESSION_TIME to 3L,
                        OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_INFLUENCE_TYPE to "direct",
                        OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_IDS to "[\"notificationId3\"]",
                        OutcomeEventsTable.COLUMN_NAME_IAM_INFLUENCE_TYPE to "direct",
                        OutcomeEventsTable.COLUMN_NAME_IAM_IDS to "[\"iamId3\"]",
                    ),
                )
            val mockDatabasePair = DatabaseMockHelper.databaseProvider(OutcomeEventsTable.TABLE_NAME, records)
            val outcomeEventsRepository = OutcomeEventsRepository(mockDatabasePair.first, testDispatcher)

            // When
            val events = outcomeEventsRepository.getAllEventsToSend()

            // Then
            events.count() shouldBe 3
            events[0].outcomeId shouldBe "outcomeId1"
            events[0].weight shouldBe 0.2f
            events[0].timestamp shouldBe 1111L
            events[0].sessionTime shouldBe 1L
            events[0].outcomeSource shouldNotBe null
            events[0].outcomeSource!!.directBody shouldBe null
            events[0].outcomeSource!!.indirectBody shouldBe null
            events[1].outcomeId shouldBe "outcomeId2"
            events[1].weight shouldBe 0.4f
            events[1].timestamp shouldBe 2222L
            events[1].sessionTime shouldBe 2L
            events[1].outcomeSource shouldNotBe null
            events[1].outcomeSource!!.directBody shouldBe null
            events[1].outcomeSource!!.indirectBody shouldNotBe null
            events[1].outcomeSource!!.indirectBody!!.notificationIds!!.length() shouldBe 2
            events[1].outcomeSource!!.indirectBody!!.notificationIds!!.getString(0) shouldBe "notificationId1"
            events[1].outcomeSource!!.indirectBody!!.notificationIds!!.getString(1) shouldBe "notificationId2"
            events[1].outcomeSource!!.indirectBody!!.inAppMessagesIds!!.length() shouldBe 2
            events[1].outcomeSource!!.indirectBody!!.inAppMessagesIds!!.getString(0) shouldBe "iamId1"
            events[1].outcomeSource!!.indirectBody!!.inAppMessagesIds!!.getString(1) shouldBe "iamId2"
            events[2].outcomeId shouldBe "outcomeId3"
            events[2].weight shouldBe 0.6f
            events[2].timestamp shouldBe 3333L
            events[2].sessionTime shouldBe 3L
            events[2].outcomeSource shouldNotBe null
            events[2].outcomeSource!!.indirectBody shouldBe null
            events[2].outcomeSource!!.directBody shouldNotBe null
            events[2].outcomeSource!!.directBody!!.notificationIds!!.length() shouldBe 1
            events[2].outcomeSource!!.directBody!!.notificationIds!!.getString(0) shouldBe "notificationId3"
            events[2].outcomeSource!!.directBody!!.inAppMessagesIds!!.length() shouldBe 1
            events[2].outcomeSource!!.directBody!!.inAppMessagesIds!!.getString(0) shouldBe "iamId3"
        }
    }

    test("save unique outcome should insert no rows when no influences") {
        runTest(dispatcherProvider.io) {
            // Given
            val mockDatabasePair = DatabaseMockHelper.databaseProvider(CachedUniqueOutcomeTable.COLUMN_NAME_NAME)
            val outcomeEventsRepository = OutcomeEventsRepository(mockDatabasePair.first, testDispatcher)

            // When
            outcomeEventsRepository.saveUniqueOutcomeEventParams(OutcomeEventParams("outcomeId1", null, 0f, 0, 1111))

            // Then
            verify(exactly = 0) { mockDatabasePair.second.insert(CachedUniqueOutcomeTable.COLUMN_NAME_NAME, null, any()) }
        }
    }

    test("save unique outcome should insert 1 row for each unique influence when direct notification and indiract iam") {
        runTest(dispatcherProvider.io) {
            // Given
            val mockDatabasePair = DatabaseMockHelper.databaseProvider(CachedUniqueOutcomeTable.TABLE_NAME)
            val outcomeEventsRepository = OutcomeEventsRepository(mockDatabasePair.first, testDispatcher)

            // When
            outcomeEventsRepository.saveUniqueOutcomeEventParams(
                OutcomeEventParams(
                    "outcomeId1",
                    OutcomeSource(
                        OutcomeSourceBody(JSONArray().put("notificationId1")),
                        OutcomeSourceBody(null, JSONArray().put("iamId1").put("iamId2")),
                    ),
                    .2f,
                    0,
                    2222,
                ),
            )

            // Then
            verifyAll {
                mockDatabasePair.second.insert(
                    CachedUniqueOutcomeTable.TABLE_NAME,
                    null,
                    withArg {
                        it[CachedUniqueOutcomeTable.COLUMN_NAME_NAME] shouldBe "outcomeId1"
                        it[CachedUniqueOutcomeTable.COLUMN_CHANNEL_TYPE] shouldBe "notification"
                        it[CachedUniqueOutcomeTable.COLUMN_CHANNEL_INFLUENCE_ID] shouldBe "notificationId1"
                    },
                )
                mockDatabasePair.second.insert(
                    CachedUniqueOutcomeTable.TABLE_NAME,
                    null,
                    withArg {
                        it[CachedUniqueOutcomeTable.COLUMN_NAME_NAME] shouldBe "outcomeId1"
                        it[CachedUniqueOutcomeTable.COLUMN_CHANNEL_TYPE] shouldBe "iam"
                        it[CachedUniqueOutcomeTable.COLUMN_CHANNEL_INFLUENCE_ID] shouldBe "iamId1"
                    },
                )
                mockDatabasePair.second.insert(
                    CachedUniqueOutcomeTable.TABLE_NAME,
                    null,
                    withArg {
                        it[CachedUniqueOutcomeTable.COLUMN_NAME_NAME] shouldBe "outcomeId1"
                        it[CachedUniqueOutcomeTable.COLUMN_CHANNEL_TYPE] shouldBe "iam"
                        it[CachedUniqueOutcomeTable.COLUMN_CHANNEL_INFLUENCE_ID] shouldBe "iamId2"
                    },
                )
            }
        }
    }

    test("save unique outcome should insert 1 row for each unique influence when direct iam and indiract notifications") {
        runTest(dispatcherProvider.io) {
            // Given
            val mockDatabasePair = DatabaseMockHelper.databaseProvider(CachedUniqueOutcomeTable.TABLE_NAME)
            val outcomeEventsRepository = OutcomeEventsRepository(mockDatabasePair.first, testDispatcher)

            // When
            outcomeEventsRepository.saveUniqueOutcomeEventParams(
                OutcomeEventParams(
                    "outcomeId1",
                    OutcomeSource(
                        OutcomeSourceBody(null, JSONArray().put("iamId1")),
                        OutcomeSourceBody(JSONArray().put("notificationId1").put("notificationId2")),
                    ),
                    .2f,
                    0,
                    2222,
                ),
            )

            // Then
            verifyAll {
                mockDatabasePair.second.insert(
                    CachedUniqueOutcomeTable.TABLE_NAME,
                    null,
                    withArg {
                        it[CachedUniqueOutcomeTable.COLUMN_NAME_NAME] shouldBe "outcomeId1"
                        it[CachedUniqueOutcomeTable.COLUMN_CHANNEL_TYPE] shouldBe "notification"
                        it[CachedUniqueOutcomeTable.COLUMN_CHANNEL_INFLUENCE_ID] shouldBe "notificationId1"
                    },
                )
                mockDatabasePair.second.insert(
                    CachedUniqueOutcomeTable.TABLE_NAME,
                    null,
                    withArg {
                        it[CachedUniqueOutcomeTable.COLUMN_NAME_NAME] shouldBe "outcomeId1"
                        it[CachedUniqueOutcomeTable.COLUMN_CHANNEL_TYPE] shouldBe "notification"
                        it[CachedUniqueOutcomeTable.COLUMN_CHANNEL_INFLUENCE_ID] shouldBe "notificationId2"
                    },
                )
                mockDatabasePair.second.insert(
                    CachedUniqueOutcomeTable.TABLE_NAME,
                    null,
                    withArg {
                        it[CachedUniqueOutcomeTable.COLUMN_NAME_NAME] shouldBe "outcomeId1"
                        it[CachedUniqueOutcomeTable.COLUMN_CHANNEL_TYPE] shouldBe "iam"
                        it[CachedUniqueOutcomeTable.COLUMN_CHANNEL_INFLUENCE_ID] shouldBe "iamId1"
                    },
                )
            }
        }
    }

    test("save unique outcome should insert 1 row for each unique influence when direct notification and iam") {
        runTest(dispatcherProvider.io) {
            // Given
            val mockDatabasePair = DatabaseMockHelper.databaseProvider(CachedUniqueOutcomeTable.TABLE_NAME)
            val outcomeEventsRepository = OutcomeEventsRepository(mockDatabasePair.first, testDispatcher)

            // When
            outcomeEventsRepository.saveUniqueOutcomeEventParams(
                OutcomeEventParams(
                    "outcomeId1",
                    OutcomeSource(
                        OutcomeSourceBody(JSONArray().put("notificationId1"), JSONArray().put("iamId1")),
                        null,
                    ),
                    .2f,
                    0,
                    2222,
                ),
            )

            // Then
            verifyAll {
                mockDatabasePair.second.insert(
                    CachedUniqueOutcomeTable.TABLE_NAME,
                    null,
                    withArg {
                        it[CachedUniqueOutcomeTable.COLUMN_NAME_NAME] shouldBe "outcomeId1"
                        it[CachedUniqueOutcomeTable.COLUMN_CHANNEL_TYPE] shouldBe "notification"
                        it[CachedUniqueOutcomeTable.COLUMN_CHANNEL_INFLUENCE_ID] shouldBe "notificationId1"
                    },
                )
                mockDatabasePair.second.insert(
                    CachedUniqueOutcomeTable.TABLE_NAME,
                    null,
                    withArg {
                        it[CachedUniqueOutcomeTable.COLUMN_NAME_NAME] shouldBe "outcomeId1"
                        it[CachedUniqueOutcomeTable.COLUMN_CHANNEL_TYPE] shouldBe "iam"
                        it[CachedUniqueOutcomeTable.COLUMN_CHANNEL_INFLUENCE_ID] shouldBe "iamId1"
                    },
                )
            }
        }
    }

    test("save unique outcome should insert 1 row for each unique influence when indirect notification and iam") {
        runTest(dispatcherProvider.io) {
            // Given
            val mockDatabasePair = DatabaseMockHelper.databaseProvider(CachedUniqueOutcomeTable.TABLE_NAME)
            val outcomeEventsRepository = OutcomeEventsRepository(mockDatabasePair.first, testDispatcher)

            // When
            outcomeEventsRepository.saveUniqueOutcomeEventParams(
                OutcomeEventParams(
                    "outcomeId1",
                    OutcomeSource(
                        null,
                        OutcomeSourceBody(JSONArray().put("notificationId1").put("notificationId2"), JSONArray().put("iamId1").put("iamId2")),
                    ),
                    .2f,
                    0,
                    2222,
                ),
            )

            // Then
            verifyAll {
                mockDatabasePair.second.insert(
                    CachedUniqueOutcomeTable.TABLE_NAME,
                    null,
                    withArg {
                        it[CachedUniqueOutcomeTable.COLUMN_NAME_NAME] shouldBe "outcomeId1"
                        it[CachedUniqueOutcomeTable.COLUMN_CHANNEL_TYPE] shouldBe "notification"
                        it[CachedUniqueOutcomeTable.COLUMN_CHANNEL_INFLUENCE_ID] shouldBe "notificationId1"
                    },
                )
                mockDatabasePair.second.insert(
                    CachedUniqueOutcomeTable.TABLE_NAME,
                    null,
                    withArg {
                        it[CachedUniqueOutcomeTable.COLUMN_NAME_NAME] shouldBe "outcomeId1"
                        it[CachedUniqueOutcomeTable.COLUMN_CHANNEL_TYPE] shouldBe "notification"
                        it[CachedUniqueOutcomeTable.COLUMN_CHANNEL_INFLUENCE_ID] shouldBe "notificationId2"
                    },
                )
                mockDatabasePair.second.insert(
                    CachedUniqueOutcomeTable.TABLE_NAME,
                    null,
                    withArg {
                        it[CachedUniqueOutcomeTable.COLUMN_NAME_NAME] shouldBe "outcomeId1"
                        it[CachedUniqueOutcomeTable.COLUMN_CHANNEL_TYPE] shouldBe "iam"
                        it[CachedUniqueOutcomeTable.COLUMN_CHANNEL_INFLUENCE_ID] shouldBe "iamId1"
                    },
                )
                mockDatabasePair.second.insert(
                    CachedUniqueOutcomeTable.TABLE_NAME,
                    null,
                    withArg {
                        it[CachedUniqueOutcomeTable.COLUMN_NAME_NAME] shouldBe "outcomeId1"
                        it[CachedUniqueOutcomeTable.COLUMN_CHANNEL_TYPE] shouldBe "iam"
                        it[CachedUniqueOutcomeTable.COLUMN_CHANNEL_INFLUENCE_ID] shouldBe "iamId2"
                    },
                )
            }
        }
    }

    test("retrieve non-cached influence should return full list when there are no cached unique influences") {
        runTest(dispatcherProvider.io) {
            // Given
            val records = listOf<Map<String, Any>>()
            val mockDatabasePair = DatabaseMockHelper.databaseProvider(CachedUniqueOutcomeTable.TABLE_NAME, records)
            val outcomeEventsRepository = OutcomeEventsRepository(mockDatabasePair.first, testDispatcher)

            // When
            val influences =
                outcomeEventsRepository.getNotCachedUniqueInfluencesForOutcome(
                    "outcomeId1",
                    listOf(
                        Influence(InfluenceChannel.NOTIFICATION, InfluenceType.DIRECT, JSONArray().put("notificationId1")),
                        Influence(InfluenceChannel.NOTIFICATION, InfluenceType.DIRECT, JSONArray().put("notificationId2")),
                    ),
                )

            // Then
            influences.count() shouldBe 2
        }
    }

    test("retrieve non-cached influence should filter out an influence when there are is a matching influence") {
        runTest(dispatcherProvider.io) {
            // Given
            val records = listOf(mapOf(CachedUniqueOutcomeTable.COLUMN_NAME_NAME to "outcomeId1"))
            val mockDatabasePair = DatabaseMockHelper.databaseProvider(CachedUniqueOutcomeTable.TABLE_NAME, records)
            val outcomeEventsRepository = OutcomeEventsRepository(mockDatabasePair.first, testDispatcher)

            // When
            val influences =
                outcomeEventsRepository.getNotCachedUniqueInfluencesForOutcome(
                    "outcomeId1",
                    listOf(
                        Influence(InfluenceChannel.NOTIFICATION, InfluenceType.DIRECT, JSONArray().put("notificationId1")),
                        Influence(InfluenceChannel.NOTIFICATION, InfluenceType.DIRECT, JSONArray().put("notificationId2")),
                    ),
                )

            // Then
            influences.count() shouldBe 0
        }
    }

    test("clear unique influence should delete out an influence when there are is a matching influence") {
        runTest(dispatcherProvider.io) {
            // Given
            val mockDatabasePair = DatabaseMockHelper.databaseProvider(CachedUniqueOutcomeTable.TABLE_NAME)
            val outcomeEventsRepository = OutcomeEventsRepository(mockDatabasePair.first, testDispatcher)

            // When
            outcomeEventsRepository.cleanCachedUniqueOutcomeEventNotifications()

            // Then
            verifyAll {
                mockDatabasePair.second.delete(CachedUniqueOutcomeTable.TABLE_NAME, any(), any())
            }
        }
    }
})
