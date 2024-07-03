package com.onesignal.core.internal.startup

import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import io.kotest.assertions.throwables.shouldThrowUnit
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify

class StartupServiceTests : FunSpec({

    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("bootstrap with no IBootstrapService dependencies is a no-op") {
        // Given
        val startupService = StartupService(listOf(), listOf())

        // When
        startupService.bootstrap()

        // Then
    }

    test("bootstrap will call all IBootstrapService dependencies successfully") {
        // Given
        val mockBootstrapService1 = spyk<IBootstrapService>()
        val mockBootstrapService2 = spyk<IBootstrapService>()

        val startupService = StartupService(listOf(mockBootstrapService1, mockBootstrapService2), listOf())

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

        val startupService = StartupService(listOf(mockBootstrapService1, mockBootstrapService2), listOf())

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

    test("startup will call all IStartableService dependencies successfully") {
        // Given
        val mockStartupService1 = spyk<IStartableService>()
        val mockStartupService2 = spyk<IStartableService>()

        val startupService = StartupService(listOf(), listOf(mockStartupService1, mockStartupService2))

        // When
        startupService.start()

        // Then
        verify(exactly = 1) { mockStartupService1.start() }
        verify(exactly = 1) { mockStartupService2.start() }
    }

    test("startup will propagate exception when an IStartableService throws an exception") {
        // Given
        val exception = Exception("SOMETHING BAD")

        val mockStartableService1 = mockk<IStartableService>()
        every { mockStartableService1.start() } throws exception
        val mockStartableService2 = spyk<IStartableService>()

        val startupService = StartupService(listOf(), listOf(mockStartableService1, mockStartableService2))

        // When
        val actualException =
            shouldThrowUnit<Exception> {
                startupService.start()
            }

        // Then
        actualException shouldBe exception
        verify(exactly = 1) { mockStartableService1.start() }
        verify(exactly = 0) { mockStartableService2.start() }
    }
})
