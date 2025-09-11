package com.onesignal.common

import com.onesignal.core.BuildConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class OneSignalUtilsTest : FunSpec({

    context("formatVersion standard cases") {
        test("should format '5.2.1' correctly") {
            OneSignalUtils.formatVersion("5.2.1") shouldBe "050201"
        }

        test("should format '05.02.01' correctly") {
            OneSignalUtils.formatVersion("05.02.01") shouldBe "050201"
        }

        test("should format '4.10.0' correctly") {
            OneSignalUtils.formatVersion("4.10.0") shouldBe "041000"
        }

        test("should format '12.1.3' correctly") {
            OneSignalUtils.formatVersion("12.1.3") shouldBe "120103"
        }

        test("should format '1.2.3' correctly") { // This was also a separate test later, combined here
            OneSignalUtils.formatVersion("1.2.3") shouldBe "010203"
        }

        test("should format '10.20.30' correctly") { // This was also a separate test later, combined here
            OneSignalUtils.formatVersion("10.20.30") shouldBe "102030"
        }
    }

    context("formatVersion edge cases and malformed inputs") {
        test("should format major only '5' correctly") {
            OneSignalUtils.formatVersion("5") shouldBe "050000"
        }

        test("should format major with trailing dot '5.' correctly") {
            OneSignalUtils.formatVersion("5.") shouldBe "050000"
        }

        test("should format major and minor '5.2' correctly") {
            OneSignalUtils.formatVersion("5.2") shouldBe "050200"
        }

        test("should format major and minor with trailing dot '5.2.' correctly") {
            OneSignalUtils.formatVersion("5.2.") shouldBe "050200"
        }

        test("should format empty string correctly") {
            OneSignalUtils.formatVersion("") shouldBe "000000"
        }

        test("should format single dot '.' correctly") {
            OneSignalUtils.formatVersion(".") shouldBe "000000"
        }

        test("should format multiple dots '..' correctly") {
            OneSignalUtils.formatVersion("..") shouldBe "000000"
        }

        test("should format non-numeric 'a.b.c' correctly") {
            OneSignalUtils.formatVersion("a.b.c") shouldBe "0a0b0c"
        }

        test("should format version with extra segments '1.2.3.4' correctly") {
            OneSignalUtils.formatVersion("1.2.3.4") shouldBe "010203"
        }

        test("should format version with large components '100.200.300' correctly") {
            OneSignalUtils.formatVersion("100.200.300") shouldBe "100200300"
        }

        test("should format '0.0.0' as '000000'") {
            OneSignalUtils.formatVersion("0.0.0") shouldBe "000000"
        }

        test("should format '0.0.1' as '1'") { // "000001" then trimmed
            OneSignalUtils.formatVersion("0.0.1") shouldBe "000001"
        }
    }

    context("formatVersion trims leading zeros correctly") {
        test("should format '05.02.01' (no leading zero in result) as '050201'") {
            OneSignalUtils.formatVersion("05.02.01") shouldBe "050201"
        }

        test("should format '0.2.1' (becomes '000201') and trim to '0201'") {
            OneSignalUtils.formatVersion("0.2.1") shouldBe "000201"
        }

        // test for "0.0.0" is already covered in edge cases
        // test for "0.0.1" is already covered in edge cases
    }

    test("should format '5.2.1' with patch") {
        OneSignalUtils.formatVersion("5.2.1") shouldBe "050201"
    }

    test("should format '5.2' without patch") {
        OneSignalUtils.formatVersion("5.2") shouldBe "050200"
    }

    test("should format '5.2.0-beta-06' with patch and suffix") {
        OneSignalUtils.formatVersion("5.2.0-beta-06") shouldBe "050200-beta-06"
    }

    test("should format '5.2-beta' without patch, with suffix") {
        OneSignalUtils.formatVersion("5.2-beta") shouldBe "050200-beta"
    }

    test("should format '5' without patch or minor") {
        OneSignalUtils.formatVersion("5") shouldBe "050000"
    }

    test("sdkVersion should return the formatted version from BuildConfig.SDK_VERSION") {
        // Calculate expected based on the real BuildConfig.SDK_VERSION
        val expectedDirectlyFromFormatVersion = OneSignalUtils.formatVersion(BuildConfig.SDK_VERSION)
        OneSignalUtils.sdkVersion shouldBe expectedDirectlyFromFormatVersion
    }
})
