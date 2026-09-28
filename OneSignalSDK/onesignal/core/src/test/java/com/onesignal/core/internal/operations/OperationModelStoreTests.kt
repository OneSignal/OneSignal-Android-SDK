package com.onesignal.core.internal.operations

import com.onesignal.core.internal.operations.impl.OperationModelStore
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.MockHelper
import com.onesignal.mocks.MockPreferencesService
import com.onesignal.user.internal.operations.LoginUserOperation
import com.onesignal.user.internal.operations.SetPropertyOperation
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private const val LOAD_TIME = 1_700_000_000_000L

class OperationModelStoreTests : FunSpec({

    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("does not load invalid cached operations") {
        // Given
        val prefs = MockPreferencesService()
        val operationModelStore = OperationModelStore(prefs, MockHelper.time(LOAD_TIME))
        val jsonArray = JSONArray()

        // 1. Create a VALID Operation with onesignalId
        val validOperation = SetPropertyOperation(UUID.randomUUID().toString(), UUID.randomUUID().toString(), null, "property", "value")
        validOperation.id = UUID.randomUUID().toString()

        // 2. Create a VALID operation missing onesignalId
        val validOperationMissingOnesignalId = LoginUserOperation()
        validOperationMissingOnesignalId.id = UUID.randomUUID().toString()

        // 3. Create an INVALID Operation missing onesignalId
        val invalidOperationMissingOnesignalId = SetPropertyOperation()
        invalidOperationMissingOnesignalId.id = UUID.randomUUID().toString()

        // 4. Create an INVALID Operation missing operation name
        val invalidOperationMissingName =
            JSONObject()
                .put("app_id", UUID.randomUUID().toString())
                .put("onesignalId", UUID.randomUUID().toString())
                .put("id", UUID.randomUUID().toString())

        // Add the Operations to the cache
        jsonArray.put(validOperation.toJSON())
        jsonArray.put(validOperationMissingOnesignalId.toJSON())
        jsonArray.put(invalidOperationMissingOnesignalId.toJSON())
        jsonArray.put(invalidOperationMissingName)
        prefs.saveString(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.MODEL_STORE_PREFIX + "operations", jsonArray.toString())

        // When
        operationModelStore.loadOperations()

        // Then
        operationModelStore.list().count() shouldBe 2
        operationModelStore.get(validOperation.id) shouldNotBe null
        operationModelStore.get(validOperationMissingOnesignalId.id) shouldNotBe null
        operationModelStore.get(invalidOperationMissingOnesignalId.id) shouldBe null
        operationModelStore.get(invalidOperationMissingName["id"] as String) shouldBe null
    }

    test("createdAt round-trips through persist and load") {
        // Given
        val prefs = MockPreferencesService()
        val operation = SetPropertyOperation("appId", "onesignal-id", null, "property", "value")
        operation.id = UUID.randomUUID().toString()
        operation.createdAt = 1_234L
        val writer = OperationModelStore(prefs, MockHelper.time(LOAD_TIME))
        writer.loadOperations()
        writer.add(operation)

        // When
        val reader = OperationModelStore(prefs, MockHelper.time(LOAD_TIME))
        reader.loadOperations()

        // Then
        reader.get(operation.id)?.createdAt shouldBe 1_234L
    }

    test("an operation persisted without createdAt is stamped with the load time") {
        // Given
        val prefs = MockPreferencesService()
        val legacy = SetPropertyOperation("appId", "onesignal-id", null, "property", "value")
        legacy.id = UUID.randomUUID().toString()
        prefs.saveString(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.MODEL_STORE_PREFIX + "operations", JSONArray().put(legacy.toJSON()).toString())
        val operationModelStore = OperationModelStore(prefs, MockHelper.time(LOAD_TIME))

        // When
        operationModelStore.loadOperations()

        // Then
        operationModelStore.get(legacy.id)?.createdAt shouldBe LOAD_TIME
    }

    test("the load-time stamp is persisted, so a second load keeps it") {
        // Given
        val prefs = MockPreferencesService()
        val legacy = SetPropertyOperation("appId", "onesignal-id", null, "property", "value")
        legacy.id = UUID.randomUUID().toString()
        prefs.saveString(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.MODEL_STORE_PREFIX + "operations", JSONArray().put(legacy.toJSON()).toString())
        OperationModelStore(prefs, MockHelper.time(LOAD_TIME)).loadOperations()

        // When
        val later = OperationModelStore(prefs, MockHelper.time(LOAD_TIME + 1_000))
        later.loadOperations()

        // Then
        later.get(legacy.id)?.createdAt shouldBe LOAD_TIME
    }

    test("a persisted string over MAX_PERSISTED_LENGTH is not parsed and the preference is reset") {
        // Given
        val prefs = MockPreferencesService()
        val key = PreferenceOneSignalKeys.MODEL_STORE_PREFIX + "operations"
        // Not JSON, so parsing it would throw.
        prefs.saveString(PreferenceStores.ONESIGNAL, key, "[" + "x".repeat(OperationModelStore.MAX_PERSISTED_LENGTH) + "]")
        val operationModelStore = OperationModelStore(prefs, MockHelper.time(LOAD_TIME))

        // When
        operationModelStore.loadOperations()

        // Then
        operationModelStore.list().count() shouldBe 0
        prefs.getString(PreferenceStores.ONESIGNAL, key, null) shouldBe "[]"
    }
})
