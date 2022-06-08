package com.onesignal.onesignal.internal.iam

import com.onesignal.OSInAppMessageOutcome
import com.onesignal.OSInAppMessagePrompt
import com.onesignal.OSInAppMessageTag
import kotlin.Throws
import org.json.JSONException
import com.onesignal.OSInAppMessageLocationPrompt
import com.onesignal.onesignal.iam.IInAppMessageAction
import org.json.JSONArray
import org.json.JSONObject
import java.util.ArrayList

class InAppMessageAction : IInAppMessageAction {
    /**
     * UUID assigned by OneSignal for internal use.
     * Package-private to track which element was tapped to report to the OneSignal dashboard.
     */
    override var clickId: String? = null
        private set

    /**
     * An optional click name entered defined by the app developer when creating the IAM
     */
    override var clickName: String? = null
        private set

    /**
     * Determines where the URL is opened, ie. Default browser.
     */
    override var urlTarget: OSInAppMessageActionUrlType? = null
        private set

    /**
     * An optional URL that opens when the action takes place
     */
    override var clickUrl: String? = null
        private set

    /**
     * UUID for the page in an IAM Carousel
     */
    override var pageId: String? = null
        private set

    /**
     * Outcome for action
     */
    override val outcomes: MutableList<OSInAppMessageOutcome> = ArrayList()

    /**
     * Prompts for action
     */
    override val prompts: MutableList<OSInAppMessagePrompt> = ArrayList()

    /**
     * Tags for action
     */
    override var tags: OSInAppMessageTag? = null
        private set

    /**
     * Determines if this was the first action taken on the in app message
     */
    override var isFirstClick = false

    /**
     * Determines if tapping on the element should close the In-App Message.
     */
    override var closesMessage = false
    @Throws(JSONException::class)
    fun OSInAppMessageAction(json: JSONObject) {
        clickId = json.optString(ID, null)
        clickName = json.optString(NAME, null)
        clickUrl = json.optString(URL, null)
        pageId = json.optString(PAGE_ID, null)
        urlTarget = OSInAppMessageActionUrlType.fromString(json.optString(URL_TARGET, null))
        if (urlTarget == null) urlTarget = OSInAppMessageActionUrlType.IN_APP_WEBVIEW
        closesMessage = json.optBoolean(CLOSE, true)
        if (json.has(OUTCOMES)) parseOutcomes(json)
        if (json.has(TAGS)) tags =
            OSInAppMessageTag(
                json.getJSONObject(
                    TAGS
                )
            )
        if (json.has(PROMPTS)) parsePrompts(json)
    }

    @Throws(JSONException::class)
    private fun parseOutcomes(json: JSONObject) {
        val outcomesJsonArray = json.getJSONArray(OUTCOMES)
        for (i in 0 until outcomesJsonArray.length()) {
            outcomes.add(
                OSInAppMessageOutcome(
                    (outcomesJsonArray[i] as JSONObject)
                )
            )
        }
    }

    @Throws(JSONException::class)
    private fun parsePrompts(json: JSONObject) {
        val promptsJsonArray = json.getJSONArray(PROMPTS)
        for (i in 0 until promptsJsonArray.length()) {
            if (promptsJsonArray[i] == OSInAppMessageLocationPrompt.LOCATION_PROMPT_KEY) {
                prompts.add(com.onesignal.onesignal.iam.OSInAppMessageLocationPrompt())
            }
        }
    }

    fun getOutcomes(): List<OSInAppMessageOutcome> {
        return outcomes
    }

    fun getPrompts(): List<OSInAppMessagePrompt> {
        return prompts
    }

    fun doesCloseMessage(): Boolean {
        return closesMessage
    }

    fun toJSONObject(): JSONObject {
        val mainObj = JSONObject()
        try {
            mainObj.put(CLICK_NAME, clickName)
            mainObj.put(CLICK_URL, clickUrl)
            mainObj.put(FIRST_CLICK, isFirstClick)
            mainObj.put(CLOSES_MESSAGE, closesMessage)
            val outcomesJson = JSONArray()
            for (outcome in outcomes) outcomesJson.put(outcome.toJSONObject())
            mainObj.put(OUTCOMES, outcomesJson)
            if (tags != null) mainObj.put(TAGS, tags!!.toJSONObject())
            // Omitted for now until necessary
//            if (urlTarget != null)
//                mainObj.put("url_target", urlTarget.toJSONObject());
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return mainObj
    }

    /**
     * An enumeration of the possible places action URL's can be loaded,
     * such as an in-app webview
     */
    enum class OSInAppMessageActionUrlType(private val text: String) {
        // Opens in an in-app webview
        IN_APP_WEBVIEW("webview"),  // Moves app to background and opens URL in browser
        BROWSER("browser"),  // Loads the URL on the in-app message webview itself
        REPLACE_CONTENT("replacement");

        override fun toString(): String {
            return text
        }

        fun toJSONObject(): JSONObject {
            val mainObj = JSONObject()
            try {
                mainObj.put("url_type", text)
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            return mainObj
        }

        companion object {
            fun fromString(text: String?): OSInAppMessageActionUrlType? {
                for (type in values()) {
                    if (type.text.equals(text, ignoreCase = true)) return type
                }
                return null
            }
        }
    }

    companion object {
        private const val ID = "id"
        private const val NAME = "name"
        private const val URL = "url"
        private const val PAGE_ID = "pageId"
        private const val URL_TARGET = "url_target"
        private const val CLOSE = "close"
        private const val CLICK_NAME = "click_name"
        private const val CLICK_URL = "click_url"
        private const val FIRST_CLICK = "first_click"
        private const val CLOSES_MESSAGE = "closes_message"
        private const val OUTCOMES = "outcomes"
        private const val TAGS = "tags"
        private const val PROMPTS = "prompts"
    }
}