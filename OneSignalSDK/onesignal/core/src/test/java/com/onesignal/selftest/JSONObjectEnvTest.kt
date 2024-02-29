package com.onesignal.selftest

import com.onesignal.common.toMap
import com.onesignal.testhelpers.extensions.RobolectricTest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.spec.style.funSpec
import io.kotest.matchers.shouldBe
import org.json.JSONObject

/**
 * This purpose of this file is to ensure org.json classes being used in
 * other tests matches real Android devices.
*/

val keyAOSPBehavior =
    funSpec {
        // This is a key oddity only AOSP seems to do
        test("JSONObject.getString stringifies JSON object") {
            val json =
                JSONObject()
                    .put(
                        "obj",
                        JSONObject().put("nested", "value"),
                    )
            json.getString("obj") shouldBe "{\"nested\":\"value\"}"
        }
    }

@RobolectricTest
class JSONObjectRobolectricEnvTest : FunSpec({
    test("ensure our src JSON Kotlin extension methods work with @RobolectricTest") {
        val test = JSONObject()
        test.put("test", "test")
        // Ensuring this doesn't throw
        test.toMap()
    }

    include(keyAOSPBehavior)
})

class JSONObjectJVMEnvTest : FunSpec({
    include(keyAOSPBehavior)
})
