package com.onesignal.internal

import com.onesignal.debug.LogLevel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class ObservabilityConfigEvaluatorTest : FunSpec({

    // ---- null -> enabled ----

    test("null old config and new enabled returns Enable with the configured level") {
        val result = ObservabilityConfigEvaluator.evaluate(
            old = null,
            new = ObservabilityConfig(isEnabled = true, logLevel = LogLevel.WARN),
        )
        result.shouldBeInstanceOf<ObservabilityConfigAction.Enable>()
        result.logLevel shouldBe LogLevel.WARN
    }

    test("null old config and new enabled with null logLevel defaults to ERROR") {
        val result = ObservabilityConfigEvaluator.evaluate(
            old = null,
            new = ObservabilityConfig(isEnabled = true, logLevel = null),
        )
        result.shouldBeInstanceOf<ObservabilityConfigAction.Enable>()
        result.logLevel shouldBe LogLevel.ERROR
    }

    // ---- null -> disabled ----

    test("null old config and new disabled returns NoChange") {
        val result = ObservabilityConfigEvaluator.evaluate(
            old = null,
            new = ObservabilityConfig(isEnabled = false, logLevel = null),
        )
        result shouldBe ObservabilityConfigAction.NoChange
    }

    // ---- disabled -> enabled ----

    test("disabled to enabled returns Enable") {
        val result = ObservabilityConfigEvaluator.evaluate(
            old = ObservabilityConfig.DISABLED,
            new = ObservabilityConfig(isEnabled = true, logLevel = LogLevel.INFO),
        )
        result.shouldBeInstanceOf<ObservabilityConfigAction.Enable>()
        result.logLevel shouldBe LogLevel.INFO
    }

    // ---- enabled -> disabled ----

    test("enabled to disabled returns Disable") {
        val result = ObservabilityConfigEvaluator.evaluate(
            old = ObservabilityConfig(isEnabled = true, logLevel = LogLevel.ERROR),
            new = ObservabilityConfig(isEnabled = false, logLevel = null),
        )
        result shouldBe ObservabilityConfigAction.Disable
    }

    // ---- enabled -> enabled (level changed) ----

    test("enabled to enabled with different log level returns UpdateLogLevel") {
        val result = ObservabilityConfigEvaluator.evaluate(
            old = ObservabilityConfig(isEnabled = true, logLevel = LogLevel.ERROR),
            new = ObservabilityConfig(isEnabled = true, logLevel = LogLevel.WARN),
        )
        result.shouldBeInstanceOf<ObservabilityConfigAction.UpdateLogLevel>()
        result.oldLevel shouldBe LogLevel.ERROR
        result.newLevel shouldBe LogLevel.WARN
    }

    test("enabled with null level to enabled with explicit level returns UpdateLogLevel") {
        val result = ObservabilityConfigEvaluator.evaluate(
            old = ObservabilityConfig(isEnabled = true, logLevel = null),
            new = ObservabilityConfig(isEnabled = true, logLevel = LogLevel.WARN),
        )
        result.shouldBeInstanceOf<ObservabilityConfigAction.UpdateLogLevel>()
        result.oldLevel shouldBe LogLevel.ERROR
        result.newLevel shouldBe LogLevel.WARN
    }

    // ---- enabled -> enabled (same level) ----

    test("enabled to enabled with same level returns NoChange") {
        val result = ObservabilityConfigEvaluator.evaluate(
            old = ObservabilityConfig(isEnabled = true, logLevel = LogLevel.ERROR),
            new = ObservabilityConfig(isEnabled = true, logLevel = LogLevel.ERROR),
        )
        result shouldBe ObservabilityConfigAction.NoChange
    }

    // ---- disabled -> disabled ----

    test("disabled to disabled returns NoChange") {
        val result = ObservabilityConfigEvaluator.evaluate(
            old = ObservabilityConfig.DISABLED,
            new = ObservabilityConfig.DISABLED,
        )
        result shouldBe ObservabilityConfigAction.NoChange
    }
})
