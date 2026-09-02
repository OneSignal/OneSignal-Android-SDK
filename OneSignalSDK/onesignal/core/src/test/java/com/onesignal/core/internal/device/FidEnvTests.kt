package com.onesignal.core.internal.device

import com.onesignal.core.internal.device.impl.FidEnvSnapshot
import com.onesignal.core.internal.device.impl.dashboardSenderForCensus
import com.onesignal.core.internal.device.impl.parseAgpVersion
import com.onesignal.core.internal.device.impl.sanitizeToken
import com.onesignal.core.internal.device.impl.senderMatch
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class FidEnvTests : FunSpec({
    test("header encodes known values in stable key order") {
        val header =
            FidEnvSnapshot(
                googleServices = true,
                agpVersion = "8.8.2",
                fidFlag = false,
                defaultFirebaseApp = true,
                firebaseInitProvider = true,
                minSdk = 24,
                targetSdk = 35,
                senderMatch = true,
            ).toHeaderValue()

        header shouldBe "gs=1;agp=8.8.2;flag=0;def=1;prov=1;min=24;tgt=35;snd=1"
    }

    test("header uses dashes for unknown optional fields") {
        val header =
            FidEnvSnapshot(
                googleServices = false,
                agpVersion = null,
                fidFlag = false,
                defaultFirebaseApp = false,
                firebaseInitProvider = false,
                minSdk = null,
                targetSdk = null,
                senderMatch = null,
            ).toHeaderValue()

        header shouldBe "gs=0;agp=-;flag=0;def=0;prov=0;min=-;tgt=-;snd=-"
    }

    test("sanitizeToken strips header-unsafe characters and caps length") {
        sanitizeToken("8.8.2-alpha01") shouldBe "8.8.2-alpha01"
        sanitizeToken("8.8.2 injected\nX-Other: 1") shouldBe "8.8.2injectedX-Other1"
        sanitizeToken("8.8.2\u00e9") shouldBe "8.8.2"
        sanitizeToken(" ".repeat(40)) shouldBe "-"
        sanitizeToken("a".repeat(40)).length shouldBe 32
    }

    test("parseAgpVersion reads androidGradlePluginVersion") {
        parseAgpVersion("androidGradlePluginVersion=8.8.2\n") shouldBe "8.8.2"
    }

    test("parseAgpVersion returns null when the key is missing") {
        parseAgpVersion("appMetadataVersion=1.1\n") shouldBe null
    }

    test("parseAgpVersion returns null for malformed properties") {
        parseAgpVersion("androidGradlePluginVersion=\\uXXXX") shouldBe null
    }

    test("senderMatch is unknown until dashboard sender is available") {
        senderMatch("123", null) shouldBe null
        senderMatch(null, null) shouldBe null
    }

    test("senderMatch is false when dashboard sender is known but the resource is missing") {
        senderMatch(null, "123") shouldBe false
        senderMatch("", "123") shouldBe false
    }

    test("senderMatch compares the google-services sender to the dashboard sender") {
        senderMatch("123", "123") shouldBe true
        senderMatch("123", "999") shouldBe false
    }

    test("dashboardSenderForCensus requires hydrated params for the current appId") {
        dashboardSenderForCensus(
            hydrated = false,
            appId = "app-a",
            senderAppId = "app-a",
            sender = "111",
        ) shouldBe null
        dashboardSenderForCensus(
            hydrated = true,
            appId = "app-b",
            senderAppId = "app-a",
            sender = "111",
        ) shouldBe null
        dashboardSenderForCensus(
            hydrated = true,
            appId = "app-a",
            senderAppId = "app-a",
            sender = "111",
        ) shouldBe "111"
    }
})
