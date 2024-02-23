package com.onesignal.selftest

import com.onesignal.common.toMap
import com.onesignal.testhelpers.extensions.RobolectricTest
import io.kotest.core.spec.style.FunSpec
import io.kotest.runner.junit4.KotestTestRunner
import org.json.JSONObject
import org.junit.runner.RunWith

@RobolectricTest
@RunWith(KotestTestRunner::class)
class JSONObjectExtensionMethodsAllowedInTestsTest : FunSpec({
    // This is testing logic in ContainedRobolectricRunner.keepExistingOrgJson() works
    test("ensure we can use a Kotlin extension method defined in src in our tests") {
        val test = JSONObject()
        test.put("test", "test")
        // Ensuring this doesn't throw
        test.toMap()
    }
})
