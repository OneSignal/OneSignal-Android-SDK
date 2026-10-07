package com.onesignal.common

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.Date
import java.util.TimeZone

class DateUtilsTest : FunSpec({
    test("iso8601Format parses Z timestamps as UTC outside the UTC timezone") {
        val originalTimeZone = TimeZone.getDefault()

        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))

            val parsedDate = DateUtils.iso8601Format().parse("2026-09-14T11:30:00.000Z")

            parsedDate shouldBe Date.from(Instant.parse("2026-09-14T11:30:00.000Z"))
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    test("iso8601Format formats dates as UTC outside the UTC timezone") {
        val originalTimeZone = TimeZone.getDefault()

        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))

            val formatted = DateUtils.iso8601Format().format(Date.from(Instant.parse("2026-09-14T11:30:00.000Z")))

            formatted shouldBe "2026-09-14T11:30:00.000Z"
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }
})
