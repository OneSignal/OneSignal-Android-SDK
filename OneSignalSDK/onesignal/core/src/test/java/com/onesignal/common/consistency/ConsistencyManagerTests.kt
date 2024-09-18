package com.onesignal.common.consistency.impl

import com.onesignal.common.consistency.enums.IamFetchRywTokenKey
import com.onesignal.common.consistency.models.ICondition
import com.onesignal.common.consistency.models.IConsistencyKeyEnum
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest

class ConsistencyManagerTests : FunSpec({

    lateinit var consistencyManager: ConsistencyManager

    beforeAny {
        consistencyManager = ConsistencyManager()
    }

    test("setRywToken updates the token correctly") {
        runTest {
            // Given
            val id = "test_id"
            val key = IamFetchRywTokenKey.USER_UPDATE
            val value = 123L

            consistencyManager.setRywToken(id, key, value)

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
            val key = IamFetchRywTokenKey.USER_UPDATE
            val value = 123L

            // Set a token to meet the condition
            consistencyManager.setRywToken(id, key, value)

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

            consistencyManager.setRywToken("id", IamFetchRywTokenKey.USER_UPDATE, 123L)
            deferred.isCompleted shouldBe false
        }
    }
}) {
    // Mock implementation of ICondition that simulates a condition that isn't met
    private class TestUnmetCondition : ICondition {
        override fun isMet(rywTokens: Map<String, Map<IConsistencyKeyEnum, Long?>>): Boolean {
            return false // Always returns false to simulate an unmet condition
        }

        override fun getNewestToken(indexedTokens: Map<String, Map<IConsistencyKeyEnum, Long?>>): Long? {
            return null
        }
    }

    // Mock implementation of ICondition for cases where the condition is met
    private class TestMetCondition(
        private val expectedRywTokens: Map<String, Map<IConsistencyKeyEnum, Long?>>,
    ) : ICondition {
        override fun isMet(rywTokens: Map<String, Map<IConsistencyKeyEnum, Long?>>): Boolean {
            return rywTokens == expectedRywTokens
        }

        override fun getNewestToken(indexedTokens: Map<String, Map<IConsistencyKeyEnum, Long?>>): Long? {
            return expectedRywTokens.values.firstOrNull()?.values?.firstOrNull()
        }
    }
}
