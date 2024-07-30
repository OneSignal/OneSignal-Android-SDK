package com.onesignal.inAppMessages.internal

import com.onesignal.core.internal.time.ITime
import io.mockk.mockk
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class InAppMessagingHelpers {
    companion object {
        const val TEST_SPANISH_ANDROID_VARIANT_ID = "d8cc-11e4-bed1-df8f05be55ba-a4b3gj7f"
        const val TEST_ENGLISH_ANDROID_VARIANT_ID = "11e4-bed1-df8f05be55ba-a4b3gj7f-d8cc"
        const val IAM_CLICK_ID = "12345678-1234-1234-1234-123456789012"
        const val IAM_PAGE_ID = "12345678-1234-ABCD-1234-123456789012"
        const val IAM_HAS_LIQUID = "has_liquid"

        internal fun evaluateMessage(message: InAppMessage) {
            // TODO
        }

        internal fun onMessageWasDisplayed(message: InAppMessage) {
            val mockInAppMessageManager = mockk<InAppMessagesManager>()
            mockInAppMessageManager.onMessageWasDisplayed(message)
        }

        internal fun onMessageActionOccurredOnMessage(
            message: InAppMessage,
            clickResult: InAppMessageClickResult,
        ) {
            val mockInAppMessageManager = mockk<InAppMessagesManager>()
            mockInAppMessageManager.onMessageActionOccurredOnMessage(message, clickResult)
        }

        fun buildTestMessageWithLiquid(triggerJson: JSONArray?): OSTestInAppMessageInternal {
            val json = basicIAMJSONObject(triggerJson)
            json.put(IAM_HAS_LIQUID, true)
            return OSTestInAppMessageInternal(json)
        }

        internal fun buildTestMessageWithSingleTriggerAndLiquid(
            kind: Trigger.OSTriggerKind,
            key: String?,
            operator: String?,
            value: Any?,
        ): OSTestInAppMessageInternal {
            val triggersJson =
                basicTrigger(
                    kind,
                    key,
                    operator!!,
                    value!!,
                )
            return buildTestMessageWithLiquid(triggersJson)
        }

        // Most tests build a test message using only one trigger.
        // This convenience method makes it easy to build such a message
        internal fun buildTestMessageWithSingleTrigger(
            kind: Trigger.OSTriggerKind,
            key: String?,
            operator: String,
            value: Any,
        ): OSTestInAppMessageInternal {
            val triggersJson = basicTrigger(kind, key, operator, value)
            return buildTestMessage(triggersJson)
        }

        private fun buildTestMessage(triggerJson: JSONArray?): OSTestInAppMessageInternal {
            return OSTestInAppMessageInternal(basicIAMJSONObject(triggerJson))
        }

        private fun basicTrigger(
            kind: Trigger.OSTriggerKind,
            key: String?,
            operator: String,
            value: Any,
        ): JSONArray {
            val triggerJson: JSONObject =
                object : JSONObject() {
                    init {
                        put("id", UUID.randomUUID().toString())
                        put("kind", kind.toString())
                        put("property", key)
                        put("operator", operator)
                        put("value", value)
                    }
                }

            return wrap(wrap(triggerJson))
        }

        private fun wrap(`object`: Any?): JSONArray {
            return object : JSONArray() {
                init {
                    put(`object`)
                }
            }
        }

        fun buildTestMessageWithRedisplay(
            limit: Int,
            delay: Long,
        ): OSTestInAppMessageInternal {
            return buildTestMessageWithMultipleDisplays(null, limit, delay)
        }

        private fun buildTestMessageWithMultipleDisplays(
            triggerJson: JSONArray?,
            limit: Int,
            delay: Long,
        ): OSTestInAppMessageInternal {
            val json = basicIAMJSONObject(triggerJson)
            json.put(
                "redisplay",
                object : JSONObject() {
                    init {
                        put("limit", limit)
                        put("delay", delay) // in seconds
                    }
                },
            )

            return OSTestInAppMessageInternal(json)
        }

        private fun basicIAMJSONObject(triggerJson: JSONArray?): JSONObject {
            val jsonObject = JSONObject()
            jsonObject.put("id", UUID.randomUUID().toString())
            jsonObject.put("clickIds", JSONArray(listOf("clickId1", "clickId2", "clickId3")))
            // shouldn't hard-code?
            jsonObject.put("displayedInSession", true)
            jsonObject.put(
                "variants",
                JSONObject().apply {
                    put(
                        "android",
                        JSONObject().apply {
                            put("es", TEST_SPANISH_ANDROID_VARIANT_ID)
                            put("en", TEST_ENGLISH_ANDROID_VARIANT_ID)
                        },
                    )
                },
            )
            jsonObject.put("max_display_time", 30)
            if (triggerJson != null) {
                jsonObject.put("triggers", triggerJson)
            } else {
                jsonObject.put("triggers", JSONArray())
            }
            jsonObject.put(
                "actions",
                JSONArray().apply {
                    put(buildTestActionJson())
                },
            )

            return jsonObject
        }

        fun buildTestActionJson(): JSONObject {
            return object : JSONObject() {
                init {
                    put("click_type", "button")
                    put("id", IAM_CLICK_ID)
                    put("name", "click_name")
                    put("url", "https://www.onesignal.com")
                    put("url_target", "webview")
                    put("close", true)
                    put("pageId", IAM_PAGE_ID)
                    put(
                        "data",
                        object : JSONObject() {
                            init {
                                put("test", "value")
                            }
                        },
                    )
                }
            }
        }
    }

    // WIP

    /** IAM Lifecycle  */
    internal fun onMessageWillDisplay(message: InAppMessage) {
        val mockInAppMessageManager = mockk<InAppMessagesManager>()
        mockInAppMessageManager.onMessageWillDisplay(message)
    }

    internal fun onMessageDidDisplay(message: InAppMessage) {
        val mockInAppMessageManager = mockk<InAppMessagesManager>()
        mockInAppMessageManager.onMessageWasDisplayed(message)
    }

    internal fun onMessageWillDismiss(message: InAppMessage) {
        val mockInAppMessageManager = mockk<InAppMessagesManager>()
        mockInAppMessageManager.onMessageWillDismiss(message)
    }

    internal fun onMessageDidDismiss(message: InAppMessage) {
        val mockInAppMessageManager = mockk<InAppMessagesManager>()
        mockInAppMessageManager.onMessageWasDismissed(message)
    }

    // End IAM Lifecycle

    class OSTestInAppMessageInternal(
        private val jsonObject: JSONObject,
    ) {
        private val inAppMessage: InAppMessage by lazy {
            initializeInAppMessage()
        }

        private fun initializeInAppMessage(): InAppMessage {
            val time =
                object : ITime {
                    override val currentTimeMillis: Long
                        get() = System.currentTimeMillis()
                }

            return InAppMessage(jsonObject, time)
        }

        val messageId: String
            get() = inAppMessage.messageId

        val variants: Map<String, Map<String, String>>
            get() = inAppMessage.variants

        internal val triggers: List<List<Trigger>>
            get() = inAppMessage.triggers

        val clickedClickIds: MutableSet<String>
            get() = inAppMessage.clickedClickIds

        var isDisplayedInSession: Boolean
            get() = inAppMessage.isDisplayedInSession
            set(value) {
                inAppMessage.isDisplayedInSession = value
            }

        internal val redisplayStats: InAppMessageRedisplayStats
            get() = inAppMessage.redisplayStats

        // Extract limit and delay from the JSON object
        private val redisplayLimit: Int
            get() = jsonObject.optJSONObject("redisplay")?.optInt("limit", -1) ?: -1

        private val redisplayDelay: Long
            get() = jsonObject.optJSONObject("redisplay")?.optLong("delay", -1L) ?: -1L

        fun isClickAvailable(clickId: String?): Boolean {
            return !clickedClickIds.contains(clickId)
        }

        fun addClickId(clickId: String) {
            clickedClickIds.add(clickId)
        }

        fun clearClickIds() {
            clickedClickIds.clear()
        }

        override fun toString(): String {
            return "OSTestInAppMessageInternal(jsonObject=$jsonObject, redisplayLimit=$redisplayLimit, redisplayDelay=$redisplayDelay)"
        }
    }
}
