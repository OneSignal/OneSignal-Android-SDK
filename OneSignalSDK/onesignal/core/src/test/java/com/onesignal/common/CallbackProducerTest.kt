package com.onesignal.common

import com.onesignal.common.events.CallbackProducer
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

class CallbackProducerTest : FunSpec({

    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("suspendingFireOnMain invokes the handler on the main thread") {
        @OptIn(ExperimentalCoroutinesApi::class)
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val producer = CallbackProducer<String>()
            producer.set("handler")
            var fired: String? = null

            producer.suspendingFireOnMain { fired = it }

            fired shouldBe "handler"
        } finally {
            @OptIn(ExperimentalCoroutinesApi::class)
            Dispatchers.resetMain()
        }
    }

    test("suspendingFireOnMain skips the handler when the main thread is unavailable") {
        val producer = CallbackProducer<String>()
        producer.set("handler")
        var fired = false

        producer.suspendingFireOnMain { fired = true }

        fired shouldBe false
    }

    test("suspendingFireOnMain does nothing without a handler") {
        @OptIn(ExperimentalCoroutinesApi::class)
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            var fired = false

            CallbackProducer<String>().suspendingFireOnMain { fired = true }

            fired shouldBe false
        } finally {
            @OptIn(ExperimentalCoroutinesApi::class)
            Dispatchers.resetMain()
        }
    }
})
