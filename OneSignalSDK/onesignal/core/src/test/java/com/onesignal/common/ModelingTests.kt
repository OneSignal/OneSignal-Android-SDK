package com.onesignal.common

import android.content.SharedPreferences
import com.onesignal.common.events.EventProducer
import com.onesignal.common.modeling.IModelChangedHandler
import com.onesignal.common.modeling.IModelStoreChangeHandler
import com.onesignal.common.modeling.ModelChangedArgs
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.mocks.MockHelper
import com.onesignal.mocks.MockPreferencesService
import com.onesignal.user.internal.subscriptions.SubscriptionModel
import com.onesignal.user.internal.subscriptions.SubscriptionModelStore
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every

class ModelingTests : FunSpec({

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

    // sometimes accessing the shared preference may be unexpectedly slow and occupy the models for too long
    test("ensure the lock on models is released before accessing preference when calling persist()") {
        // Given
        val mockPreference = MockPreferencesService()
        val modelStore = ConfigModelStore(mockPreference)
        val model = modelStore.model

        // suppose accessing the preference takes 5 seconds
        every { mockPreference.saveString(any(), any(), any()) } answers { Thread.sleep(1_000) }

        val t1 =
                Thread {
                    // will call persist that locks models
                    model.setOptAnyProperty("key1", "value1")
                }

        val t2 =
                Thread {
                    // will call persist and wait for t1 to release models
                    model.setOptAnyProperty("key2", "value2")
                }

        // when
        t1.start()
        t2.start()

        // Set 1s timeout for t2 to complete the task
        t2.join(1_200)

        // verify if the thread has been successfully terminated without waiting for twice the preference access time
        t2.state shouldBe Thread.State.TERMINATED
    }
})
