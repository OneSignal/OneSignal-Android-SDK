package com.onesignal.internal

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.features.IFeatureManager
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.crash.ObservabilitySdkSupport
import com.onesignal.debug.internal.logging.logger.android.AndroidLogCrashHandler
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import org.robolectric.annotation.Config

/**
 * The logger pipeline is the SDK's only observability path, so these cover the config
 * state machine that brings it up and tears it down.
 */
@RobolectricTest
@Config(sdk = [Build.VERSION_CODES.O])
class LoggerLifecycleManagerTest : FunSpec({
    lateinit var context: Context
    lateinit var featureManager: IFeatureManager
    var originalHandler: Thread.UncaughtExceptionHandler? = null

    fun newManager(): LoggerLifecycleManager =
        LoggerLifecycleManager(context = context, featureManagerProvider = { featureManager })

    beforeEach {
        context = ApplicationProvider.getApplicationContext()
        featureManager = mockk<IFeatureManager>().also {
            every { it.enabledFeatureKeys() } returns emptyList()
        }
        originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        ObservabilitySdkSupport.isSupported = true
    }

    afterEach {
        ObservabilitySdkSupport.reset()
        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
    }

    test("initializeFromCachedConfig is a no-op when the SDK level is unsupported") {
        ObservabilitySdkSupport.isSupported = false

        newManager().initializeFromCachedConfig()

        Thread.getDefaultUncaughtExceptionHandler() shouldBe originalHandler
    }

    test("initializeFromCachedConfig with no cached config leaves features off") {
        newManager().initializeFromCachedConfig()

        Thread.getDefaultUncaughtExceptionHandler() shouldBe originalHandler
    }

    test("a HYDRATE with remote logging enabled installs the crash handler") {
        val manager = newManager()
        manager.initializeFromCachedConfig()

        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.HYDRATE)

        Thread.getDefaultUncaughtExceptionHandler().shouldBeInstanceOf<AndroidLogCrashHandler>()
    }

    test("onModelReplaced is ignored when the SDK level is unsupported") {
        ObservabilitySdkSupport.isSupported = false
        val manager = newManager()

        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.HYDRATE)

        Thread.getDefaultUncaughtExceptionHandler() shouldBe originalHandler
    }

    test("onModelReplaced ignores non-HYDRATE tags") {
        val manager = newManager()
        manager.initializeFromCachedConfig()

        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.NORMAL)

        Thread.getDefaultUncaughtExceptionHandler() shouldBe originalHandler
    }

    test("disabling remotely unregisters the crash handler") {
        val manager = newManager()
        manager.initializeFromCachedConfig()

        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(configWith(isEnabled = false, logLevel = null), ModelChangeTags.HYDRATE)

        Thread.getDefaultUncaughtExceptionHandler() shouldBe originalHandler
    }

    test("full lifecycle: enable, change level, disable, re-enable") {
        val manager = newManager()
        manager.initializeFromCachedConfig()

        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.WARN), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.INFO), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(configWith(isEnabled = false, logLevel = null), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.DEBUG), ModelChangeTags.HYDRATE)

        Thread.getDefaultUncaughtExceptionHandler().shouldBeInstanceOf<AndroidLogCrashHandler>()
    }

    test("repeating the same config does not re-register the handler") {
        val manager = newManager()
        manager.initializeFromCachedConfig()

        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.HYDRATE)
        val afterFirst = Thread.getDefaultUncaughtExceptionHandler()
        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.HYDRATE)

        Thread.getDefaultUncaughtExceptionHandler() shouldBe afterFirst
    }
})

private fun configWith(isEnabled: Boolean, logLevel: LogLevel?): ConfigModel {
    val config = ConfigModel()
    config.remoteLoggingParams.isEnabled = isEnabled
    logLevel?.let { config.remoteLoggingParams.logLevel = it }
    return config
}
