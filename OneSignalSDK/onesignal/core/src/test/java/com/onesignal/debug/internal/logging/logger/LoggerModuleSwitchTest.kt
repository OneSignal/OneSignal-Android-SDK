package com.onesignal.debug.internal.logging.logger

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.features.FeatureFlag
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.json.JSONArray
import org.json.JSONObject
import com.onesignal.core.internal.config.CONFIG_NAME_SPACE as configNameSpace

/**
 * End-to-end coverage for the otel-vs-logger routing switch. Asserts [LoggerModuleSwitch.useLoggerModule]
 * reflects the SDK_CUSTOM_LOGGING flag as persisted in the cached config (the same prefs the previous
 * session wrote), which is how the choice is made during early init before service bootstrap.
 */
@RobolectricTest
class LoggerModuleSwitchTest : FunSpec({

    lateinit var appContext: Context
    lateinit var sharedPreferences: SharedPreferences

    fun writeCachedFeatureFlags(vararg flags: String) {
        val configModel = JSONObject().apply {
            put(ConfigModel::sdkRemoteFeatureFlags.name, JSONArray().apply { flags.forEach { put(it) } })
        }
        val configArray = JSONArray().apply { put(configModel) }
        sharedPreferences.edit()
            .putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, configArray.toString())
            .commit()
    }

    beforeEach {
        appContext = ApplicationProvider.getApplicationContext()
        sharedPreferences = appContext.getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)
        sharedPreferences.edit().clear().commit()
    }

    afterEach {
        sharedPreferences.edit().clear().commit()
    }

    test("useLoggerModule returns true when SDK_CUSTOM_LOGGING is cached") {
        writeCachedFeatureFlags(FeatureFlag.SDK_CUSTOM_LOGGING.key)

        LoggerModuleSwitch.useLoggerModule(appContext) shouldBe true
    }

    test("useLoggerModule returns false when SDK_CUSTOM_LOGGING is not cached") {
        writeCachedFeatureFlags("sdk_identity_verification")

        LoggerModuleSwitch.useLoggerModule(appContext) shouldBe false
    }

    test("useLoggerModule returns false when no config is cached") {
        LoggerModuleSwitch.useLoggerModule(appContext) shouldBe false
    }
})
