package com.onesignal.core.internal.features

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.common.modeling.ModelChangedArgs
import com.onesignal.core.internal.config.CONFIG_NAME_SPACE
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.features.FeatureFlag
import com.onesignal.mocks.MockHelper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.json.JSONArray

@RobolectricTest
class StartupFlagsCacheTests : FunSpec({
    val logging = FeatureFlag.SDK_CUSTOM_LOGGING
    val key = logging.key

    afterTest {
        clearStores()
        FeatureManager.resetStartupFlagsForTest()
    }

    test("a missing file is off") {
        StartupFlagsCache.readFile(appContext()).isEnabled(logging) shouldBe false
    }

    test("the file follows the remote flags, regardless of case") {
        val context = appContext()
        StartupFlagsCache.write(context, listOf("SDK_Custom_Logging"))
        StartupFlagsCache.readFile(context).isEnabled(logging) shouldBe true
        StartupFlagsCache.write(context, listOf(FeatureFlag.SDK_IDENTITY_VERIFICATION.key))
        StartupFlagsCache.readFile(context).isEnabled(logging) shouldBe false
    }

    test("only APP_STARTUP catalog keys reach the file") {
        val context = appContext()
        StartupFlagsCache.write(context, listOf(key, FeatureFlag.SDK_IDENTITY_VERIFICATION.key, "not_in_catalog"))
        context
            .getSharedPreferences("OneSignalStartupFlags", Context.MODE_PRIVATE)
            .getStringSet("enabled", null) shouldBe setOf(key)
    }

    test("reading an IMMEDIATE flag is rejected") {
        shouldThrow<IllegalArgumentException> {
            StartupFlagsCache.readFile(appContext()).isEnabled(FeatureFlag.SDK_IDENTITY_VERIFICATION)
        }
    }

    test("read ignores the OneSignal config store") {
        val context = appContext()
        writeConfigStore(context, listOf(key))
        StartupFlagsCache.readFile(context).isEnabled(logging) shouldBe false
    }

    test("an unreadable file is off") {
        val broken = mockk<Context>()
        every { broken.applicationContext } returns broken
        every { broken.getSharedPreferences(any(), any()) } throws SecurityException("prefs unavailable")

        val flags = StartupFlagsCache.readFile(broken)

        flags.source shouldBe StartupFlags.Source.UNREADABLE
        flags.isEnabled(logging) shouldBe false
    }

    test("no application context is off") {
        StartupFlagsCache.readFile(null).source shouldBe StartupFlags.Source.NO_APPLICATION
    }

    test("the snapshot is read once per process") {
        val context = appContext()
        StartupFlagsCache.write(context, listOf(key))
        FeatureManager.resetStartupFlagsForTest()
        FeatureManager.startupFlags { context }.isEnabled(logging) shouldBe true

        StartupFlagsCache.write(context, emptyList())
        FeatureManager.startupFlags { context }.isEnabled(logging) shouldBe true

        FeatureManager.resetStartupFlagsForTest()
        FeatureManager.startupFlags { context }.isEnabled(logging) shouldBe false
    }

    test("FeatureManager reports the snapshot, not the store, when they disagree") {
        val context = appContext()
        StartupFlagsCache.write(context, emptyList())
        FeatureManager.startupFlags { context }.isEnabled(logging) shouldBe false

        val store = MockHelper.configModelStore { it.sdkRemoteFeatureFlags = listOf(key) }
        val applicationService = MockHelper.applicationService()
        every { applicationService.appContext } returns context
        val manager = FeatureManager(store, applicationService)

        manager.isEnabled(logging) shouldBe false
        manager.enabledFeatureKeys() shouldBe emptyList()
    }

    test("FeatureManager mirrors the cached flags on start and on later updates, never before start") {
        val context = appContext()
        val store = MockHelper.configModelStore { it.sdkRemoteFeatureFlags = listOf(key) }
        val applicationService = MockHelper.applicationService()
        every { applicationService.appContext } returns context
        val manager = FeatureManager(store, applicationService)
        val update =
            ModelChangedArgs(store.model, ConfigModel::sdkRemoteFeatureFlags.name, ConfigModel::sdkRemoteFeatureFlags.name, null, null)

        manager.onModelUpdated(update, ModelChangeTags.NORMAL)
        StartupFlagsCache.readFile(context).isEnabled(logging) shouldBe false

        manager.start()
        StartupFlagsCache.readFile(context).isEnabled(logging) shouldBe true

        store.model.sdkRemoteFeatureFlags = emptyList()
        manager.onModelUpdated(update, ModelChangeTags.NORMAL)
        StartupFlagsCache.readFile(context).isEnabled(logging) shouldBe false
    }
})

private fun appContext(): Context = ApplicationProvider.getApplicationContext()

private fun clearStores() {
    val context = appContext()
    StartupFlagsCache.write(context, emptyList())
    context
        .getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)
        .edit()
        .remove(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + CONFIG_NAME_SPACE)
        .commit()
}

private fun writeConfigStore(
    context: Context,
    flags: List<String>,
) {
    val model = ConfigModel().apply { sdkRemoteFeatureFlags = flags }
    context
        .getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)
        .edit()
        .putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + CONFIG_NAME_SPACE, JSONArray().put(model.toJSON()).toString())
        .commit()
}
