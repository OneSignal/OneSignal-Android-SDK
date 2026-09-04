package com.onesignal.core.internal.gesture

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.core.internal.application.IApplicationLifecycleHandler
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.features.IFeatureManager
import com.onesignal.features.FeatureFlag
import com.onesignal.logger.ILogTelemetry
import com.onesignal.logger.IObservabilityEventRecorder
import com.onesignal.logger.ObservabilityEvent
import com.onesignal.mocks.IOMockHelper
import com.onesignal.mocks.IOMockHelper.awaitIO
import com.onesignal.mocks.MockHelper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.robolectric.annotation.Config

private const val SUBSCRIPTION_ID = "aaaabbbb-cccc-dddd-eeee-ffff00001111"

/**
 * Captures what the detector records so tests can assert the event and its attributes. The
 * attach/detach/reset side belongs to the logger lifecycle and never reaches the detector.
 */
private class RecorderSpy : IObservabilityEventRecorder {
    private val stored = mutableListOf<Pair<ObservabilityEvent, Map<String, String>>>()

    val recorded: List<Pair<ObservabilityEvent, Map<String, String>>>
        get() = synchronized(stored) { stored.toList() }

    override fun record(
        event: ObservabilityEvent,
        attributes: Map<String, String>,
    ) {
        synchronized(stored) { stored.add(event to attributes) }
    }

    override fun record(event: ObservabilityEvent) = record(event, emptyMap())

    override fun attach(telemetry: ILogTelemetry) = Unit

    override fun detach(telemetry: ILogTelemetry) = Unit

    override fun reset() = Unit
}

/**
 * Drives the detector through synthetic focus/unfocus sequences with a controlled clock and
 * reads back the real (Robolectric) clipboard. Dwells are in milliseconds; the default cycle
 * takes 2s, so six of them sit well inside the 30s window.
 */
private class Harness(
    subscriptionId: String? = SUBSCRIPTION_ID,
    killSwitchOn: Boolean = false,
    consentRequired: Boolean? = null,
    consentGiven: Boolean? = null,
    fireOnSubscribe: Boolean = false,
) {
    var nowMs = 100_000L

    val context: Context = ApplicationProvider.getApplicationContext()
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val recorder = RecorderSpy()

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
                it.consentRequired = consentRequired
                it.consentGiven = consentGiven
            }
        // Strict mock: only the kill switch flag is answered, so asking for anything else fails the test.
        val featureManager = mockk<IFeatureManager>()
        every { featureManager.isEnabled(FeatureFlag.SDK_DEVICE_GESTURE_DISABLED) } returns killSwitchOn
        detector = DeviceGestureDetector(applicationService, configModelStore, featureManager, recorder)
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
        val harness = Harness(killSwitchOn = true)

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

    test("a missing push subscription copies the placeholder") {
        // Someone following the docs gets a visible result that says why there is no ID.
        val harness = Harness(subscriptionId = null)

        repeat(6) { harness.cycle() }
        awaitIO()

        harness.clipText() shouldBe "os: no subscription ID yet"
    }

    test("a local not-yet-synced push subscription ID copies the placeholder") {
        val harness = Harness(subscriptionId = "local-$SUBSCRIPTION_ID")

        repeat(6) { harness.cycle() }
        awaitIO()

        harness.clipText() shouldBe "os: no subscription ID yet"
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

    // ===== Observability event =====
    // Every recognised gesture records DEVICE_GESTURE with its outcome, whether or not an ID was
    // copied, so the backend can answer how often the gesture happens and how often it pays off.

    test("a completed gesture records a copied event carrying the subscription ID") {
        val harness = Harness()

        // Progress is silent: the event fires on recognition, not per cycle.
        repeat(5) { harness.cycle() }
        awaitIO()
        harness.recorder.recorded.shouldBeEmpty()

        harness.cycle()
        awaitIO()

        harness.recorder.recorded shouldBe
            listOf(
                ObservabilityEvent.DEVICE_GESTURE to
                    mapOf(
                        "gesture.result" to "copied",
                        "gesture.push_subscription_id" to SUBSCRIPTION_ID,
                    ),
            )
    }

    test("the remote kill switch records a disabled result without an ID") {
        val harness = Harness(killSwitchOn = true)

        repeat(6) { harness.cycle() }
        awaitIO()

        harness.recorder.recorded shouldBe
            listOf(ObservabilityEvent.DEVICE_GESTURE to mapOf("gesture.result" to "disabled"))
    }

    test("a missing or local push subscription records a no_id result") {
        // Both shapes mean the same thing to the backend: the gesture ran before the device had
        // anything worth pasting.
        listOf(null, "local-$SUBSCRIPTION_ID").forEach { subscriptionId ->
            val harness = Harness(subscriptionId = subscriptionId)

            repeat(6) { harness.cycle() }
            awaitIO()

            harness.recorder.recorded shouldBe
                listOf(ObservabilityEvent.DEVICE_GESTURE to mapOf("gesture.result" to "no_id"))
        }
    }

    test("withheld privacy consent records nothing") {
        // The event would ship to the backend, and nothing may leave the device before consent.
        val harness = Harness(consentRequired = true, consentGiven = null)

        repeat(6) { harness.cycle() }
        awaitIO()

        harness.recorder.recorded.shouldBeEmpty()
    }

    test("each recognition records its own event") {
        val harness = Harness()

        repeat(12) { harness.cycle() }
        awaitIO()

        harness.recorder.recorded.map { it.first } shouldBe
            listOf(ObservabilityEvent.DEVICE_GESTURE, ObservabilityEvent.DEVICE_GESTURE)
        harness.recorder.recorded.map { it.second["gesture.result"] } shouldBe listOf("copied", "copied")
    }
})
