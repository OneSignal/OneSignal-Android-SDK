package com.onesignal.core.internal.features

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.common.AppContext
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.common.modeling.ModelChangedArgs
import com.onesignal.core.internal.config.CONFIG_NAME_SPACE
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.features.FeatureFlag
import com.onesignal.features.StartupFlagsSource
import com.onesignal.mocks.MockHelper
import com.onesignal.mocks.MockPreferencesService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.json.JSONArray
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

@RobolectricTest
@Config(sdk = [Build.VERSION_CODES.O])
class StartupFlagsCacheTests : FunSpec({
    val logging = FeatureFlag.SDK_CUSTOM_LOGGING
    val key = logging.key

    afterTest {
        clearStores()
        FeatureManager.resetStartupFlagsForTest()
        AppContext.resetForTest()
    }

    test("a missing file is off") {
        startupPrefs().edit().clear().commit()
        val flags = StartupFlagsCache.readFile(appContext())

        flags.source shouldBe StartupFlagsSource.MISSING
        flags.isEnabled(logging) shouldBe false
    }

    test("a failed commit is reported, not silently dropped") {
        val context = mockk<Context>()
        val prefs = mockk<SharedPreferences>()
        every { context.applicationContext } returns context
        every { context.createDeviceProtectedStorageContext() } returns context
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.getStringSet(any(), any()) } returns null
        every { prefs.edit().putStringSet(any(), any()).commit() } returns false

        shouldThrow<IllegalStateException> { StartupFlagsCache.write(context) { listOf(key) } }
    }

    test("turning a flag off remotely reaches the next process") {
        val context = appContext()
        val store = ConfigModelStore(MockPreferencesService())
        store.model.sdkRemoteFeatureFlags = listOf(key)
        val applicationService = MockHelper.applicationService()
        every { applicationService.appContext } returns context

        val first = FeatureManager(store, applicationService)
        first.start()
        FeatureManager.resetStartupFlagsForTest()
        val second = FeatureManager(store, applicationService)
        second.start()
        second.isEnabled(logging) shouldBe true

        store.model.setListProperty(ConfigModel::sdkRemoteFeatureFlags.name, emptyList<String>(), ModelChangeTags.NORMAL)
        second.isEnabled(logging) shouldBe true

        FeatureManager.resetStartupFlagsForTest()
        FeatureManager.startupFlags { context }.isEnabled(logging) shouldBe false
        FeatureManager(store, applicationService).isEnabled(logging) shouldBe false
    }

    test("the file follows the remote flags, regardless of case") {
        val context = appContext()
        StartupFlagsCache.write(context) { listOf("SDK_Custom_Logging") }
        StartupFlagsCache.readFile(context).isEnabled(logging) shouldBe true
        StartupFlagsCache.write(context) { listOf(FeatureFlag.SDK_IDENTITY_VERIFICATION.key) }
        StartupFlagsCache.readFile(context).isEnabled(logging) shouldBe false
    }

    test("racing writes leave the newest flags on disk") {
        val context = appContext()
        val current = AtomicReference(listOf(key))
        val slowHasRead = CountDownLatch(1)
        val releaseSlow = CountDownLatch(1)

        val slow =
            thread {
                StartupFlagsCache.write(context) {
                    val read = current.get()
                    slowHasRead.countDown()
                    releaseSlow.await()
                    read
                }
            }
        slowHasRead.await()
        current.set(emptyList())
        val fast = thread { StartupFlagsCache.write(context) { current.get() } }
        fast.join(200)
        releaseSlow.countDown()
        slow.join()
        fast.join()

        StartupFlagsCache.readFile(context).isEnabled(logging) shouldBe false
    }

    test("only APP_STARTUP catalog keys reach the file") {
        val context = appContext()
        StartupFlagsCache.write(context) { listOf(key, FeatureFlag.SDK_IDENTITY_VERIFICATION.key, "not_in_catalog") }
        startupPrefs().getStringSet("enabled", null) shouldBe setOf(key)
    }

    test("the file lives in device-protected storage, so it is readable before first unlock") {
        val context = appContext()
        StartupFlagsCache.write(context) { listOf(key) }

        startupPrefs().getStringSet("enabled", null) shouldBe setOf(key)
        context.getSharedPreferences(PrefsStartupFlagsStore.STORE, Context.MODE_PRIVATE).getStringSet("enabled", null) shouldBe null
    }

    test("a locked boot still reads the flags") {
        val unlockedOnly = mockk<SharedPreferences>()
        every { unlockedOnly.getStringSet(any(), any()) } throws IllegalStateException("credential storage locked")
        val deviceProtected = mockk<SharedPreferences>()
        every { deviceProtected.getStringSet(any(), any()) } returns setOf(key)
        val dpContext = mockk<Context>()
        every { dpContext.getSharedPreferences(any(), any()) } returns deviceProtected
        val locked = mockk<Context>()
        every { locked.applicationContext } returns locked
        every { locked.getSharedPreferences(any(), any()) } returns unlockedOnly
        every { locked.createDeviceProtectedStorageContext() } returns dpContext

        val flags = StartupFlagsCache.readFile(locked)

        flags.source shouldBe StartupFlagsSource.STORE
        flags.isEnabled(logging) shouldBe true
    }

    test("a context with no application context yet still reads device-protected flags") {
        val deviceProtected = mockk<SharedPreferences>()
        every { deviceProtected.getStringSet(any(), any()) } returns setOf(key)
        val dpContext = mockk<Context>()
        every { dpContext.getSharedPreferences(any(), any()) } returns deviceProtected
        val attaching = mockk<Context>()
        every { attaching.applicationContext } returns null
        every { attaching.createDeviceProtectedStorageContext() } returns dpContext

        val flags = StartupFlagsCache.readFile(attaching)

        flags.source shouldBe StartupFlagsSource.STORE
        flags.isEnabled(logging) shouldBe true
    }

    test("an IMMEDIATE flag is off even if its key is in the file") {
        val immediate = FeatureFlag.SDK_IDENTITY_VERIFICATION
        startupPrefs().edit().putStringSet("enabled", setOf(immediate.key)).commit()

        StartupFlagsCache.readFile(appContext()).isEnabled(immediate) shouldBe false
    }

    test("read ignores the OneSignal config store") {
        val context = appContext()
        writeConfigStore(context, listOf(key))
        StartupFlagsCache.readFile(context).isEnabled(logging) shouldBe false
    }

    test("an unreadable file is off") {
        val broken = mockk<Context>()
        every { broken.applicationContext } returns broken
        every { broken.createDeviceProtectedStorageContext() } returns broken
        every { broken.getSharedPreferences(any(), any()) } throws SecurityException("prefs unavailable")

        val flags = StartupFlagsCache.readFile(broken)

        flags.source shouldBe StartupFlagsSource.UNREADABLE
        flags.isEnabled(logging) shouldBe false
    }

    test("an unreadable file is rewritten") {
        val context = mockk<Context>()
        val prefs = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>()
        every { context.applicationContext } returns context
        every { context.createDeviceProtectedStorageContext() } returns context
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.getStringSet(any(), any()) } throws ClassCastException("not a string set")
        every { prefs.edit() } returns editor
        every { editor.putStringSet(any(), any()) } returns editor
        every { editor.commit() } returns true

        StartupFlagsCache.write(context) { listOf(key) }

        verify { editor.putStringSet("enabled", setOf(key)) }
    }

    test("no application context is off") {
        StartupFlagsCache.readFile(null).source shouldBe StartupFlagsSource.NO_STORE
    }

    test("the snapshot is read once per process") {
        val context = appContext()
        StartupFlagsCache.write(context) { listOf(key) }
        FeatureManager.resetStartupFlagsForTest()
        FeatureManager.startupFlags { context }.isEnabled(logging) shouldBe true

        StartupFlagsCache.write(context) { emptyList() }
        FeatureManager.startupFlags { context }.isEnabled(logging) shouldBe true

        FeatureManager.resetStartupFlagsForTest()
        FeatureManager.startupFlags { context }.isEnabled(logging) shouldBe false
    }

    test("FeatureManager reports the snapshot, not the store, when they disagree") {
        val context = appContext()
        StartupFlagsCache.write(context) { emptyList() }
        FeatureManager.startupFlags { context }.isEnabled(logging) shouldBe false

        val store = MockHelper.configModelStore { it.sdkRemoteFeatureFlags = listOf(key) }
        val applicationService = MockHelper.applicationService()
        every { applicationService.appContext } returns context
        val manager = FeatureManager(store, applicationService)

        manager.isEnabled(logging) shouldBe false
        manager.enabledFeatureKeys() shouldBe emptyList()
    }

    test("FeatureManager built before ApplicationService starts reads the file through the noted context") {
        StartupFlagsCache.write(appContext()) { listOf(key) }
        FeatureManager.resetStartupFlagsForTest()
        AppContext.note(appContext())
        val applicationService = MockHelper.applicationService()
        every { applicationService.appContext } throws NullPointerException("not started")

        val manager = FeatureManager(MockHelper.configModelStore { it.sdkRemoteFeatureFlags = listOf(key) }, applicationService)

        manager.isEnabled(logging) shouldBe true
        FeatureManager.startupFlags { null }.source shouldBe StartupFlagsSource.STORE
    }

    test("with no context anywhere the snapshot is off rather than guessed") {
        StartupFlagsCache.write(appContext()) { listOf(key) }
        FeatureManager.resetStartupFlagsForTest()
        ShadowLog.clear()

        FeatureManager.startupFlags { null }.source shouldBe StartupFlagsSource.NO_STORE
        ShadowLog.getLogsForTag(PrefsStartupFlagsStore.TAG).any {
            it.type == Log.ERROR && "no application context" in it.msg
        } shouldBe true
    }

    test("after a failed commit the next write retries, even with the same flags") {
        val context = mockk<Context>()
        val prefs = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>()
        var inMemory: Set<String>? = null
        var pending: Set<String>? = null
        val commits = ArrayDeque(listOf(false, true))
        every { context.applicationContext } returns context
        every { context.createDeviceProtectedStorageContext() } returns context
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.getStringSet(any(), any()) } answers { inMemory }
        every { prefs.edit() } returns editor
        every { editor.putStringSet(any(), any()) } answers { pending = secondArg(); editor }
        // Like SharedPreferences, a failed commit still updates the in-memory copy.
        every { editor.commit() } answers { inMemory = pending; commits.removeFirst() }

        shouldThrow<IllegalStateException> { StartupFlagsCache.write(context) { listOf(key) } }
        StartupFlagsCache.write(context) { listOf(key) }

        verify(exactly = 2) { editor.commit() }
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

private fun startupPrefs(): SharedPreferences =
    appContext().createDeviceProtectedStorageContext().getSharedPreferences(PrefsStartupFlagsStore.STORE, Context.MODE_PRIVATE)

private fun clearStores() {
    val context = appContext()
    StartupFlagsCache.write(context) { emptyList() }
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
