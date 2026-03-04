package com.onesignal.debug.internal.crash

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.core.internal.startup.IStartableService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.robolectric.annotation.Config
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

    test("should implement IStartableService interface") {
        val mockApplicationService = mockk<IApplicationService>(relaxed = true)
        every { mockApplicationService.appContext } returns appContext

        val wrapper = OneSignalCrashUploaderWrapper(mockApplicationService)

        wrapper.shouldBeInstanceOf<IStartableService>()
    }

    test("start should complete without error when remote logging is disabled") {
        // Configure remote logging as disabled (NONE)
        val remoteLoggingParams = JSONObject().put("logLevel", "NONE")
        val configModel = JSONObject().put(ConfigModel::remoteLoggingParams.name, remoteLoggingParams)
        sharedPreferences.edit()
            .putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, JSONArray().put(configModel).toString())
            .commit()

        val mockApplicationService = mockk<IApplicationService>(relaxed = true)
        every { mockApplicationService.appContext } returns appContext

        val wrapper = OneSignalCrashUploaderWrapper(mockApplicationService)

        // Should return early without error when remote logging is disabled
        runBlocking { wrapper.start() }
    }

    test("start should complete without error when no crash reports exist") {
        // Configure remote logging as enabled
        val remoteLoggingParams = JSONObject().put("logLevel", "ERROR")
        val configModel = JSONObject().put(ConfigModel::remoteLoggingParams.name, remoteLoggingParams)
        sharedPreferences.edit()
            .putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, JSONArray().put(configModel).toString())
            .commit()

        val mockApplicationService = mockk<IApplicationService>(relaxed = true)
        every { mockApplicationService.appContext } returns appContext

        val wrapper = OneSignalCrashUploaderWrapper(mockApplicationService)

        // Should complete without error even when no crash reports exist
        runBlocking { wrapper.start() }
    }

    test("start can be called multiple times safely") {
        val mockApplicationService = mockk<IApplicationService>(relaxed = true)
        every { mockApplicationService.appContext } returns appContext

        val wrapper = OneSignalCrashUploaderWrapper(mockApplicationService)

        // Multiple calls should not throw
        runBlocking {
            wrapper.start()
            wrapper.start()
        }
    }

    test("wrapper should be non-null after creation") {
        val mockApplicationService = mockk<IApplicationService>(relaxed = true)
        every { mockApplicationService.appContext } returns appContext

        val wrapper = OneSignalCrashUploaderWrapper(mockApplicationService)

        wrapper shouldNotBe null
    }
})
