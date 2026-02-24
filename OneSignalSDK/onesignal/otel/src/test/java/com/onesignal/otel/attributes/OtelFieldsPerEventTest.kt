package com.onesignal.otel.attributes

import com.onesignal.otel.IOtelPlatformProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk

class OtelFieldsPerEventTest : FunSpec({
    val mockPlatformProvider = mockk<IOtelPlatformProvider>(relaxed = true)
    val fields = OtelFieldsPerEvent(mockPlatformProvider)

    fun setupDefaultMocks(
        appId: String? = "test-app-id",
        onesignalId: String? = "test-onesignal-id",
        pushSubscriptionId: String? = "test-subscription-id",
        appState: String = "foreground",
        processUptime: Double = 100.5,
        threadName: String = "main-thread"
    ) {
        every { mockPlatformProvider.appId } returns appId
        every { mockPlatformProvider.onesignalId } returns onesignalId
        every { mockPlatformProvider.pushSubscriptionId } returns pushSubscriptionId
        every { mockPlatformProvider.appState } returns appState
        every { mockPlatformProvider.processUptime } returns processUptime
        every { mockPlatformProvider.currentThreadName } returns threadName
    }

    beforeEach { clearMocks(mockPlatformProvider) }

    test("getAttributes should include all per-event fields when all values present") {
        setupDefaultMocks()

        val attributes = fields.getAttributes()

        attributes.keys shouldContain "log.record.uid"
        attributes["log.record.uid"] shouldNotBe null
        attributes["ossdk.app_id"] shouldBe "test-app-id"
        attributes["ossdk.onesignal_id"] shouldBe "test-onesignal-id"
        attributes["ossdk.push_subscription_id"] shouldBe "test-subscription-id"
        attributes["app.state"] shouldBe "foreground"
        attributes["process.uptime"] shouldBe "100.5"
        attributes["thread.name"] shouldBe "main-thread"
    }

    test("getAttributes should exclude null optional fields") {
        setupDefaultMocks(appId = null, onesignalId = null, pushSubscriptionId = null, appState = "background")

        val attributes = fields.getAttributes()

        attributes.keys shouldNotContain "ossdk.app_id"
        attributes.keys shouldNotContain "ossdk.onesignal_id"
        attributes.keys shouldNotContain "ossdk.push_subscription_id"
        attributes["app.state"] shouldBe "background"
    }

    test("getAttributes should generate unique record IDs on each call") {
        setupDefaultMocks()

        val uid1 = fields.getAttributes()["log.record.uid"]
        val uid2 = fields.getAttributes()["log.record.uid"]

        uid1 shouldNotBe uid2
    }
})
