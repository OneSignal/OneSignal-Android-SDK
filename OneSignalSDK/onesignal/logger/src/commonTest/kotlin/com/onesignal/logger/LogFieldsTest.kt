package com.onesignal.logger

import com.onesignal.logger.attributes.LogFieldsPerEvent
import com.onesignal.logger.attributes.LogFieldsTopLevel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LogFieldsTest {
    @Test
    fun topLevelIncludesExpectedKeysAndOmitsNullWrapper() = runTest {
        val provider = FakePlatformProvider()
        val attrs = LogFieldsTopLevel(provider).getAttributes()

        assertEquals("install-abc", attrs["ossdk.install_id"])
        assertEquals("android", attrs["ossdk.sdk_base"])
        assertEquals("Android", attrs["os.name"])
        assertFalse(attrs.containsKey("ossdk.sdk_wrapper"))
    }

    @Test
    fun perEventIncludesDynamicValuesAndUniqueRecordId() {
        val provider = FakePlatformProvider()
        val fields = LogFieldsPerEvent(provider)

        val a = fields.getAttributes()
        val b = fields.getAttributes()

        assertEquals("app-123", a["ossdk.app_id"])
        assertEquals("foreground", a["app.state"])
        assertEquals("1234", a["process.uptime"])
        assertEquals("test-thread", a["thread.name"])
        // record uid must be unique per event
        assertTrue(a["log.record.uid"] != b["log.record.uid"])
    }

    @Test
    fun featureFlagsAreSortedCsvAndOmittedWhenEmpty() {
        val provider = FakePlatformProvider(enabledFeatureFlags = listOf("zeta", "alpha"))
        val attrs = LogFieldsPerEvent(provider).getAttributes()
        assertEquals("alpha,zeta", attrs["ossdk.feature_flags"])

        val empty = LogFieldsPerEvent(FakePlatformProvider()).getAttributes()
        assertFalse(empty.containsKey("ossdk.feature_flags"))
    }
}
