package com.onesignal

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class OneSignalResultTests : FunSpec({

    test("success carries data and no error") {
        val result = OneSignalResult.success(LoginData("os-1", "ext-1"))

        result.isSuccess.shouldBeTrue()
        result.error.shouldBeNull()
        result.data.shouldNotBeNull()
        result.data!!.onesignalId shouldBe "os-1"
        result.getOrNull().shouldNotBeNull()
        result.getOrThrow().externalId shouldBe "ext-1"
    }

    test("failure carries error and no data") {
        val result = OneSignalResult.failure<LoginData>(ErrorCode.INVALID_ARGUMENT, "no app ID")

        result.isSuccess.shouldBeFalse()
        result.data.shouldBeNull()
        result.getOrNull().shouldBeNull()
        result.error.shouldNotBeNull()
        result.error!!.first.code shouldBe ErrorCode.INVALID_ARGUMENT
        result.error!!.first.message shouldBe "no app ID"
    }

    test("a client code is distinguishable from a backend one without inspecting the message") {
        val client = OneSignalResult.failure<LoginData>(ErrorCode.STORAGE_LOCKED, "device locked")
        val backend = OneSignalResult.failure<LoginData>(ErrorCode.BACKEND_ERROR, "Invalid API Key", backendCode = 100)

        client.error!!.first.code.source shouldBe ErrorSource.CLIENT
        client.error!!.first.backendCode.shouldBeNull()

        backend.error!!.first.code.source shouldBe ErrorSource.BACKEND
        backend.error!!.first.backendCode shouldBe 100
    }

    test("an error can carry several reasons at once") {
        val error =
            OneSignalError(
                listOf(
                    OneSignalError.Detail(ErrorCode.BACKEND_ERROR, 100, "Invalid API Key"),
                    OneSignalError.Detail(ErrorCode.BACKEND_ERROR, 144, "Invalid external ID"),
                ),
            )

        error.error.size shouldBe 2
        error.first.backendCode shouldBe 100
        error.toList().map { it["backendCode"] } shouldBe listOf(100, 144)
    }

    // first is documented as always safe to read, so the constructor has to refuse the one input
    // that would make it throw.
    test("an error cannot be built with no reasons") {
        shouldThrow<IllegalArgumentException> { OneSignalError(emptyList()) }
    }

    test("a reason with no message keeps the exception text free of a bare null") {
        val result = OneSignalResult.failure<LogoutData>(ErrorCode.STORAGE_LOCKED)

        val thrown =
            runCatching { result.getOrThrow() }
                .exceptionOrNull()
                .shouldBeInstanceOf<OneSignalException>()

        thrown.message shouldBe "STORAGE_LOCKED"
    }

    test("getOrThrow surfaces the error and keeps the cause attached") {
        val boom = IllegalStateException("boom")
        val result = OneSignalResult.failure<LogoutData>(ErrorCode.UNKNOWN, "offline", cause = boom)

        val thrown =
            runCatching { result.getOrThrow() }
                .exceptionOrNull()
                .shouldBeInstanceOf<OneSignalException>()

        thrown.error.first.code shouldBe ErrorCode.UNKNOWN
        thrown.message shouldBe "UNKNOWN: offline"
        thrown.cause shouldBe boom
    }

    test("the cause stays off the wire so every SDK serializes the same shape") {
        val error = OneSignalError.of(ErrorCode.UNKNOWN, "boom", cause = IllegalStateException("boom"))

        error.cause.shouldNotBeNull()
        error.toList().single().keys shouldBe setOf("code", "source", "backendCode", "message")
    }

    test("success projects onto the wire envelope") {
        val map = OneSignalResult.success(LoginData("os-1", "ext-1")).toMap()

        map shouldBe
            mapOf(
                "success" to true,
                "data" to mapOf("onesignalId" to "os-1", "externalId" to "ext-1"),
                "error" to null,
            )
    }

    test("failure projects onto the wire envelope") {
        val map = OneSignalResult.failure<LoginData>(ErrorCode.STORAGE_LOCKED, "device locked").toMap()

        map shouldBe
            mapOf(
                "success" to false,
                "data" to null,
                "error" to
                    listOf(
                        mapOf(
                            "code" to "STORAGE_LOCKED",
                            "source" to "CLIENT",
                            "backendCode" to null,
                            "message" to "device locked",
                        ),
                    ),
            )
    }

    test("an empty payload still serializes as a present, empty data object") {
        val map = OneSignalResult.success(InitData()).toMap()

        map["success"] shouldBe true
        map["error"].shouldBeNull()
        map.containsKey("data").shouldBeTrue()
        map["data"] shouldBe emptyMap<String, Any?>()
    }

    test("success round-trips through the wire shape") {
        val original = OneSignalResult.success(LoginData("os-1", "ext-1"))

        val restored = OneSignalResult.fromMap(original.toMap(), LoginData::fromMap)

        restored.isSuccess.shouldBeTrue()
        restored.toMap() shouldBe original.toMap()
    }

    test("failure round-trips through the wire shape") {
        val original = OneSignalResult.failure<LoginData>(ErrorCode.BACKEND_ERROR, "already linked", backendCode = 409)

        val restored = OneSignalResult.fromMap(original.toMap(), LoginData::fromMap)

        restored.isSuccess.shouldBeFalse()
        restored.toMap() shouldBe original.toMap()
    }

    test("an empty payload round-trips through the wire shape") {
        val original = OneSignalResult.success(InitData())

        val restored = OneSignalResult.fromMap(original.toMap(), InitData::fromMap)

        restored.isSuccess.shouldBeTrue()
        restored.toMap() shouldBe original.toMap()
    }

    test("unknown envelope and payload fields are ignored rather than throwing") {
        val fromNewerProducer =
            mapOf(
                "success" to true,
                "data" to
                    mapOf(
                        "onesignalId" to "os-1",
                        "externalId" to "ext-1",
                        "subscriptionId" to "sub-9",
                    ),
                "error" to null,
                "traceId" to "abc-123",
            )

        val restored = OneSignalResult.fromMap(fromNewerProducer, LoginData::fromMap)

        restored.isSuccess.shouldBeTrue()
        restored.data!!.onesignalId shouldBe "os-1"
        restored.toMap() shouldBe
            mapOf(
                "success" to true,
                "data" to mapOf("onesignalId" to "os-1", "externalId" to "ext-1"),
                "error" to null,
            )
    }

    // The enum is closed, so a wrapper running against a newer producer will eventually meet a
    // code it cannot name. It has to degrade rather than throw out of valueOf.
    test("an unrecognized code degrades to UNKNOWN with the message preserved") {
        val fromNewerProducer =
            mapOf(
                "success" to false,
                "data" to null,
                "error" to listOf(mapOf("code" to "RATE_LIMITED", "message" to "slow down", "retryAfterSeconds" to 30)),
            )

        val restored = OneSignalResult.fromMap(fromNewerProducer, LoginData::fromMap)

        restored.isSuccess.shouldBeFalse()
        restored.error!!.first.code shouldBe ErrorCode.UNKNOWN
        restored.error!!.first.message shouldBe "slow down"
    }

    test("a malformed error missing its code degrades to unknown instead of throwing") {
        val restored =
            OneSignalResult.fromMap(
                mapOf("success" to false, "data" to null, "error" to listOf(mapOf("message" to "something broke"))),
                LoginData::fromMap,
            )

        restored.isSuccess.shouldBeFalse()
        restored.error!!.first.code shouldBe ErrorCode.UNKNOWN
        restored.error!!.first.message shouldBe "something broke"
    }

    // first is documented as always safe to read, so an error list that arrives empty still has
    // to produce one reason rather than blowing up at the call site.
    test("an empty reason list still yields a readable error") {
        val restored =
            OneSignalResult.fromMap(
                mapOf("success" to false, "data" to null, "error" to emptyList<Map<String, Any?>>()),
                LoginData::fromMap,
            )

        restored.isSuccess.shouldBeFalse()
        restored.error!!.first.code shouldBe ErrorCode.UNKNOWN
    }

    test("error presence wins over a contradictory success flag") {
        val contradictory =
            mapOf(
                "success" to true,
                "data" to mapOf("onesignalId" to "os-1", "externalId" to "ext-1"),
                "error" to listOf(mapOf("code" to "STORAGE_LOCKED", "message" to "device locked")),
            )

        val restored = OneSignalResult.fromMap(contradictory, LoginData::fromMap)

        restored.isSuccess.shouldBeFalse()
        restored.data.shouldBeNull()
        restored.error!!.first.code shouldBe ErrorCode.STORAGE_LOCKED
    }
})
