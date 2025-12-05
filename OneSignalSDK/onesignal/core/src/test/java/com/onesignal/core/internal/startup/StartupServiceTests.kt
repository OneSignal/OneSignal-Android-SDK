package com.onesignal.core.internal.startup

import com.onesignal.common.services.ServiceBuilder
import com.onesignal.common.services.ServiceProvider
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.IOMockHelper
import com.onesignal.mocks.IOMockHelper.awaitIO
import io.kotest.assertions.throwables.shouldThrowUnit
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred

class StartupServiceTests : FunSpec({
    fun setupServiceProvider(
        bootstrapServices: List<IBootstrapService>,
        startableServices: List<IStartableService>,
    ): ServiceProvider {
        val serviceBuilder = ServiceBuilder()
        for (reg in bootstrapServices)
            serviceBuilder.register(reg).provides<IBootstrapService>()
        for (reg in startableServices)
            serviceBuilder.register(reg).provides<IStartableService>()
        return serviceBuilder.build()
    }

    listener(IOMockHelper)

    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("bootstrap with no IBootstrapService dependencies is a no-op") {
        // Given
        val startupService = StartupService(setupServiceProvider(listOf(), listOf()))

        // When
        startupService.bootstrap()

        // Then
    }

    test("bootstrap will call all IBootstrapService dependencies successfully") {
        // Given
        val mockBootstrapService1 = mockk<IBootstrapService>(relaxed = true)
        val mockBootstrapService2 = mockk<IBootstrapService>(relaxed = true)

        val startupService = StartupService(setupServiceProvider(listOf(mockBootstrapService1, mockBootstrapService2), listOf()))

        // When
        startupService.bootstrap()

        // Then
        verify(exactly = 1) { mockBootstrapService1.bootstrap() }
        verify(exactly = 1) { mockBootstrapService2.bootstrap() }
    }

    test("bootstrap will propagate exception when an IBootstrapService throws an exception") {
        // Given
        val exception = Exception("SOMETHING BAD")

        val mockBootstrapService1 = mockk<IBootstrapService>()
        every { mockBootstrapService1.bootstrap() } throws exception
        val mockBootstrapService2 = spyk<IBootstrapService>()

        val startupService = StartupService(setupServiceProvider(listOf(mockBootstrapService1, mockBootstrapService2), listOf()))

        // When
        val actualException =
            shouldThrowUnit<Exception> {
                startupService.bootstrap()
            }

        // Then
        actualException shouldBe exception
        verify(exactly = 1) { mockBootstrapService1.bootstrap() }
        verify(exactly = 0) { mockBootstrapService2.bootstrap() }
    }

    test("startup will call all IStartableService dependencies successfully after a short delay") {
        // Given
        val mockStartupService1 = spyk<IStartableService>()
        val mockStartupService2 = spyk<IStartableService>()

        val startupService = StartupService(setupServiceProvider(listOf(), listOf(mockStartupService1, mockStartupService2)))

        // When
        startupService.scheduleStart()

        // Then
        Thread.sleep(10)
        verify(exactly = 1) { mockStartupService1.start() }
        verify(exactly = 1) { mockStartupService2.start() }
    }

    test("scheduleStart does not block main thread") {
        // Given
        val mockStartableService1 = spyk<IStartableService>()
        val mockStartableService2 = spyk<IStartableService>()
        val startupService = StartupService(setupServiceProvider(listOf(), listOf(mockStartableService1)))

        // Block the scheduled services until we're ready
        val blockTrigger = CompletableDeferred<Unit>()
        every { mockStartableService1.start() } coAnswers {
            blockTrigger.await() // Block until released
        }

        // When - scheduleStart() is async, so it doesn't block
        val startTime = System.currentTimeMillis()
        startupService.scheduleStart()
        val scheduleTime = System.currentTimeMillis() - startTime

        // This should execute immediately since scheduleStart() doesn't block
        mockStartableService3.start()
        val immediateTime = System.currentTimeMillis() - startTime

        // Then - verify scheduleStart() returned quickly (non-blocking)
        // Should return in < 50ms (proving it doesn't wait for services to start)
        scheduleTime shouldBeLessThan 50L
        immediateTime shouldBeLessThan 50L

        // Wait for async execution to complete
        Thread.sleep(100)

        // Verify all services were called
        verify(exactly = 1) { mockStartableService1.start() }
        verify(exactly = 1) { mockStartableService2.start() }
        verify(exactly = 1) { mockStartableService3.start() }

        // The key assertion: scheduleStart() returned immediately without blocking,
        // allowing service3.start() to be called synchronously. All services eventually
        // get started, proving scheduleStart() is non-blocking.
    }
})
