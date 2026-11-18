package com.onesignal.debug.internal.crash

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith

// Shape of a real traces.txt: a header, then one quoted block per thread.
private val DUMP =
    """
    ----- pid 12345 at 2026-11-18 10:00:00 -----
    Cmd line: com.example.app

    DALVIK THREADS (42):
    "main" prio=5 tid=1 Native
      | group="main" sCount=1 dsCount=0 flags=1 obj=0x12345678 self=0xabc
      | sysTid=12345 nice=-10 cgrp=default sched=0/0 handle=0xdef
      | held mutexes=
      at android.os.MessageQueue.nativePollOnce(Native method)
      at android.os.Looper.loopOnce(Looper.java:161)
      at com.onesignal.internal.OneSignalImp.waitForInit(OneSignalImp.kt:123)

    "Binder:12345_1" prio=5 tid=8 Native
      | group="main" sCount=1 dsCount=0 flags=1 obj=0x999 self=0x111
      at android.os.Binder.execTransact(Binder.java:1215)
    """.trimIndent()

class ProcessExitAnrTraceTest : FunSpec({

    test("reads only the main thread's frames") {
        val frames = ProcessExitAnrTrace.mainThreadFrames(DUMP)

        frames shouldBe
            listOf(
                "android.os.MessageQueue.nativePollOnce(Native method)",
                "android.os.Looper.loopOnce(Looper.java:161)",
                "com.onesignal.internal.OneSignalImp.waitForInit(OneSignalImp.kt:123)",
            )
    }

    test("stops at the next thread block rather than absorbing its frames") {
        val frames = ProcessExitAnrTrace.mainThreadFrames(DUMP)

        frames.none { it.contains("Binder") } shouldBe true
    }

    test("a dump whose main block is not first still resolves") {
        val reordered =
            """
            "Signal Catcher" prio=10 tid=2 Runnable
              at java.lang.Thread.run(Thread.java:1)
            "main" prio=5 tid=1 Native
              at com.onesignal.Foo.bar(Foo.kt:9)
            """.trimIndent()

        ProcessExitAnrTrace.mainThreadFrames(reordered) shouldBe listOf("com.onesignal.Foo.bar(Foo.kt:9)")
    }

    test("a dump with no main thread yields no frames rather than guessing") {
        val noMain =
            """
            "Binder:1_1" prio=5 tid=8 Native
              at android.os.Binder.execTransact(Binder.java:1215)
            """.trimIndent()

        ProcessExitAnrTrace.mainThreadFrames(noMain) shouldBe emptyList()
    }

    test("an empty or truncated dump yields no frames") {
        ProcessExitAnrTrace.mainThreadFrames("") shouldBe emptyList()
        ProcessExitAnrTrace.mainThreadFrames("----- pid 1 -----") shouldBe emptyList()
    }

    test("a thread named main-something is not mistaken for main") {
        val lookalike =
            """
            "main-worker" prio=5 tid=4 Native
              at com.example.Other.run(Other.kt:1)
            """.trimIndent()

        ProcessExitAnrTrace.mainThreadFrames(lookalike) shouldBe emptyList()
    }

    test("attribution is true only when a OneSignal frame is on the blocked thread") {
        ProcessExitAnrTrace.isOneSignalAtFault(ProcessExitAnrTrace.mainThreadFrames(DUMP)) shouldBe true
        ProcessExitAnrTrace.isOneSignalAtFault(listOf("android.os.Looper.loopOnce(Looper.java:161)")) shouldBe false
        ProcessExitAnrTrace.isOneSignalAtFault(emptyList()) shouldBe false
    }

    test("crash data keeps the live ANR exception type so both group together") {
        val data =
            ProcessExitAnrTrace.buildCrashData(
                frames = ProcessExitAnrTrace.mainThreadFrames(DUMP),
                appState = "foreground",
                occurredAtMs = 1_700_000_000_123,
            )

        data.exceptionType shouldBe ANR_EXCEPTION_TYPE
        data.threadName shouldBe "main"
        data.appState shouldBe "foreground"
        data.occurredAtMs shouldBe 1_700_000_000_123L
        data.attributed shouldBe true
    }

    test("the stacktrace matches the JVM format consumers already parse") {
        val data =
            ProcessExitAnrTrace.buildCrashData(
                frames = listOf("com.onesignal.Foo.bar(Foo.kt:9)"),
                appState = "unknown",
                occurredAtMs = 1,
            )

        data.stacktrace shouldStartWith "$ANR_EXCEPTION_TYPE: ${ProcessExitAnrTrace.POST_MORTEM_MESSAGE}"
        data.stacktrace shouldContain "\tat com.onesignal.Foo.bar(Foo.kt:9)"
    }

    test("an unattributed ANR is still built, flagged rather than dropped") {
        val data =
            ProcessExitAnrTrace.buildCrashData(
                frames = listOf("android.os.Looper.loopOnce(Looper.java:161)"),
                appState = "foreground",
                occurredAtMs = 1,
            )

        data.attributed shouldBe false
    }
})
