package com.onesignal.common.consistency.impl

import com.onesignal.common.consistency.enums.IamFetchOffsetKey
import com.onesignal.common.consistency.models.ICondition
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest

class ConsistencyManagerTests : FunSpec({

    lateinit var consistencyManager: ConsistencyManager<IamFetchOffsetKey>

    beforeAny {
        consistencyManager = ConsistencyManager()
    }

    test("setOffset updates the offset correctly") {
        runTest {
            // Given
            val id = "test_id"
            val key = IamFetchOffsetKey.USER_UPDATE
            val value = 123L

            consistencyManager.setOffset(id, key, value)

            val condition = TestMetCondition(mapOf(id to mapOf(key to value)))
            val deferred = consistencyManager.registerCondition(condition)
            val result = deferred.await()

            result shouldBe value
        }
    }

    test("registerCondition completes when condition is met") {
        runTest {
            // Given
            val id = "test_id"
            val key = IamFetchOffsetKey.USER_UPDATE
            val value = 123L

            // Set an offset to meet the condition
            consistencyManager.setOffset(id, key, value)

            val condition = TestMetCondition(mapOf(id to mapOf(key to value)))
            val deferred = consistencyManager.registerCondition(condition)

            deferred.await()
            deferred.isCompleted shouldBe true
        }
    }

    test("registerCondition does not complete when condition is not met") {
        runTest {
            val condition = TestUnmetCondition()
            val deferred = consistencyManager.registerCondition(condition)

            consistencyManager.setOffset("id", IamFetchOffsetKey.USER_UPDATE, 123L)
            deferred.isCompleted shouldBe false
        }
    }
}) {
    // Mock implementation of ICondition that simulates a condition that isn't met
    private class TestUnmetCondition : ICondition<IamFetchOffsetKey> {
        override fun isMet(offsets: Map<String, Map<IamFetchOffsetKey, Long?>>): Boolean {
            return false // Always returns false to simulate an unmet condition
        }

        override fun getNewestOffset(offsets: Map<String, Map<IamFetchOffsetKey, Long?>>): Long? {
            return null
        }
    }

    // Mock implementation of ICondition for cases where the condition is met
    private class TestMetCondition(
        private val expectedOffsets: Map<String, Map<IamFetchOffsetKey, Long?>>,
    ) : ICondition<IamFetchOffsetKey> {
        override fun isMet(offsets: Map<String, Map<IamFetchOffsetKey, Long?>>): Boolean {
            return offsets == expectedOffsets
        }

        override fun getNewestOffset(offsets: Map<String, Map<IamFetchOffsetKey, Long?>>): Long? {
            return expectedOffsets.values.firstOrNull()?.values?.firstOrNull()
        }
    }
}
