package com.onesignal.core.internal.backend.impl

import com.onesignal.core.internal.backend.RemoteFeatureFlagsResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * **What this is:** strict JSON parsing for the Turbine SDK feature-flags response
 * ([OneSignal/turbine#1681](https://github.com/OneSignal/turbine/pull/1681)).
 *
 * **Wire shape:** root object with a `features` array of **string** flag ids. Optional per-flag JSON
 * objects may appear as **sibling root properties** (same name as the id); those are merged into
 * [RemoteFeatureFlagsResult.metadata].
 *
 * **Optional metadata:** for a string entry `"foo"`, if the root also has a property `"foo"` (or
 * case-insensitive match) whose value is a JSON object, that object is stored per-flag in
 * [RemoteFeatureFlagsResult.metadata]. That lets a future API add per-flag config without a new
 * top-level field; the SDK persists it in [com.onesignal.core.internal.config.ConfigModel.sdkRemoteFeatureFlagMetadata]
 * so a later process or SDK version can read it without re-fetching.
 *
 * **API surface:** [parseSuccessful] for HTTP 200 bodies; [parse] for lenient “best effort”;
 * [encodeMetadata] / [parseStoredMetadataMap] for the persisted metadata column.
 *
 * Uses only Kotlin stdlib + kotlinx.serialization (Kotlin Multiplatform-friendly).
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

    private const val FEATURES_PROPERTY = "features"

    fun parse(payload: String): RemoteFeatureFlagsResult = parseSuccessful(payload) ?: RemoteFeatureFlagsResult.EMPTY

    /**
     * Parses a 200 response body. Returns `null` if the text is not JSON, not an object, or does not
     * contain a `features` **array** of the expected element types. Returns an empty result for
     * `{"features":[]}`.
     */
    fun parseSuccessful(payload: String): RemoteFeatureFlagsResult? {
        return try {
            val root = format.parseToJsonElement(payload) as? JsonObject ?: return null
            parseRootStrict(root)
        } catch (_: Throwable) {
            null
        }
    }

    private fun parseRootStrict(root: JsonObject): RemoteFeatureFlagsResult? {
        val featuresEl = root[FEATURES_PROPERTY] ?: return null
        val featuresArray = featuresEl as? JsonArray ?: return null
        val flagEntries =
            featuresArray.mapNotNull { el ->
                (el as? JsonPrimitive)
                    ?.takeIf { it.isString }
                    ?.content
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { raw -> raw to canonicalFeatureFlagId(raw) }
            }.distinctBy { it.second }

        if (flagEntries.isEmpty()) {
            return RemoteFeatureFlagsResult(emptyList(), null)
        }

        val keys = flagEntries.map { it.second }

        val metadata =
            buildJsonObject {
                for ((rawKey, canonicalKey) in flagEntries) {
                    findSiblingJsonObject(root, rawKey, canonicalKey)?.let { put(canonicalKey, it) }
                }
            }
        val metaOut = if (metadata.isEmpty()) null else metadata
        return RemoteFeatureFlagsResult(keys, metaOut)
    }

    private fun findSiblingJsonObject(
        root: JsonObject,
        rawKeyFromFeaturesArray: String,
        canonicalKey: String,
    ): JsonObject? {
        for (candidate in listOf(rawKeyFromFeaturesArray, canonicalKey)) {
            if (candidate == FEATURES_PROPERTY) {
                continue
            }
            when (val v = root[candidate]) {
                is JsonObject -> return v
                else -> Unit
            }
        }
        for ((k, v) in root) {
            if (k == FEATURES_PROPERTY) {
                continue
            }
            if (k.equals(rawKeyFromFeaturesArray, ignoreCase = true) && v is JsonObject) {
                return v
            }
        }
        return null
    }

    private fun canonicalFeatureFlagId(raw: String): String =
        buildString(raw.length) {
            for (c in raw) {
                append(c.lowercaseChar())
            }
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
