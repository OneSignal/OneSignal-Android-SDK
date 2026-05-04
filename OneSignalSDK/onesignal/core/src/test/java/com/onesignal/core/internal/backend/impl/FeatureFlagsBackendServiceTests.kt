package com.onesignal.core.internal.backend.impl

import com.onesignal.common.OneSignalUtils
import com.onesignal.core.internal.backend.RemoteFeatureFlagsFetchOutcome
import com.onesignal.core.internal.http.HttpResponse
import com.onesignal.core.internal.http.IHttpClient
import com.onesignal.debug.ILogListener
import com.onesignal.debug.LogLevel
import com.onesignal.debug.OneSignalLogEvent
import com.onesignal.debug.internal.logging.Logging
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk

/**
 * Verifies that [FeatureFlagsBackendService] surfaces enough diagnostic information for a
 * developer to understand why a remote feature-flags fetch returned
 * [RemoteFeatureFlagsFetchOutcome.Unavailable]. In particular:
 * - 4xx failures (e.g. 403 Forbidden for apps not enrolled in Turbine) should log at [LogLevel.WARN]
 *   and include a body snippet so the Turbine error payload shows up in logcat by default.
 * - 5xx / transient failures should stay at [LogLevel.DEBUG] to avoid noisy logs for flaky networks.
 * - A 200 with an unexpected body shape should log at [LogLevel.WARN] with a body snippet since it
 *   indicates a real contract/config issue that would otherwise be silent.
 */
class FeatureFlagsBackendServiceTests : FunSpec({
    lateinit var capturedLogs: MutableList<OneSignalLogEvent>
    lateinit var listener: ILogListener
    var originalLogLevel: LogLevel = LogLevel.WARN

    beforeEach {
        originalLogLevel = Logging.logLevel
        Logging.logLevel = LogLevel.NONE
        capturedLogs = mutableListOf()
        listener = ILogListener { event -> capturedLogs.add(event) }
        Logging.addListener(listener)
    }

    afterEach {
        Logging.removeListener(listener)
        Logging.logLevel = originalLogLevel
    }

    fun logsForService(): List<OneSignalLogEvent> =
        capturedLogs.filter { it.entry.contains("FeatureFlagsBackendService:") }

    test("403 Forbidden logs at WARN with body snippet and returns Unavailable") {
        val http = mockk<IHttpClient>()
        coEvery { http.get(any(), any()) } returns HttpResponse(403, """{"errors":["Forbidden"]}""")

        val outcome = FeatureFlagsBackendService(http).fetchRemoteFeatureFlags("appId")

        outcome shouldBe RemoteFeatureFlagsFetchOutcome.Unavailable
        val warns = logsForService().filter { it.level == LogLevel.WARN }
        warns shouldHaveSize 1
        warns[0].entry.contains("status=403") shouldBe true
        warns[0].entry.contains("""{"errors":["Forbidden"]}""") shouldBe true
    }

    test("404 Not Found also logs at WARN with body snippet") {
        val http = mockk<IHttpClient>()
        coEvery { http.get(any(), any()) } returns HttpResponse(404, """{"errors":["Not Found"]}""")

        FeatureFlagsBackendService(http).fetchRemoteFeatureFlags("appId") shouldBe
            RemoteFeatureFlagsFetchOutcome.Unavailable
        val warns = logsForService().filter { it.level == LogLevel.WARN }
        warns shouldHaveSize 1
        warns[0].entry.contains("status=404") shouldBe true
    }

    test("500 server error stays at DEBUG to avoid noisy logs for transient failures") {
        val http = mockk<IHttpClient>()
        coEvery { http.get(any(), any()) } returns HttpResponse(500, "boom")

        FeatureFlagsBackendService(http).fetchRemoteFeatureFlags("appId") shouldBe
            RemoteFeatureFlagsFetchOutcome.Unavailable
        logsForService().any { it.level == LogLevel.WARN } shouldBe false
        val debugs = logsForService().filter { it.level == LogLevel.DEBUG }
        debugs.any { it.entry.contains("status=500") && it.entry.contains("body=boom") } shouldBe true
    }

    test("network error (statusCode=0) stays at DEBUG") {
        val http = mockk<IHttpClient>()
        coEvery { http.get(any(), any()) } returns HttpResponse(0, null)

        FeatureFlagsBackendService(http).fetchRemoteFeatureFlags("appId") shouldBe
            RemoteFeatureFlagsFetchOutcome.Unavailable
        logsForService().any { it.level == LogLevel.WARN } shouldBe false
        val debugs = logsForService().filter { it.level == LogLevel.DEBUG }
        debugs.any { it.entry.contains("status=0") && it.entry.contains("body=<empty>") } shouldBe true
    }

    test("200 with non-contract JSON body logs at WARN with body snippet") {
        val http = mockk<IHttpClient>()
        coEvery { http.get(any(), any()) } returns HttpResponse(200, """{"errors":["Forbidden"]}""")

        FeatureFlagsBackendService(http).fetchRemoteFeatureFlags("appId") shouldBe
            RemoteFeatureFlagsFetchOutcome.Unavailable
        val warns =
            logsForService().filter {
                it.level == LogLevel.WARN && it.entry.contains("not valid Turbine feature-flags JSON")
            }
        warns shouldHaveSize 1
        warns[0].entry.contains("""{"errors":["Forbidden"]}""") shouldBe true
    }

    test("body snippet caps long payloads at 200 chars") {
        val longBody = "x".repeat(1_000)
        val http = mockk<IHttpClient>()
        coEvery { http.get(any(), any()) } returns HttpResponse(403, longBody)

        FeatureFlagsBackendService(http).fetchRemoteFeatureFlags("appId")
        val warn = logsForService().first { it.level == LogLevel.WARN }
        // Snippet is truncated to 200 chars plus an ellipsis marker.
        warn.entry.contains("x".repeat(200) + "…") shouldBe true
        warn.entry.contains("x".repeat(201)) shouldBe false
    }

    test("newlines in body are flattened so log entry stays single-line") {
        val http = mockk<IHttpClient>()
        coEvery { http.get(any(), any()) } returns HttpResponse(403, "line1\nline2\rline3")

        FeatureFlagsBackendService(http).fetchRemoteFeatureFlags("appId")
        val warn = logsForService().first { it.level == LogLevel.WARN }
        warn.entry.contains("body=line1 line2 line3") shouldBe true
        warn.entry.contains("\n") shouldBe false
        warn.entry.contains("\r") shouldBe false
    }

    test("200 with valid empty features array is Success and does not log at WARN") {
        val http = mockk<IHttpClient>()
        coEvery { http.get(any(), any()) } returns HttpResponse(200, """{"features":[]}""")

        val outcome = FeatureFlagsBackendService(http).fetchRemoteFeatureFlags("appId")
        outcome.shouldBeSuccessWithEmptyKeys()
        logsForService().any { it.level == LogLevel.WARN } shouldBe false
    }

    test("200 with empty body logs at WARN for contract anomaly and returns Unavailable") {
        val http = mockk<IHttpClient>()
        coEvery { http.get(any(), any()) } returns HttpResponse(200, null)

        val outcome = FeatureFlagsBackendService(http).fetchRemoteFeatureFlags("appId")
        outcome shouldBe RemoteFeatureFlagsFetchOutcome.Unavailable
        val warns = logsForService().filter { it.level == LogLevel.WARN }
        warns.any { it.entry.contains("empty body for success status=200") } shouldBe true
    }

    test("buildFeatureFlagsGetPath matches Turbine /apps/:app_id/sdk/features/:platform/:sdk_version") {
        FeatureFlagsBackendService.buildFeatureFlagsGetPath(
            appId = "14719551-23f1-4d20-8dab-81496ffca5ea",
            platform = FeatureFlagsBackendService.TURBINE_FEATURES_PLATFORM_ANDROID,
            sdkVersion = "050801",
        ) shouldBe "apps/14719551-23f1-4d20-8dab-81496ffca5ea/sdk/features/android/050801"
    }

    test("buildFeatureFlagsGetPath encodes prerelease sdk version label") {
        FeatureFlagsBackendService.buildFeatureFlagsGetPath(
            appId = "14719551-23f1-4d20-8dab-81496ffca5ea",
            platform = FeatureFlagsBackendService.TURBINE_FEATURES_PLATFORM_ANDROID,
            sdkVersion = "050801-beta",
        ) shouldBe "apps/14719551-23f1-4d20-8dab-81496ffca5ea/sdk/features/android/050801-beta"
    }

    test("isValidFeaturesSdkVersionLabel accepts only 6-digit labels with optional -suffix") {
        val valid =
            listOf(
                "050801",
                "050801-beta",
                "050801-beta1",
                "010203-rc.2",
                "000000",
            )
        val invalid =
            listOf(
                "5.8.1",
                "05080",
                "0508010",
                "",
                "050801-",
                "050801/",
                "v050801",
            )
        valid.forEach { FeatureFlagsBackendService.isValidFeaturesSdkVersionLabel(it) shouldBe true }
        invalid.forEach { FeatureFlagsBackendService.isValidFeaturesSdkVersionLabel(it) shouldBe false }
    }

    test("OneSignalUtils.sdkVersion from BuildConfig matches Turbine label rules") {
        FeatureFlagsBackendService.isValidFeaturesSdkVersionLabel(OneSignalUtils.sdkVersion) shouldBe true
    }
})

private fun RemoteFeatureFlagsFetchOutcome.shouldBeSuccessWithEmptyKeys() {
    check(this is RemoteFeatureFlagsFetchOutcome.Success) {
        "expected Success, got $this"
    }
    this.result.enabledKeys shouldBe emptyList()
}
