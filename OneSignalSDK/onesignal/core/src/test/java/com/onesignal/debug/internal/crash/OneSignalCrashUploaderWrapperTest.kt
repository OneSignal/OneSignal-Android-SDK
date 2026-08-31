package com.onesignal.debug.internal.crash

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.features.IFeatureManager
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.debug.internal.logging.logger.android.getCrashStoragePath
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.robolectric.annotation.Config
import java.io.File
import kotlin.time.Duration.Companion.seconds
import com.onesignal.core.internal.config.CONFIG_NAME_SPACE as configNameSpace

@RobolectricTest
@Config(sdk = [Build.VERSION_CODES.O])
class OneSignalCrashUploaderWrapperTest : FunSpec({

    lateinit var appContext: Context
    lateinit var sharedPreferences: SharedPreferences

    beforeAny {
        appContext = ApplicationProvider.getApplicationContext()
        sharedPreferences = appContext.getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)
    }

    afterEach {
        sharedPreferences.edit().clear().commit()
    }

    fun mockFeatureManager(): IFeatureManager {
        val featureManager = mockk<IFeatureManager>()
        every { featureManager.enabledFeatureKeys() } returns emptyList()
        every { featureManager.remoteFeatureFlagMetadata() } returns null
        return featureManager
    }

    test("should implement IStartableService interface") {
        val mockApplicationService = mockk<IApplicationService>(relaxed = true)
        every { mockApplicationService.appContext } returns appContext

        val wrapper = OneSignalCrashUploaderWrapper(mockApplicationService, mockFeatureManager())

        wrapper.shouldBeInstanceOf<IStartableService>()
    }

    test("start should complete without error when remote logging is disabled") {
        val remoteLoggingParams = JSONObject().put("logLevel", "NONE")
        val configModel = JSONObject().put(ConfigModel::remoteLoggingParams.name, remoteLoggingParams)
        sharedPreferences.edit()
            .putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, JSONArray().put(configModel).toString())
            .commit()

        val mockApplicationService = mockk<IApplicationService>(relaxed = true)
        every { mockApplicationService.appContext } returns appContext

        val wrapper = OneSignalCrashUploaderWrapper(mockApplicationService, mockFeatureManager())

        runBlocking { wrapper.start() }
    }

    test("start should complete without error when no crash reports exist") {
        val remoteLoggingParams = JSONObject().put("logLevel", "ERROR")
        val configModel = JSONObject().put(ConfigModel::remoteLoggingParams.name, remoteLoggingParams)
        sharedPreferences.edit()
            .putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, JSONArray().put(configModel).toString())
            .commit()

        val mockApplicationService = mockk<IApplicationService>(relaxed = true)
        every { mockApplicationService.appContext } returns appContext

        val wrapper = OneSignalCrashUploaderWrapper(mockApplicationService, mockFeatureManager())

        runBlocking { wrapper.start() }
    }

    test("start can be called multiple times safely") {
        val mockApplicationService = mockk<IApplicationService>(relaxed = true)
        every { mockApplicationService.appContext } returns appContext

        val wrapper = OneSignalCrashUploaderWrapper(mockApplicationService, mockFeatureManager())

        runBlocking {
            wrapper.start()
            wrapper.start()
        }
    }

    test("wrapper should be non-null after creation") {
        val mockApplicationService = mockk<IApplicationService>(relaxed = true)
        every { mockApplicationService.appContext } returns appContext

        val wrapper = OneSignalCrashUploaderWrapper(mockApplicationService, mockFeatureManager())

        wrapper shouldNotBe null
    }

    // First launch after upgrade has no cached config, so reclaim must run without an upload.
    test("start reclaims records left in the crash dir by a pre-upgrade otel session") {
        val crashDir = File(getCrashStoragePath(appContext)).apply { mkdirs() }
        // Pre-upgrade sessions wrote bare-millis filenames; the logger owns `.otlp` only.
        val legacyRecord = File(crashDir, "1784621689841").apply {
            writeBytes("legacy".toByteArray())
            setLastModified(System.currentTimeMillis() - 60_000L)
        }
        val ownedRecord = File(crashDir, "1784621689841-abc.otlp").apply {
            writeBytes("owned".toByteArray())
            setLastModified(System.currentTimeMillis() - 60_000L)
        }

        val mockApplicationService = mockk<IApplicationService>(relaxed = true)
        every { mockApplicationService.appContext } returns appContext

        val wrapper = OneSignalCrashUploaderWrapper(mockApplicationService, mockFeatureManager())
        runBlocking { wrapper.start() }

        eventually(10.seconds) { legacyRecord.exists() shouldBe false }
        // A pending logger-owned record is never collateral damage.
        ownedRecord.exists() shouldBe true
        crashDir.deleteRecursively()
    }
})
