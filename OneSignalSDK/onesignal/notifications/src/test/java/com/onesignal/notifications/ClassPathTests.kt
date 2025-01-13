package com.onesignal.notifications

import io.kotest.core.spec.style.FunSpec
import org.junit.jupiter.api.assertDoesNotThrow

class ClassPathTests : FunSpec({
    test("ensure the class path for NotificationOpenedActivityHMS.kt is in consistent with that returned by the backend service") {
        // The test will fail if the classpath is changed by accident.
        // If the change is intentional and corresponds with the backend update, modify or remove this test accordingly.
        val fullClassName = "com.onesignal.NotificationOpenedActivityHMS"
        assertDoesNotThrow {
            Class.forName(fullClassName)
        }
    }
})
