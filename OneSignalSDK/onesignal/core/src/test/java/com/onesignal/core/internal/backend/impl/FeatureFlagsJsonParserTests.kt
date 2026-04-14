package com.onesignal.core.internal.backend.impl

import com.onesignal.core.internal.backend.RemoteFeatureFlagsResult
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class FeatureFlagsJsonParserTests : FunSpec({
    test("parses features array with sibling flag objects (sample contract)") {
        val payload =
            """
            {
              "features": ["feature_a", "feature_b"],
              "feature_a": { "weight": 0.1 },
              "feature_b": { "enabled": true }
            }
            """.trimIndent()

        val r = FeatureFlagsJsonParser.parse(payload)

        r.enabledKeys shouldBe listOf("feature_a", "feature_b")
        val meta = requireNotNull(r.metadata)
        meta.getValue("feature_a").toString().contains("weight") shouldBe true
        meta.getValue("feature_a").toString().contains("0.1") shouldBe true
        meta.getValue("feature_b").toString().contains("enabled") shouldBe true
    }

    test("omits metadata entry when flag has no sibling object") {
        val payload = """{"features":["only_key"]}"""

        val r = FeatureFlagsJsonParser.parse(payload)

        r.enabledKeys shouldBe listOf("only_key")
        r.metadata shouldBe null
    }

    test("features list plus sibling object for only some ids (valid JSON with commas)") {
        val payload =
            """
            {
              "features": ["feature_a", "feature_b"],
              "feature_a": { "weight": 0.1 }
            }
            """.trimIndent()

        val r = FeatureFlagsJsonParser.parse(payload)

        r.enabledKeys shouldBe listOf("feature_a", "feature_b")
        val meta = requireNotNull(r.metadata)
        meta.containsKey("feature_a") shouldBe true
        meta.containsKey("feature_b") shouldBe false
        val weight = requireNotNull(meta.getValue("feature_a").jsonObject["weight"]).jsonPrimitive.content
        weight shouldBe "0.1"
    }

    test("normalizes feature ids to lowercase and resolves sibling with mismatched casing") {
        val payload =
            """
            {
              "features": ["SDK_Background_Threading"],
              "sdk_background_threading": { "weight": 0.5 }
            }
            """.trimIndent()

        val r = FeatureFlagsJsonParser.parse(payload)

        r.enabledKeys shouldBe listOf("sdk_background_threading")
        val meta = requireNotNull(r.metadata)
        meta.getValue("sdk_background_threading").jsonObject.getValue("weight").jsonPrimitive.content shouldBe "0.5"
    }

    test("invalid json returns empty") {
        FeatureFlagsJsonParser.parse("{") shouldBe RemoteFeatureFlagsResult.EMPTY
    }

    test("encodeMetadata round-trips for storage string") {
        val payload =
            """
            {"features":["x"],"x":{"weight":2.5}}
            """.trimIndent()
        val r = FeatureFlagsJsonParser.parse(payload)
        val encoded = requireNotNull(FeatureFlagsJsonParser.encodeMetadata(r.metadata))
        encoded.contains("weight") shouldBe true
        encoded.contains("2.5") shouldBe true
    }

    test("missing features key returns empty") {
        FeatureFlagsJsonParser.parse("""{"feature_a":{}}""") shouldBe RemoteFeatureFlagsResult.EMPTY
    }

    test("features not an array returns empty") {
        FeatureFlagsJsonParser.parse("""{"features":"bad"}""") shouldBe RemoteFeatureFlagsResult.EMPTY
    }

    test("parseStoredMetadataMap splits root object into flag id to JsonObject") {
        val map = FeatureFlagsJsonParser.parseStoredMetadataMap("""{"a":{"weight":1},"b":2}""")
        map.keys shouldBe setOf("a")
        requireNotNull(map.getValue("a")["weight"]).toString() shouldBe "1"
    }

    test("parseStoredMetadataMap blank or invalid returns empty") {
        FeatureFlagsJsonParser.parseStoredMetadataMap(null) shouldBe emptyMap()
        FeatureFlagsJsonParser.parseStoredMetadataMap("") shouldBe emptyMap()
        FeatureFlagsJsonParser.parseStoredMetadataMap("{") shouldBe emptyMap()
    }
})
