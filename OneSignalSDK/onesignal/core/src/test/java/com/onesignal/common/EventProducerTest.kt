package com.onesignal.common

import com.onesignal.common.events.EventProducer
import io.kotest.core.spec.style.FunSpec
import kotlin.concurrent.thread

class EventProducerTest : FunSpec({

    fun modifyingSubscribersThread(eventProducer: EventProducer<Boolean>): Thread {
        return thread(start = true) {
            repeat(10_000) {
                eventProducer.subscribe(true)
                eventProducer.unsubscribe(true)
            }
        }
    }

    test("fire is thread safe") {
        val eventProducer = EventProducer<Boolean>()
        val modifyingSubscribersThread = modifyingSubscribersThread(eventProducer)

        repeat(10_000) {
            eventProducer.fire { }
        }

        modifyingSubscribersThread.join()
    }

    test("suspendingFire is thread safe") {
        val eventProducer = EventProducer<Boolean>()
        val modifyingSubscribersThread = modifyingSubscribersThread(eventProducer)

        repeat(10_000) {
            eventProducer.suspendingFire { }
        }

        modifyingSubscribersThread.join()
    }

    test("hasSubscribers is thread safe") {
        val eventProducer = EventProducer<Boolean>()
        val modifyingSubscribersThread = modifyingSubscribersThread(eventProducer)

        repeat(10_000) {
            eventProducer.hasSubscribers
        }

        modifyingSubscribersThread.join()
    }
})
