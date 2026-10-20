package com.onesignal.core.internal.features

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.common.threading.OneSignalDispatchers
import com.onesignal.core.internal.config.CONFIG_NAME_SPACE
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.features.FeatureFlag
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.json.JSONArray
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

@RobolectricTest
class DispatcherBootstrapCacheTests : FunSpec({
    val key = FeatureFlag.SDK_DISPATCHER_BOOTSTRAP.key

    afterTest {
        clearFlags()
        OneSignalDispatchers.resetBootstrapInputsForTest()
        OneSignalDispatchers.resetForTest()
    }

    test("missing, empty, and unreadable caches are off") {
        DispatcherBootstrapCache.containsKey(null, key) shouldBe false
        DispatcherBootstrapCache.containsKey("", key) shouldBe false
        DispatcherBootstrapCache.containsKey("[]", key) shouldBe false
        DispatcherBootstrapCache.containsKey("not json", key) shouldBe false
        DispatcherBootstrapCache.containsKey("[{}]", key) shouldBe false
        DispatcherBootstrapCache.containsKey(
            """[{"sdkRemoteFeatureFlags":["sdk_custom_logging"]}]""",
            key,
        ) shouldBe false
    }

    test("the cached key enables bootstrap regardless of case") {
        val raw = """[{"sdkRemoteFeatureFlags":["SDK_Dispatcher_Bootstrap"]}]"""
        DispatcherBootstrapCache.containsKey(raw, key) shouldBe true
    }

    test("a persisted key selects bootstrap on the next pool generation") {
        val context = ApplicationProvider.getApplicationContext<Context>()
        writeFlags(context, listOf(key))
        latchFrom(context)

        val callerThreadId = AtomicLong()
        val createThreadId = AtomicLong()
        val createStarted = CountDownLatch(1)
        val finished = CountDownLatch(1)
        OneSignalDispatchers.beforeLaneCreateForTest = { lane ->
            if (lane == "IO") {
                createThreadId.set(Thread.currentThread().id)
                createStarted.countDown()
            }
        }
        Thread {
            callerThreadId.set(Thread.currentThread().id)
            OneSignalDispatchers.launchOnIO { }
            finished.countDown()
        }.start()

        createStarted.await(2, TimeUnit.SECONDS) shouldBe true
        finished.await(2, TimeUnit.SECONDS) shouldBe true
        createThreadId.get() shouldNotBe callerThreadId.get()
    }

    test("a cache without the key keeps construction on the caller") {
        val context = ApplicationProvider.getApplicationContext<Context>()
        writeFlags(context, emptyList())
        latchFrom(context)

        val callerThreadId = AtomicLong()
        val createThreadId = AtomicLong()
        val finished = CountDownLatch(1)
        OneSignalDispatchers.beforeLaneCreateForTest = { lane ->
            if (lane == "IO") createThreadId.set(Thread.currentThread().id)
        }
        Thread {
            callerThreadId.set(Thread.currentThread().id)
            OneSignalDispatchers.launchOnIO { }
            finished.countDown()
        }.start()

        finished.await(2, TimeUnit.SECONDS) shouldBe true
        createThreadId.get() shouldBe callerThreadId.get()
    }

    test("prewarm latches from the process application when startup did not note a context") {
        val context = ApplicationProvider.getApplicationContext<Context>()
        writeFlags(context, listOf(key))
        OneSignalDispatchers.resetBootstrapInputsForTest()
        OneSignalDispatchers.resetForTest()

        val callerThreadId = AtomicLong()
        val createThreadId = AtomicLong()
        val createStarted = CountDownLatch(1)
        val finished = CountDownLatch(1)
        OneSignalDispatchers.beforeLaneCreateForTest = { lane ->
            if (lane == "IO") {
                createThreadId.set(Thread.currentThread().id)
                createStarted.countDown()
            }
        }
        Thread {
            callerThreadId.set(Thread.currentThread().id)
            OneSignalDispatchers.launchOnIO { }
            finished.countDown()
        }.start()

        createStarted.await(2, TimeUnit.SECONDS) shouldBe true
        finished.await(2, TimeUnit.SECONDS) shouldBe true
        createThreadId.get() shouldNotBe callerThreadId.get()
    }
})

private fun latchFrom(context: Context) {
    OneSignalDispatchers.resetBootstrapInputsForTest()
    OneSignalDispatchers.noteApplicationContext(context)
    OneSignalDispatchers.resetForTest()
}

private fun clearFlags() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context
        .getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)
        .edit()
        .remove(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + CONFIG_NAME_SPACE)
        .commit()
}

private fun writeFlags(
    context: Context,
    flags: List<String>,
) {
    val model = ConfigModel().apply { sdkRemoteFeatureFlags = flags }
    val store = JSONArray().put(model.toJSON()).toString()
    context
        .getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)
        .edit()
        .putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + CONFIG_NAME_SPACE, store)
        .commit()
}
