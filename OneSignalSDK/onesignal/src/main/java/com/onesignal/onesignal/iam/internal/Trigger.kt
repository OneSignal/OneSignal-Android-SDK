package com.onesignal.onesignal.iam.internal

import org.json.JSONException
import org.json.JSONObject

internal class Trigger(json: JSONObject) {
    /**
     * An enumeration of the operators triggers can use
     */
    enum class OSTriggerOperator(private val text: String) {
        GREATER_THAN("greater"), LESS_THAN("less"), EQUAL_TO("equal"), NOT_EQUAL_TO("not_equal"), LESS_THAN_OR_EQUAL_TO(
            "less_or_equal"
        ),
        GREATER_THAN_OR_EQUAL_TO("greater_or_equal"), EXISTS("exists"), NOT_EXISTS("not_exists"), CONTAINS(
            "in"
        );

        override fun toString(): String {
            return text
        }

        fun checksEquality(): Boolean {
            return this == EQUAL_TO || this == NOT_EQUAL_TO
        }

        companion object {
            fun fromString(text: String?): OSTriggerOperator {
                for (type in values()) {
                    if (type.text.equals(text, ignoreCase = true)) return type
                }
                return EQUAL_TO
            }
        }
    }

    enum class OSTriggerKind(private val value: String) {
        TIME_SINCE_LAST_IN_APP("min_time_since"), SESSION_TIME("session_time"), CUSTOM("custom"), UNKNOWN(
            "unknown"
        );

        override fun toString(): String {
            return value
        }

        companion object {
            fun fromString(value: String?): OSTriggerKind {
                for (type in values()) {
                    if (type.value.equals(value, ignoreCase = true)) return type
                }
                return UNKNOWN
            }
        }
    }
    // Position.valueOf(jsonObject.optString("displayLocation", "FULL_SCREEN").toUpperCase());
    /**
     * The unique identifier for this trigger, to help avoid scheduling duplicate timers and so on
     */
    var triggerId: String

    /**
     * Kind of trigger; session time, time since last in app, or custom.
     */
    var kind: OSTriggerKind

    /**
     * The property that this trigger operates on, such as 'game_score'
     */
    var property: String?

    /**
     * The type of operator used to perform the logical equivalence/comparison on,
     * such as > or <=
     */
    var operatorType: OSTriggerOperator

    /**
     * Most comparison-based operators have a value to allow for triggers
     * such as game_score > 30
     */
    var value: Any?
    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        try {
            json.put("id", triggerId)
            json.put("kind", kind)
            json.put("property", property)
            json.put("operator", operatorType.toString())
            json.put("value", value)
        } catch (exception: JSONException) {
            exception.printStackTrace()
        }
        return json
    }

    override fun toString(): String {
        return "OSTrigger{" +
                "triggerId='" + triggerId + '\'' +
                ", kind=" + kind +
                ", property='" + property + '\'' +
                ", operatorType=" + operatorType +
                ", value=" + value +
                '}'
    }

    init {
        triggerId = json.getString("id")
        kind = OSTriggerKind.fromString(json.getString("kind"))
        property = json.optString("property", null)
        operatorType = OSTriggerOperator.fromString(json.getString("operator"))
        value = json.opt("value")
    }
}