package com.onesignal

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class OneSignalErrorTests : FunSpec({

    test("fromBackendResponse parses catalog code, title, and conflicting_aliases") {
        val body =
            """
            {"errors":[{
              "code":"user-1",
              "title":"Multiple existing Users found with provided set of Aliases",
              "meta":{"conflicting_aliases":{"custom_alias_1":"jon-api-1","external_id":"jon-api-3"}}
            }]}
            """.trimIndent()

        val error = OneSignalError.fromBackendResponse(409, body)

        error.first.code shouldBe ErrorCode.BACKEND_ERROR
        error.first.backendCode shouldBe "user-1"
        error.first.httpStatus shouldBe 409
        error.first.message shouldBe "Multiple existing Users found with provided set of Aliases"
        error.first.meta shouldBe
            mapOf(
                "conflicting_aliases" to
                    mapOf(
                        "custom_alias_1" to "jon-api-1",
                        "external_id" to "jon-api-3",
                    ),
            )
    }

    test("fromBackendResponse maps each errors entry to its own reason") {
        val body =
            """
            {"errors":[
              {"code":"user-1","title":"alias conflict"},
              {"code":"user-3","title":"invalid external id"}
            ]}
            """.trimIndent()

        val error = OneSignalError.fromBackendResponse(409, body)

        error.error.map { it.backendCode } shouldBe listOf("user-1", "user-3")
        error.error.map { it.message } shouldBe listOf("alias conflict", "invalid external id")
        error.error.all { it.httpStatus == 409 } shouldBe true
    }

    test("fromBackendResponse keeps a plain body as the message") {
        val error = OneSignalError.fromBackendResponse(409, "CONFLICT", fallbackMessage = "Login did not complete.")

        error.first.backendCode.shouldBeNull()
        error.first.httpStatus shouldBe 409
        error.first.message shouldBe "CONFLICT"
        error.first.meta.shouldBeNull()
    }

    test("fromBackendResponse uses the fallback when the body is empty") {
        val error = OneSignalError.fromBackendResponse(400, "  ", fallbackMessage = "Login did not complete (HTTP 400).")

        error.first.httpStatus shouldBe 400
        error.first.message shouldBe "Login did not complete (HTTP 400)."
    }
})
