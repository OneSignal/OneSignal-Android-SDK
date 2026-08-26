package com.onesignal.core.internal.gesture

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.core.internal.application.IApplicationLifecycleHandler
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.mocks.IOMockHelper
import com.onesignal.mocks.IOMockHelper.awaitIO
import com.onesignal.mocks.MockHelper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.robolectric.annotation.Config

private const val SUBSCRIPTION_ID = "aaaabbbb-cccc-dddd-eeee-ffff00001111"

/**
 * Drives the detector through synthetic focus/unfocus sequences with a controlled clock and
 * reads back the real (Robolectric) clipboard. Dwells are in milliseconds; the default cycle
 * takes 2s, so six of them sit well inside the 30s window.
 */
private class Harness(
    subscriptionId: String? = SUBSCRIPTION_ID,
    remoteFlags: List<String> = emptyList(),
    consentRequired: Boolean? = null,
    consentGiven: Boolean? = null,
    fireOnSubscribe: Boolean = false,
) {
    var nowMs = 100_000L

    val context: Context = ApplicationProvider.getApplicationContext()
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private val handlerSlot = slot<IApplicationLifecycleHandler>()
    val detector: DeviceGestureDetector

    init {
        val applicationService = mockk<IApplicationService>()
        every { applicationService.appContext } returns context
        every { applicationService.addApplicationLifecycleHandler(capture(handlerSlot)) } answers {
            // Mirrors ApplicationService.addApplicationLifecycleHandler when the app is
            // already foregrounded at subscribe time.
            if (fireOnSubscribe) {
                handlerSlot.captured.onFocus(true)
            }
        }
        val configModelStore =
            MockHelper.configModelStore {
                it.pushSubscriptionId = subscriptionId
                it.sdkRemoteFeatureFlags = remoteFlags
                it.consentRequired = consentRequired
                it.consentGiven = consentGiven
            }
        detector = DeviceGestureDetector(applicationService, configModelStore)
        detector.monotonicMillis = { nowMs }
        detector.start()
    }

    val handler: IApplicationLifecycleHandler get() = handlerSlot.captured

    /** One foreground-dwell + background-dwell cycle. */
    fun cycle(
        backgroundDwellMs: Long = 1_000L,
        foregroundDwellMs: Long = 1_000L,
    ) {
        nowMs += foregroundDwellMs
        handler.onUnfocused()
        nowMs += backgroundDwellMs
        handler.onFocus(false)
    }

    fun clipText(): String? = clipboard.primaryClip?.getItemAt(0)?.text?.toString()

    val expectedClip: String get() = DeviceGestureDetector.clipText(SUBSCRIPTION_ID)
}

@RobolectricTest
@Config(sdk = [Build.VERSION_CODES.O])
class DeviceGestureDetectorTests : FunSpec({
    listener(IOMockHelper)

    test("six rapid cycles copy the prefixed subscription ID to the clipboard") {
        val harness = Harness()

        repeat(6) { harness.cycle() }
        awaitIO()

        harness.clipText() shouldBe "os: $SUBSCRIPTION_ID"
        harness.clipboard.primaryClip!!.description.label shouldBe "OneSignal subscription ID"
    }

    test("five cycles copy nothing") {
        val harness = Harness()

        repeat(5) { harness.cycle() }
        awaitIO()

        harness.clipText() shouldBe null
    }

    test("cycles slower than the window never accumulate six") {
        val harness = Harness()

        // 7 seconds per round trip caps the window at five cycles, so a user who
        // backgrounds the app all day at a normal pace can never fire this.
        repeat(8) { harness.cycle(backgroundDwellMs = 3_000L, foregroundDwellMs = 4_000L) }
        awaitIO()

        harness.clipText() shouldBe null
    }

    test("a pause mid-gesture does not reset progress") {
        val harness = Harness()

        repeat(3) { harness.cycle() }
        // A pause costs time, not accumulated cycles; all six still land inside the window.
        harness.cycle(foregroundDwellMs = 10_000L)
        repeat(2) { harness.cycle() }
        awaitIO()

        harness.clipText() shouldBe harness.expectedClip
    }

    test("a sub-human background blip does not count as a cycle") {
        val harness = Harness()

        repeat(5) { harness.cycle() }
        // Rotation with configChanges produces a synthetic pair this fast. It does not
        // count, so one more real cycle completes the gesture.
        harness.cycle(backgroundDwellMs = 1L)
        awaitIO()
        harness.clipText() shouldBe null

        harness.cycle()
        awaitIO()
        harness.clipText() shouldBe harness.expectedClip
    }

    test("the detector re-arms after firing") {
        val harness = Harness()

        repeat(6) { harness.cycle() }
        awaitIO()
        harness.clipText() shouldBe harness.expectedClip

        harness.clipboard.setPrimaryClip(ClipData.newPlainText("other", "sentinel"))
        repeat(6) { harness.cycle() }
        awaitIO()
        harness.clipText() shouldBe harness.expectedClip
    }

    test("the remote kill switch suppresses the copy") {
        // Server casing is preserved in the stored list, so match case-insensitively.
        val harness = Harness(remoteFlags = listOf("SDK_Device_Gesture_Disabled"))

        repeat(6) { harness.cycle() }
        awaitIO()

        harness.clipText() shouldBe null
    }

    test("withheld privacy consent suppresses the copy") {
        val harness = Harness(consentRequired = true, consentGiven = null)

        repeat(6) { harness.cycle() }
        awaitIO()

        harness.clipText() shouldBe null
    }

    test("granted privacy consent allows the copy") {
        val harness = Harness(consentRequired = true, consentGiven = true)

        repeat(6) { harness.cycle() }
        awaitIO()

        harness.clipText() shouldBe harness.expectedClip
    }

    test("a missing push subscription copies nothing") {
        val harness = Harness(subscriptionId = null)

        repeat(6) { harness.cycle() }
        awaitIO()

        harness.clipText() shouldBe null
    }

    test("a local not-yet-synced push subscription ID copies nothing") {
        val harness = Harness(subscriptionId = "local-$SUBSCRIPTION_ID")

        repeat(6) { harness.cycle() }
        awaitIO()

        harness.clipText() shouldBe null
    }

    test("the subscribe-time focus replay does not count as a cycle") {
        val harness = Harness(fireOnSubscribe = true)

        repeat(5) { harness.cycle() }
        awaitIO()
        harness.clipText() shouldBe null

        harness.cycle()
        awaitIO()
        harness.clipText() shouldBe harness.expectedClip
    }

    test("a focus without a preceding background does not count as a cycle") {
        val harness = Harness()

        // Cold start: the app comes to the foreground with no background phase to pair with.
        harness.handler.onFocus(false)
        repeat(5) { harness.cycle() }
        awaitIO()
        harness.clipText() shouldBe null

        harness.cycle()
        awaitIO()
        harness.clipText() shouldBe harness.expectedClip
    }
})
