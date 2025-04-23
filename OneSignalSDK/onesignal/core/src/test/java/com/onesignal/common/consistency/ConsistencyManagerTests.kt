package com.onesignal.common.consistency

import com.onesignal.common.consistency.enums.IamFetchRywTokenKey
import com.onesignal.common.consistency.impl.ConsistencyManager
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
            val key = IamFetchRywTokenKey.USER
            val token = "123"
            val delay = 500L
            val rywData = RywData(token, delay)

            consistencyManager.setRywData(id, key, rywData)

            val condition = TestMetCondition(mapOf(id to mapOf(key to rywData)))
            val deferred = consistencyManager.getRywDataFromAwaitableCondition(condition)
            val result = deferred.await()

            result shouldBe rywData
        }
    }

    test("registerCondition completes when condition is met") {
        runTest {
            // Given
            val id = "test_id"
            val key = IamFetchRywTokenKey.USER
            val token = "123"
            val delay = 500L
            val rywData = RywData(token, delay)

            // Set a token to meet the condition
            consistencyManager.setRywData(id, key, rywData)

            val condition = TestMetCondition(mapOf(id to mapOf(key to rywData)))
            val deferred = consistencyManager.getRywDataFromAwaitableCondition(condition)

            deferred.await()
            deferred.isCompleted shouldBe true
        }
    }

    test("registerCondition does not complete when condition is not met") {
        runTest {
            val condition = TestUnmetCondition()
            val deferred = consistencyManager.getRywDataFromAwaitableCondition(condition)

            consistencyManager.setRywData("id", IamFetchRywTokenKey.USER, RywData("123", 500L))
            deferred.isCompleted shouldBe false
        }
    }

    test("resolveConditionsWithID resolves conditions based on ID") {
        runTest {
            val condition = TestUnmetCondition()
            val deferred = consistencyManager.getRywDataFromAwaitableCondition(condition)
            consistencyManager.resolveConditionsWithID(TestUnmetCondition.ID)
            deferred.await()

            deferred.isCompleted shouldBe true
        }
    }

    test("translateConditionKeyWithID translates keys with corresponding ID") {
        runTest {
            // Given
            val oldOnesignalId = "123"
            val newOnesignalId = "456"
            val condition = IamFetchReadyCondition(oldOnesignalId)
            val deferred = consistencyManager.getRywDataFromAwaitableCondition(condition)

            // a new onesignal ID has been received
            consistencyManager.translateConditionKeyWithID(condition.id, oldOnesignalId, newOnesignalId)
            consistencyManager.setRywData(newOnesignalId, IamFetchRywTokenKey.USER, RywData("token", 500L))

            deferred.await()
            // setRywData with new onesignal ID completes the condition that was created with old onesignalID
            deferred.isCompleted shouldBe true
        }
    }
}) {
    // Mock implementation of ICondition that simulates a condition that isn't met
    private class TestUnmetCondition : ICondition {
        companion object {
            const val ID = "TestUnmetCondition"
        }

        override val id: String
            get() = ID

        override fun isMet(indexedTokens: Map<String, Map<IConsistencyKeyEnum, RywData>>): Boolean {
            return false // Always returns false to simulate an unmet condition
        }

        override fun getRywData(indexedTokens: Map<String, Map<IConsistencyKeyEnum, RywData?>>): RywData? {
            return null
        }

        override fun translateKey(
            oldKey: String,
            newKey: String,
        ) {
            // not used
        }
    }

    // Mock implementation of ICondition for cases where the condition is met
    private class TestMetCondition(
        private val expectedRywTokens: Map<String, Map<IConsistencyKeyEnum, RywData?>>,
    ) : ICondition {
        companion object {
            const val ID = "TestMetCondition"
        }

        override val id: String
            get() = ID

        override fun isMet(indexedTokens: Map<String, Map<IConsistencyKeyEnum, RywData>>): Boolean {
            return indexedTokens == expectedRywTokens
        }

        override fun getRywData(indexedTokens: Map<String, Map<IConsistencyKeyEnum, RywData?>>): RywData? {
            return expectedRywTokens.values.firstOrNull()?.values?.firstOrNull()
        }

        override fun translateKey(
            oldKey: String,
            newKey: String,
        ) {
            // not used
        }
    }
}
