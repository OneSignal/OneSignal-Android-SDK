package com.onesignal.common.threading

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

class InFlightResultTests : FunSpec({
    test("cancelling the owner while the lock is held does not stick the fallback") {
        val shared = InFlightResult<Boolean>()
        val gate =
            InFlightResult::class.java.getDeclaredField("gate").apply { isAccessible = true }.get(shared) as Mutex
        val started = CompletableDeferred<Unit>()
        val holding = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val scope = CoroutineScope(Dispatchers.Default)
        val owner =
            scope.launch {
                shared.share(false) {
                    started.complete(Unit)
                    suspendCancellableCoroutine<Unit> { }
                    true
                }
            }

        started.await()
        val holder =
            scope.launch {
                gate.lock()
                holding.complete(Unit)
                release.await()
                gate.unlock()
            }
        holding.await()
        owner.cancel()
        // A cancelled owner that gives up on the held lock finishes here. The fix waits.
        val abandoned = withTimeoutOrNull(1.seconds) { owner.join() }
        release.complete(Unit)
        holder.join()
        if (abandoned == null) {
            owner.join()
        }

        var ran = false
        val next =
            shared.share(false) {
                ran = true
                true
            }

        abandoned shouldBe null
        next shouldBe true
        ran shouldBe true
        scope.cancel()
    }
})
