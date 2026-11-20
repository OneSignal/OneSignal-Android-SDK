package com.onesignal.core.internal.application

import android.content.Context
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.IOneSignal
import com.onesignal.common.services.IServiceProvider
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.internal.OneSignalImp
import com.onesignal.internal.OneSignalImpV2
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Runs the same scenario on the legacy and the never-crash implementation and compares what the app
 * can observe. The only intended differences are the calls that used to throw, which are covered by
 * the V2 suites; everything here must stay identical.
 */
@RobolectricTest
class OneSignalImpParityTests : FunSpec({

    fun IOneSignal.awaitInit(timeoutMs: Long = 5_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!isInitialized && System.currentTimeMillis() < deadline) Thread.sleep(10)
        return isInitialized
    }

    fun IOneSignal.awaitExternalId(expected: String, timeoutMs: Long = 5_000): String {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (user.externalId != expected && System.currentTimeMillis() < deadline) Thread.sleep(10)
        return user.externalId
    }

    fun IOneSignal.appIdInConfig() = (this as IServiceProvider).getService(ConfigModelStore::class.java).model.appId

    // PreferencesService flushes on a 200ms buffer, so an instance from the previous scenario can still
    // write after a clear; let it land first.
    fun clearPrefs() {
        Thread.sleep(400)
        val context = getApplicationContext<Context>()
        for (name in listOf("OneSignal", "com.onesignal")) {
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    /** Inits a throwaway instance of the same implementation and waits until its app ID is on disk. */
    fun IOneSignal.cacheAppId(context: Context, appId: String) {
        val warm = if (this is OneSignalImpV2) OneSignalImpV2() else OneSignalImp()
        Logging.logLevel = LogLevel.NONE
        warm.initWithContext(context, appId)
        warm.awaitInit() shouldBe true
        val prefs = context.getSharedPreferences("OneSignal", Context.MODE_PRIVATE)
        val deadline = System.currentTimeMillis() + 5_000
        while (prefs.all.values.none { it.toString().contains(appId) } && System.currentTimeMillis() < deadline) Thread.sleep(10)
        prefs.all.values.any { it.toString().contains(appId) } shouldBe true
    }

    fun implementations(): List<Pair<String, () -> IOneSignal>> =
        listOf(
            "legacy" to { OneSignalImp().also { Logging.logLevel = LogLevel.NONE } },
            "v2" to { OneSignalImpV2().also { Logging.logLevel = LogLevel.NONE } },
        )

    /** Runs [scenario] on a fresh instance of each implementation, with clean prefs, and compares the results. */
    fun parity(scenario: (IOneSignal, Context) -> Any?) {
        val results =
            implementations().map { (name, create) ->
                clearPrefs()
                name to scenario(create(), getApplicationContext())
            }
        withClue("legacy=${results[0].second} v2=${results[1].second}") {
            results[1].second shouldBe results[0].second
        }
    }

    beforeAny { Logging.logLevel = LogLevel.NONE }
    afterAny { clearPrefs() }

    test("init then reading every top-level value") {
        parity { os, context ->
            os.initWithContext(context, "appId")
            os.awaitInit() shouldBe true
            os.isInitialized shouldBe true
            os.appIdInConfig() shouldBe "appId"
            listOf(
                os.isInitialized,
                os.user.onesignalId.isNotEmpty(),
                os.user.externalId,
                os.user.pushSubscription.optedIn,
                os.user.pushSubscription.id,
                os.user.getTags(),
                os.consentRequired,
                os.consentGiven,
                os.disableGMSMissingPrompt,
                os.appIdInConfig(),
            )
        }
    }

    test("settings changed before init reach the config model") {
        parity { os, context ->
            os.consentRequired = true
            os.disableGMSMissingPrompt = true
            val beforeInit = listOf(os.consentRequired, os.consentGiven, os.disableGMSMissingPrompt)
            os.initWithContext(context, "appId")
            os.awaitInit() shouldBe true
            os.isInitialized shouldBe true
            val model = (os as IServiceProvider).getService(ConfigModelStore::class.java).model
            beforeInit + listOf(os.consentRequired, os.consentGiven, os.disableGMSMissingPrompt, model.consentRequired, model.consentGiven, model.disableGMSMissingPrompt)
        }
    }

    test("a second initWithContext after success keeps the first app ID") {
        parity { os, context ->
            os.initWithContext(context, "appId")
            os.awaitInit() shouldBe true
            os.isInitialized shouldBe true
            os.appIdInConfig() shouldBe "appId"
            val second = os.initWithContext(context, "otherAppId")
            Thread.sleep(100)
            listOf(second, os.isInitialized, os.appIdInConfig())
        }
    }

    test("login, tags and logout after init") {
        parity { os, context ->
            os.initWithContext(context, "appId")
            os.awaitInit() shouldBe true
            os.isInitialized shouldBe true
            os.login("ext-1")
            val afterLogin = os.awaitExternalId("ext-1")
            afterLogin shouldBe "ext-1"
            os.user.addTags(mapOf("a" to "1", "b" to "2"))
            os.user.removeTag("b")
            val tags = os.user.getTags()
            os.logout()
            val afterLogout = os.awaitExternalId("")
            listOf(afterLogin, tags, afterLogout, os.user.onesignalId.isNotEmpty())
        }
    }

    test("a cached app ID is reused when init is called with an empty app ID") {
        parity { os, context ->
            os.cacheAppId(context, "cachedAppId")

            os.initWithContext(context, "")
            os.awaitInit() shouldBe true
            os.isInitialized shouldBe true
            os.appIdInConfig() shouldBe "cachedAppId"
            listOf(os.isInitialized, os.appIdInConfig())
        }
    }

    test("init with a null app ID resolves from the cache written by an earlier init") {
        parity { os, context ->
            os.cacheAppId(context, "cachedAppId")

            val result =
                kotlinx.coroutines.runBlocking { os.initWithContextSuspend(context, null) }
            result shouldBe true
            os.isInitialized shouldBe true
            os.appIdInConfig() shouldBe "cachedAppId"
            listOf(result, os.isInitialized, os.appIdInConfig())
        }
    }
})
