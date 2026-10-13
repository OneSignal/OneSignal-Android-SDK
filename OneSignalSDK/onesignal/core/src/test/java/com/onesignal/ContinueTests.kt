package com.onesignal

import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.util.function.Consumer

class ContinueTests : FunSpec({

    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("the default context falls back to Dispatchers.Default when Main is unavailable") {
        val continuation = Continue.with(Consumer<ContinueResult<Unit>> { })

        continuation.context shouldBe Dispatchers.Default
        Continue.none<Unit>().context shouldBe Dispatchers.Default
    }

    test("the default context is Main when Main is available") {
        @OptIn(ExperimentalCoroutinesApi::class)
        val main = UnconfinedTestDispatcher()
        Dispatchers.setMain(main)
        try {
            val continuation = Continue.with(Consumer<ContinueResult<Unit>> { })

            continuation.context shouldBe Dispatchers.Main
        } finally {
            Dispatchers.resetMain()
        }
    }

    test("an explicit context is used as given") {
        val continuation = Continue.with(Consumer<ContinueResult<Unit>> { }, Dispatchers.Unconfined)

        continuation.context shouldBe Dispatchers.Unconfined
    }

    test("the result carries the coroutine's failure") {
        var seen: ContinueResult<String>? = null
        val continuation = Continue.with(Consumer<ContinueResult<String>> { seen = it }, Dispatchers.Unconfined)
        val failure = IllegalStateException("boom")

        continuation.resumeWith(Result.failure(failure))

        seen!!.isSuccess shouldBe false
        seen!!.data shouldBe null
        seen!!.throwable shouldBe failure
    }

    test("the result carries the coroutine's value") {
        var seen: ContinueResult<String>? = null
        val continuation = Continue.with(Consumer<ContinueResult<String>> { seen = it }, Dispatchers.Unconfined)

        continuation.resumeWith(Result.success("ok"))

        seen!!.isSuccess shouldBe true
        seen!!.data shouldBe "ok"
        seen!!.throwable shouldBe null
    }
})
