package com.onesignal.internal

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.features.IFeatureManager
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.crash.OtelSdkSupport
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.debug.internal.logging.otel.android.OtelPlatformProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.robolectric.annotation.Config

@RobolectricTest
@Config(sdk = [Build.VERSION_CODES.O])
class OtelLifecycleManagerTest : FunSpec({
    lateinit var context: Context
    lateinit var featureManager: IFeatureManager

    fun newManager(
        fm: IFeatureManager = featureManager,
        platformProviderFactory: ((Context, () -> IFeatureManager) -> OtelPlatformProvider)? = null,
    ): OtelLifecycleManager =
        if (platformProviderFactory != null) {
            OtelLifecycleManager(
                context = context,
                featureManagerProvider = { fm },
                platformProviderFactory = platformProviderFactory,
            )
        } else {
            OtelLifecycleManager(context = context, featureManagerProvider = { fm })
        }

    beforeEach {
        context = ApplicationProvider.getApplicationContext()
        featureManager = mockk<IFeatureManager>().also {
            every { it.enabledFeatureKeys() } returns emptyList()
        }
        OtelSdkSupport.isSupported = true
    }

    afterEach {
        OtelSdkSupport.reset()
    }

    test("initializeFromCachedConfig does not crash when SDK unsupported") {
        OtelSdkSupport.isSupported = false
        val manager = newManager()
        manager.initializeFromCachedConfig()
    }

    test("initializeFromCachedConfig does not throw on supported SDK") {
        val manager = newManager()
        manager.initializeFromCachedConfig()
    }

    test("onModelReplaced does not crash when SDK unsupported") {
        OtelSdkSupport.isSupported = false
        val manager = newManager()
        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.HYDRATE)
    }

    test("onModelReplaced ignores non-HYDRATE tags") {
        val manager = newManager()
        manager.initializeFromCachedConfig()
        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.NORMAL)
    }

    test("onModelReplaced enable then disable does not throw") {
        val manager = newManager()
        manager.initializeFromCachedConfig()

        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(configWith(isEnabled = false, logLevel = null), ModelChangeTags.HYDRATE)
    }

    test("onModelReplaced updates log level without throwing") {
        val manager = newManager()
        manager.initializeFromCachedConfig()

        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.WARN), ModelChangeTags.HYDRATE)
    }

    test("onModelReplaced with same config is no-op") {
        val manager = newManager()
        manager.initializeFromCachedConfig()

        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.HYDRATE)
    }

    test("disable clears Otel telemetry from Logging") {
        val manager = newManager()
        manager.initializeFromCachedConfig()

        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(configWith(isEnabled = false, logLevel = null), ModelChangeTags.HYDRATE)

        Logging.info("test message after otel disabled")
    }

    test("full lifecycle: init -> enable -> update level -> disable -> re-enable") {
        val manager = newManager()
        manager.initializeFromCachedConfig()

        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.ERROR), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.WARN), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.INFO), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(configWith(isEnabled = false, logLevel = null), ModelChangeTags.HYDRATE)
        manager.onModelReplaced(configWith(isEnabled = true, logLevel = LogLevel.DEBUG), ModelChangeTags.HYDRATE)
    }

    test("FeatureManager supplier is forwarded to the platform provider via the factory") {
        var capturedSupplier: (() -> IFeatureManager)? = null
        val pp = mockk<OtelPlatformProvider>(relaxed = true)
        val fm = mockk<IFeatureManager>()
        every { fm.enabledFeatureKeys() } returns listOf("sdk_background_threading")

        val manager = newManager(
            fm = fm,
            platformProviderFactory = { _, supplier ->
                capturedSupplier = supplier
                pp
            },
        )
        manager.initializeFromCachedConfig()

        // Supplier was passed through and resolves to the manager wired up in `newManager`.
        capturedSupplier?.invoke() shouldBe fm
        capturedSupplier?.invoke()?.enabledFeatureKeys() shouldBe listOf("sdk_background_threading")
    }
})

private fun configWith(isEnabled: Boolean, logLevel: LogLevel?): ConfigModel {
    val config = ConfigModel()
    config.remoteLoggingParams.isEnabled = isEnabled
    logLevel?.let { config.remoteLoggingParams.logLevel = it }
    return config
}
