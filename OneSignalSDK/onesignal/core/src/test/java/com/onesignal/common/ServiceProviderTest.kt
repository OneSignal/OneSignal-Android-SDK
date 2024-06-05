package com.onesignal.common

import com.onesignal.common.services.ServiceBuilder
import com.onesignal.common.services.ServiceProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

internal class MySlowerConstructorClass : IMyTestInterface {
    init {
        // NOTE: Keep these println calls, otherwise Kotlin optimizes
        //   something which cases the test not fail when it should.
        println("MySlowerConstructorClass BEFORE")
        Thread.sleep(50)
        println("MySlowerConstructorClass AFTER")
    }
}

class ServiceProviderTest : FunSpec({

    fun setupServiceProviderWithSlowInitClass(): ServiceProvider {
        val serviceBuilder = ServiceBuilder()
        serviceBuilder.register<MySlowConstructorClass>().provides<IMyTestInterface>()
        return serviceBuilder.build()
    }

    fun setupServiceProviderWithSlowerInitClass(): ServiceProvider {
        val serviceBuilder = ServiceBuilder()
        serviceBuilder.register<MySlowConstructorClass>().provides<MySlowConstructorClass>()
        serviceBuilder.register<MySlowerConstructorClass>().provides<MySlowerConstructorClass>()
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

    test("Ensure that retrieving a long-running service does not block the retrieval of other services by calling getSuspendService.") {
        var services = setupServiceProviderWithSlowerInitClass()

        // ***
        // This part calls to get service of MySlowConstructorClass first, and the Slow service can
        // be ready after 20 ms
        val queue = LinkedBlockingQueue<IMyTestInterface>()
        GlobalScope.launch {
            queue.add(services.getService<MySlowConstructorClass>())
            queue.add(services.getService<MySlowerConstructorClass>())
        }

        delay(20)
        queue.size shouldBe 1

        delay(40)
        queue.size shouldBe 2
        // ***

        // ***
        // however, if we call to retrieve MySlowerConstructorClass first, MySlowConstructorClass
        // will be blocked until after the slower service
        queue.clear()
        services = setupServiceProviderWithSlowerInitClass()
        GlobalScope.launch {
            queue.add(services.getService<MySlowerConstructorClass>())
            queue.add(services.getService<MySlowConstructorClass>())
        }

        delay(20)
        queue.size shouldBe 0

        delay(36)
        queue.size shouldBe 1

        delay(10)
        queue.size shouldBe 2
        // ***

        // *** TEST FAILED!!!
        // The goal here is to be able to get the Slow service after the shorter completion time
        // even retrieving Slower service is called first
        services = setupServiceProviderWithSlowerInitClass()
        var slowService: MySlowConstructorClass? = null
        var slowerService: MySlowerConstructorClass? = null
        GlobalScope.launch {
            slowerService = services.getSuspendService<MySlowerConstructorClass>()
            slowService = services.getSuspendService<MySlowConstructorClass>()
        }

        delay(20)
        slowService shouldNotBe null
        slowerService shouldBe null

        delay(40)
        slowerService shouldNotBe null
        // ***
    }
})
