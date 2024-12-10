package com.onesignal.common

import com.onesignal.common.events.EventProducer
import com.onesignal.common.modeling.IModelChangedHandler
import com.onesignal.common.modeling.IModelStoreChangeHandler
import com.onesignal.common.modeling.ModelChangedArgs
import com.onesignal.core.internal.operations.impl.OperationModelStore
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.mocks.MockHelper
import com.onesignal.mocks.MockPreferencesService
import com.onesignal.user.internal.operations.LoginUserFromSubscriptionOperation
import com.onesignal.user.internal.operations.LoginUserOperation
import com.onesignal.user.internal.subscriptions.SubscriptionModel
import com.onesignal.user.internal.subscriptions.SubscriptionModelStore
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.json.JSONArray
import java.util.UUID

class ModelingTests : FunSpec({

    test("ensure prolonged loading in the background thread does not block insertion in the main thread") {
        // Given
        val prefs = MockPreferencesService()
        val operationModelStore = OperationModelStore(prefs)

        // add an arbitrary operation to the cache
        val cachedOperation = LoginUserFromSubscriptionOperation()
        val newOperation = LoginUserOperation()
        cachedOperation.id = UUID.randomUUID().toString()
        val jsonArray = JSONArray()
        jsonArray.put(cachedOperation.toJSON())
        prefs.saveString(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.MODEL_STORE_PREFIX + "operations", jsonArray.toString())

        // simulate a background thread to load operations
        val backgroundThread =
            Thread {
                operationModelStore.loadOperations()
            }

        val mainThread =
            Thread {
                operationModelStore.add(newOperation)
            }

        backgroundThread.start()
        mainThread.start()

        mainThread.join(100)

        // Then
        // insertion from the main thread is done without blocking
        operationModelStore.list().count() shouldBe 1
        operationModelStore.list().first() shouldBe newOperation
    }

    test("Deadlock related to Model.setOptAnyProperty") {
        // Given
        val modelStore = MockHelper.configModelStore()
        val model = modelStore.model

        val t1 =
            Thread {
                // acquire "model.data", then trigger the onChanged event
                model.setOptAnyProperty("key1", "value1")
            }

        val t2 =
            Thread {
                // acquire "model.initializationLock", then wait for "model.data" to be released
                model.initializeFromModel("", MockHelper.configModelStore().model)
            }

        model.subscribe(
            object : IModelChangedHandler {
                // will be executed in t1
                override fun onChanged(
                    args: ModelChangedArgs,
                    tag: String,
                ) {
                    Thread.sleep(200)
                    // waiting for "model.initializationLock"
                    model.toJSON()
                }
            },
        )

        t1.start()
        t2.start()

        // Set 1s timeout for t2 to complete the task
        t2.join(1000)

        // verify if the thread has been successfully terminated
        t2.state shouldBe Thread.State.TERMINATED
    }

    test("Deadlock related to ModelSstore add() or remove()") {
        // Given
        val modelStore = SubscriptionModelStore(MockPreferencesService())
        val event = EventProducer<SubscriptionModel>()
        val oldSubscriptionModel = SubscriptionModel()
        val newSubscriptionModel = SubscriptionModel()
        oldSubscriptionModel.id = "oldModel"
        newSubscriptionModel.id = "newModel"
        modelStore.add(oldSubscriptionModel)

        val t1 =
            Thread {
                // acquire "ModelStore.models", then trigger the onChanged event
                modelStore.add(newSubscriptionModel)
            }

        val t2 =
            Thread {
                // acquire "model.data", then wait for "ModelStore.models"
                newSubscriptionModel.toJSON()
            }

        modelStore.subscribe(
            object : IModelStoreChangeHandler<SubscriptionModel> {
                override fun onModelAdded(
                    model: SubscriptionModel,
                    tag: String,
                ) {
                    // waiting for "model.data"
                    model.initializeFromModel("", MockHelper.configModelStore().model)
                }

                override fun onModelUpdated(
                    args: ModelChangedArgs,
                    tag: String,
                ) {
                    // left empty in purpose
                }

                override fun onModelRemoved(
                    model: SubscriptionModel,
                    tag: String,
                ) {
                    // left empty in purpose
                }
            },
        )

        t1.start()
        t2.start()

        // Set 1s timeout for t2 to complete the task
        t2.join(1000)

        // verify if the thread has been successfully terminated
        t2.state shouldBe Thread.State.TERMINATED
    }

    test("Unsubscribing handler in change event may cause the concurrent modification exception") {
        // Given an arbitrary model
        val modelStore = MockHelper.configModelStore()
        val model = modelStore.model

        // subscribe to a change handler
        model.subscribe(
            object : IModelChangedHandler {
                override fun onChanged(
                    args: ModelChangedArgs,
                    tag: String,
                ) {
                    // remove from "subscribers" while "subscribers" is being accessed
                    model.unsubscribe(this)
                }
            },
        )

        // this will trigger EventProducer.fire and loop through the list "subscribers"
        model.setOptAnyProperty("key1", "value1")

        // ensure no concurrent modification exception is thrown and "subcribers" is clear
        model.hasSubscribers shouldBe false
    }

    test("ensure Model Store load pulls cached operations and doesn't duplicate models") {
        // Given
        val prefs = MockPreferencesService()
        val operationModelStore = OperationModelStore(prefs)
        val jsonArray = JSONArray()

        val cachedOperation = LoginUserFromSubscriptionOperation()
        cachedOperation.id = UUID.randomUUID().toString()
        // Add duplicate operations to the cache
        jsonArray.put(cachedOperation.toJSON())
        jsonArray.put(cachedOperation.toJSON())
        prefs.saveString(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.MODEL_STORE_PREFIX + "operations", jsonArray.toString())

        // When - adding an operation first and then loading from cache
        val newOperation = LoginUserOperation()
        newOperation.id = UUID.randomUUID().toString()
        operationModelStore.add(newOperation)
        operationModelStore.loadOperations()

        // Then
        operationModelStore.list().count() shouldBe 2
        // The cached operation will not be the same instance
        operationModelStore.get(cachedOperation.id)!!.name shouldBe cachedOperation.name
        // The new operation added directly to the store should be the same instance
        operationModelStore.get(newOperation.id) shouldBe newOperation
    }
})
