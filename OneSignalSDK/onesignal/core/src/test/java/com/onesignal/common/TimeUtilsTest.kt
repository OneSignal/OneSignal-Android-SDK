package com.onesignal.common

import android.os.Build
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeEmpty
import io.kotest.matchers.string.shouldNotContain
import java.time.ZoneId
import java.util.TimeZone

@RobolectricTest
class TimeUtilsTest : FunSpec({

    test("getTimeZoneId returns correct time zone id") {
        // Given
        val expected =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ZoneId.systemDefault().id
            } else {
                TimeZone.getDefault().id
            }

        // When
        val actual = TimeUtils.getTimeZoneId()

        // Then
        actual shouldBe expected
        actual.shouldNotBeEmpty()
    }

    test("getTimeZoneId returns valid timezone format") {
        // When
        val timeZoneId = TimeUtils.getTimeZoneId()

        // Then
        timeZoneId.shouldNotBeEmpty()
        timeZoneId shouldNotBe ""

        // Valid timezone IDs follow IANA format patterns:
        // - Continental zones: "America/New_York", "Europe/London"
        // - UTC variants: "UTC", "GMT"
        // - Offset formats: "GMT+05:30", "UTC-08:00"
        // Should not contain spaces or invalid characters
        timeZoneId.shouldNotContain(" ")
    }
})
