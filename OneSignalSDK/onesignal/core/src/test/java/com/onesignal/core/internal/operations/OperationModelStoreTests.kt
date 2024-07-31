package com.onesignal.core.internal.operations

import com.onesignal.core.internal.operations.impl.OperationModelStore
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.MockPreferencesService
import com.onesignal.user.internal.operations.LoginUserOperation
import com.onesignal.user.internal.operations.SetPropertyOperation
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class OperationModelStoreTests : FunSpec({

    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("does not load invalid cached operations") {
        // Given
        val prefs = MockPreferencesService()
        val operationModelStore = OperationModelStore(prefs)
        val jsonArray = JSONArray()

        // 1. Create a VALID Operation with onesignalId
        val validOperation = SetPropertyOperation(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "property", "value")
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
})
