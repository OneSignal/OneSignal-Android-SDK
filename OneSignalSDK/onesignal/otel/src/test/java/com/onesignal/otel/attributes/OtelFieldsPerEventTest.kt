package com.onesignal.otel.attributes

import com.onesignal.otel.IOtelPlatformProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk

class OtelFieldsPerEventTest : FunSpec({
    val mockPlatformProvider = mockk<IOtelPlatformProvider>(relaxed = true)
    val fields = OtelFieldsPerEvent(mockPlatformProvider)

    test("getAttributes should include all per-event fields") {
        every { mockPlatformProvider.appId } returns "test-app-id"
        every { mockPlatformProvider.onesignalId } returns "test-onesignal-id"
        every { mockPlatformProvider.pushSubscriptionId } returns "test-subscription-id"
        every { mockPlatformProvider.appState } returns "foreground"
        every { mockPlatformProvider.processUptime } returns 100.5
        every { mockPlatformProvider.currentThreadName } returns "main-thread"

        val attributes = fields.getAttributes()

        attributes.keys shouldContain "log.record.uid"
        attributes["log.record.uid"] shouldNotBe null
        attributes["ossdk.app_id"] shouldBe "test-app-id"
        attributes["ossdk.onesignal_id"] shouldBe "test-onesignal-id"
        attributes["ossdk.push_subscription_id"] shouldBe "test-subscription-id"
        attributes["android.app.state"] shouldBe "foreground"
        attributes["process.uptime"] shouldBe "100.5"
        attributes["thread.name"] shouldBe "main-thread"
    }

    test("getAttributes should exclude null optional fields") {
        every { mockPlatformProvider.appId } returns null
        every { mockPlatformProvider.onesignalId } returns null
        every { mockPlatformProvider.pushSubscriptionId } returns null
        every { mockPlatformProvider.appState } returns "background"
        every { mockPlatformProvider.processUptime } returns 50.0
        every { mockPlatformProvider.currentThreadName } returns "worker-thread"

        val attributes = fields.getAttributes()

        attributes.keys shouldNotContain "ossdk.app_id"
        attributes.keys shouldNotContain "ossdk.onesignal_id"
        attributes.keys shouldNotContain "ossdk.push_subscription_id"
        attributes["android.app.state"] shouldBe "background"
        attributes["process.uptime"] shouldBe "50.0"
        attributes["thread.name"] shouldBe "worker-thread"
    }

    test("getAttributes should generate unique record IDs") {
        every { mockPlatformProvider.appId } returns "test-app-id"
        every { mockPlatformProvider.onesignalId } returns null
        every { mockPlatformProvider.pushSubscriptionId } returns null
        every { mockPlatformProvider.appState } returns "foreground"
        every { mockPlatformProvider.processUptime } returns 100.0
        every { mockPlatformProvider.currentThreadName } returns "main"

        val attributes1 = fields.getAttributes()
        val attributes2 = fields.getAttributes()

        attributes1["log.record.uid"] shouldNotBe attributes2["log.record.uid"]
    }
})
