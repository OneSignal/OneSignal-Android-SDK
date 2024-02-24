package com.onesignal.common

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.runner.junit4.KotestTestRunner
import io.mockk.mockkStatic
import io.mockk.verify
import org.json.JSONArray
import org.json.JSONObject
import org.junit.runner.RunWith

@RunWith(KotestTestRunner::class)
class JSONObjectExtensionsTest : DescribeSpec({
    describe("toMap") {
        // Some org.json JVM libraries define their own toMap. We want to
        // ensure we are testing ours, not theirs.
        it("is using our extension function") {
            mockkStatic(JSONObject::toMap) {
                JSONObject().toMap()
                verify { any<JSONObject>().toMap() }
            }
        }

        it("supports primitives") {
            val test =
                JSONObject()
                    .put("String", "String")
                    .put("Boolean", true)
                    .put("Integer", 1)
                    .put("Long", 2L)
                    .put("Float", 3.3f)
                    .put("Double", Double.MAX_VALUE)

            test.toMap() shouldBe
                mapOf(
                    "String" to "String",
                    "Boolean" to true,
                    "Integer" to 1,
                    "Long" to 2L,
                    "Float" to 3.3f,
                    "Double" to Double.MAX_VALUE,
                )
        }

        describe("JSONArray") {
            it("supports empty") {
                val test = JSONObject().put("MyArray", JSONArray())
                test.toMap() shouldBe
                    mapOf("MyArray" to emptyList<Any>())
            }
            it("supports one item") {
                val test = JSONObject().put("MyArray", JSONArray().put("String"))
                test.toMap() shouldBe
                    mapOf("MyArray" to listOf("String"))
            }
        }

        it("supports JSONObject") {
            val test =
                JSONObject()
                    .put("MyNestedJSONObject", JSONObject().put("String", "String"))
            test.toMap() shouldBe
                mapOf(
                    "MyNestedJSONObject" to mapOf("String" to "String"),
                )
        }
    }
})
