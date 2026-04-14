package com.onesignal.core.internal.backend.impl

import com.onesignal.core.internal.backend.RemoteFeatureFlagsResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Parses the feature-flags API response with kotlinx.serialization.
 *
 * Expected shape:
 * ```json
 * {
 *   "features": ["feature_a", "feature_b"],
 *   "feature_a": { "weight": 0.1 },
 *   "feature_b": { ... }
 * }
 * ```
 *
 * Turbine returns only `features` ([OneSignal/turbine#1681](https://github.com/OneSignal/turbine/pull/1681)).
 * If the same flag id also appears as a root property with a JSON object value, that object is copied
 * into [RemoteFeatureFlagsResult.metadata] for forward-compatible extras (flags without a sibling
 * object stay enabled via [RemoteFeatureFlagsResult.enabledKeys] only).
 *
 * Payload must be valid JSON (e.g. a comma between `"features": [...]` and the next property).
 */
internal object FeatureFlagsJsonParser {
    /**
     * RFC 8259–style JSON only (no lenient tokens like unquoted keys, `NaN`, trailing commas).
     * That keeps [encodeMetadata] strings portable: the same text can be parsed later by
     * `org.json.JSONObject`, Gson, or kotlinx without relying on kotlinx-only quirks.
     */
    val format =
        Json {
            ignoreUnknownKeys = true
            isLenient = false
            allowSpecialFloatingPointValues = false
            prettyPrint = false
        }

    fun parse(payload: String): RemoteFeatureFlagsResult {
        return try {
            val root = format.parseToJsonElement(payload) as? JsonObject ?: return RemoteFeatureFlagsResult.EMPTY
            parseRoot(root)
        } catch (_: Throwable) {
            RemoteFeatureFlagsResult.EMPTY
        }
    }

    private fun parseRoot(root: JsonObject): RemoteFeatureFlagsResult {
        val featuresEl = root["features"] ?: return RemoteFeatureFlagsResult.EMPTY
        val featuresArray = featuresEl as? JsonArray ?: return RemoteFeatureFlagsResult.EMPTY
        val keys =
            featuresArray.mapNotNull { el ->
                (el as? JsonPrimitive)
                    ?.takeIf { it.isString }
                    ?.content
                    ?.takeIf { it.isNotBlank() }
            }
        if (keys.isEmpty()) {
            return RemoteFeatureFlagsResult(emptyList(), null)
        }

        val metadata =
            buildJsonObject {
                for (key in keys) {
                    when (val v = root[key]) {
                        is JsonObject -> put(key, v)
                        else -> Unit
                    }
                }
            }
        val metaOut = if (metadata.isEmpty()) null else metadata
        return RemoteFeatureFlagsResult(keys, metaOut)
    }

    fun encodeMetadata(metadata: JsonObject?): String? =
        metadata?.let { format.encodeToString(JsonElement.serializer(), it) }

    /**
     * Decodes [ConfigModel.sdkRemoteFeatureFlagMetadata] (a JSON object of flag id → object) into a map.
     * Non-object values are skipped so each entry stays a [JsonObject] for nested decoding (e.g. with `Json.decodeFromJsonElement`).
     */
    fun parseStoredMetadataMap(raw: String?): Map<String, JsonObject> {
        if (raw.isNullOrBlank()) {
            return emptyMap()
        }
        return try {
            val root = format.parseToJsonElement(raw) as? JsonObject ?: return emptyMap()
            root.entries.mapNotNull { (key, value) ->
                (value as? JsonObject)?.let { key to it }
            }.toMap()
        } catch (_: Throwable) {
            emptyMap()
        }
    }
}
