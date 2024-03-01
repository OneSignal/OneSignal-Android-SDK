package com.onesignal.common

import com.onesignal.common.services.ServiceBuilder
import com.onesignal.common.services.ServiceProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.types.shouldBeSameInstanceAs
import java.util.concurrent.LinkedBlockingQueue

internal interface IMyTestInterface

internal class MySlowConstructorClass : IMyTestInterface {
    init {
        // NOTE: Keep these println calls, otherwise Kotlin optimizes
        //   something which cases the test not fail when it should.
        println("MySlowConstructorClass BEFORE")
        Thread.sleep(10)
        println("MySlowConstructorClass AFTER")
    }
}

class ServiceProviderTest : FunSpec({

    fun setupServiceProviderWithSlowInitClass(): ServiceProvider {
        val serviceBuilder = ServiceBuilder()
        serviceBuilder.register<MySlowConstructorClass>().provides<IMyTestInterface>()
        return serviceBuilder.build()
    }

    test("getService is thread safe") {
        val services = setupServiceProviderWithSlowInitClass()

        val queue = LinkedBlockingQueue<IMyTestInterface>()
        Thread {
            queue.add(services.getService<IMyTestInterface>())
        }.start()
        Thread {
            queue.add(services.getService<IMyTestInterface>())
        }.start()

        val firstReference = queue.take()
        val secondReference = queue.take()
        firstReference shouldBeSameInstanceAs secondReference
    }
})
