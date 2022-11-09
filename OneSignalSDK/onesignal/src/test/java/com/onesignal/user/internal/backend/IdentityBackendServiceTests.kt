package com.onesignal.user.internal.backend

import com.onesignal.core.internal.http.HttpResponse
import com.onesignal.core.internal.http.IHttpClient
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.user.internal.backend.impl.IdentityBackendService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.runner.junit4.KotestTestRunner
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.runner.RunWith

@RunWith(KotestTestRunner::class)
class IdentityBackendServiceTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("create identity") {
        /* Given */
        val aliasLabel = "onesignal_id"
        val aliasValue = "11111111-1111-1111-1111-111111111111"
        val spyHttpClient = mockk<IHttpClient>()
        coEvery { spyHttpClient.post(any(), any()) } returns HttpResponse(200, "{ aliasKey1: \"aliasValue1\"}")
        val identityBackendService = IdentityBackendService(spyHttpClient)
        val identities = mapOf("aliasKey1" to "aliasValue1")

        /* When */
        val response = identityBackendService.createAlias("appId", aliasLabel, aliasValue, identities)

        /* Then */
        response["aliasKey1"] shouldBe "aliasValue1"
        coVerify {
            spyHttpClient.put(
                "apps/appId/user/by/$aliasLabel/$aliasValue/identity",
                withArg {
                    it.has("identity") shouldBe true
                    it.getJSONObject("identity").has("aliasKey1") shouldBe true
                    it.getJSONObject("identity").getString("aliasKey1") shouldBe "aliasValue1"
                }
            )
        }
    }

    test("delete identity") {
        /* Given */
        val aliasLabel = "onesignal_id"
        val aliasValue = "11111111-1111-1111-1111-111111111111"
        val aliasToDelete = "aliasKey1"
        val spyHttpClient = mockk<IHttpClient>()
        coEvery { spyHttpClient.post(any(), any()) } returns HttpResponse(200, "")
        val identityBackendService = IdentityBackendService(spyHttpClient)

        /* When */
        identityBackendService.deleteAlias("appId", aliasLabel, aliasValue, aliasToDelete)

        /* Then */
        coVerify {
            spyHttpClient.delete("apps/appId/user/by/$aliasLabel/$aliasValue/identity/$aliasToDelete")
        }
    }
})
