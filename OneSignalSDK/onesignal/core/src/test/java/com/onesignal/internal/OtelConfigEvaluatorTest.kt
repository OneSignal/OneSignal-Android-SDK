package com.onesignal.internal

import com.onesignal.debug.LogLevel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class OtelConfigEvaluatorTest : FunSpec({

    // ---- null -> enabled ----

    test("null old config and new enabled returns Enable with the configured level") {
        val result = OtelConfigEvaluator.evaluate(
            old = null,
            new = OtelConfig(isEnabled = true, logLevel = LogLevel.WARN),
        )
        result.shouldBeInstanceOf<OtelConfigAction.Enable>()
        result.logLevel shouldBe LogLevel.WARN
    }

    test("null old config and new enabled with null logLevel defaults to ERROR") {
        val result = OtelConfigEvaluator.evaluate(
            old = null,
            new = OtelConfig(isEnabled = true, logLevel = null),
        )
        result.shouldBeInstanceOf<OtelConfigAction.Enable>()
        result.logLevel shouldBe LogLevel.ERROR
    }

    // ---- null -> disabled ----

    test("null old config and new disabled returns NoChange") {
        val result = OtelConfigEvaluator.evaluate(
            old = null,
            new = OtelConfig(isEnabled = false, logLevel = null),
        )
        result shouldBe OtelConfigAction.NoChange
    }

    // ---- disabled -> enabled ----

    test("disabled to enabled returns Enable") {
        val result = OtelConfigEvaluator.evaluate(
            old = OtelConfig.DISABLED,
            new = OtelConfig(isEnabled = true, logLevel = LogLevel.INFO),
        )
        result.shouldBeInstanceOf<OtelConfigAction.Enable>()
        result.logLevel shouldBe LogLevel.INFO
    }

    // ---- enabled -> disabled ----

    test("enabled to disabled returns Disable") {
        val result = OtelConfigEvaluator.evaluate(
            old = OtelConfig(isEnabled = true, logLevel = LogLevel.ERROR),
            new = OtelConfig(isEnabled = false, logLevel = null),
        )
        result shouldBe OtelConfigAction.Disable
    }

    // ---- enabled -> enabled (level changed) ----

    test("enabled to enabled with different log level returns UpdateLogLevel") {
        val result = OtelConfigEvaluator.evaluate(
            old = OtelConfig(isEnabled = true, logLevel = LogLevel.ERROR),
            new = OtelConfig(isEnabled = true, logLevel = LogLevel.WARN),
        )
        result.shouldBeInstanceOf<OtelConfigAction.UpdateLogLevel>()
        result.oldLevel shouldBe LogLevel.ERROR
        result.newLevel shouldBe LogLevel.WARN
    }

    test("enabled with null level to enabled with explicit level returns UpdateLogLevel") {
        val result = OtelConfigEvaluator.evaluate(
            old = OtelConfig(isEnabled = true, logLevel = null),
            new = OtelConfig(isEnabled = true, logLevel = LogLevel.WARN),
        )
        result.shouldBeInstanceOf<OtelConfigAction.UpdateLogLevel>()
        result.oldLevel shouldBe LogLevel.ERROR
        result.newLevel shouldBe LogLevel.WARN
    }

    // ---- enabled -> enabled (same level) ----

    test("enabled to enabled with same level returns NoChange") {
        val result = OtelConfigEvaluator.evaluate(
            old = OtelConfig(isEnabled = true, logLevel = LogLevel.ERROR),
            new = OtelConfig(isEnabled = true, logLevel = LogLevel.ERROR),
        )
        result shouldBe OtelConfigAction.NoChange
    }

    // ---- disabled -> disabled ----

    test("disabled to disabled returns NoChange") {
        val result = OtelConfigEvaluator.evaluate(
            old = OtelConfig.DISABLED,
            new = OtelConfig.DISABLED,
        )
        result shouldBe OtelConfigAction.NoChange
    }
})
