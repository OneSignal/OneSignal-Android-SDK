package com.onesignal.internal

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.crash.OtelSdkSupport
import com.onesignal.debug.internal.logging.Logging
import io.kotest.core.spec.style.FunSpec
import org.robolectric.annotation.Config

@RobolectricTest
@Config(sdk = [Build.VERSION_CODES.O])
class OtelLifecycleManagerTest : FunSpec({
    lateinit var context: Context

    beforeEach {
        context = ApplicationProvider.getApplicationContext()
        OtelSdkSupport.isSupported = true
    }

    afterEach {
        OtelSdkSupport.reset()
    }

    test("initializeFromCachedConfig does not crash when SDK unsupported") {
        OtelSdkSupport.isSupported = false
        val manager = OtelLifecycleManager(context)
        manager.initializeFromCachedConfig()
    }

    test("initializeFromCachedConfig does not throw on supported SDK") {
        val manager = OtelLifecycleManager(context)
        manager.initializeFromCachedConfig()
    }

    test("onModelReplaced does not crash when SDK unsupported") {
        OtelSdkSupport.isSupported = false
        val manager = OtelLifecycleManager(context)
        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.HYDRATE)
    }

    test("onModelReplaced ignores non-HYDRATE tags") {
        val manager = OtelLifecycleManager(context)
        manager.initializeFromCachedConfig()
        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.NORMAL)
    }

    test("onModelReplaced enable then disable does not throw") {
        val manager = OtelLifecycleManager(context)
        manager.initializeFromCachedConfig()

        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(configWith(isEnabled = false, logLevel = null), ModelChangeTags.HYDRATE)
    }

    test("onModelReplaced updates log level without throwing") {
        val manager = OtelLifecycleManager(context)
        manager.initializeFromCachedConfig()

        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.WARN), ModelChangeTags.HYDRATE)
    }

    test("onModelReplaced with same config is no-op") {
        val manager = OtelLifecycleManager(context)
        manager.initializeFromCachedConfig()

        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.HYDRATE)
    }

    test("disable clears Otel telemetry from Logging") {
        val manager = OtelLifecycleManager(context)
        manager.initializeFromCachedConfig()

        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(configWith(isEnabled = false, logLevel = null), ModelChangeTags.HYDRATE)

        Logging.info("test message after otel disabled")
    }

    test("full lifecycle: init -> enable -> update level -> disable -> re-enable") {
        val manager = OtelLifecycleManager(context)
        manager.initializeFromCachedConfig()

        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.WARN), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.INFO), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(configWith(isEnabled = false, logLevel = null), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.DEBUG), ModelChangeTags.HYDRATE)
    }
})

private fun configWith(isEnabled: Boolean, logLevel: LogLevel?): ConfigModel {
    val config = ConfigModel()
    config.remoteLoggingParams.isEnabled = isEnabled
    logLevel?.let { config.remoteLoggingParams.logLevel = it }
    return config
}
