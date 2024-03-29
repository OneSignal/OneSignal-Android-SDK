package com.onesignal.inAppMessages.internal

import com.onesignal.inAppMessages.IInAppMessageClickResult
import com.onesignal.inAppMessages.InAppMessageActionUrlType
import com.onesignal.inAppMessages.internal.prompt.IInAppMessagePromptFactory
import com.onesignal.inAppMessages.internal.prompt.impl.InAppMessagePrompt
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal class InAppMessageClickResult(json: JSONObject, promptFactory: IInAppMessagePromptFactory) :
    IInAppMessageClickResult {
    /**
     * UUID assigned by OneSignal for internal use.
     * Package-private to track which element was tapped to report to the OneSignal dashboard.
     */
    val clickId: String?

    /**
     * An optional click name entered defined by the app developer when creating the IAM
     */
    override val actionId: String?

    /**
     * Determines where the URL is opened, ie. Default browser.
     */
    override var urlTarget: InAppMessageActionUrlType? = null

    /**
     * An optional URL that opens when the action takes place
     */
    override val url: String?

    /**
     * UUID for the page in an IAM Carousel
     */
    val pageId: String?

    /**
     * Outcome for action
     */
    val outcomes: MutableList<InAppMessageOutcome> = mutableListOf()

    /**
     * Prompts for action
     */
    val prompts: MutableList<InAppMessagePrompt> = mutableListOf()

    /**
     * Tags for action
     */
    var tags: InAppMessageTag? = null

    /**
     * Determines if this was the first action taken on the in app message
     */
    var isFirstClick = false

    /**
     * Determines if tapping on the element should close the In-App Message.
     */
    override val closingMessage: Boolean

    init {
        clickId = json.optString(ID, null)
        actionId = json.optString(NAME, null)
        url = json.optString(URL, null)
        pageId = json.optString(PAGE_ID, null)

        urlTarget = InAppMessageActionUrlType.fromString(json.optString(URL_TARGET, null))
        if (urlTarget == null) {
            urlTarget = InAppMessageActionUrlType.IN_APP_WEBVIEW
        }

        closingMessage = json.optBoolean(CLOSE, true)
        if (json.has(OUTCOMES)) parseOutcomes(json)
        if (json.has(TAGS)) tags = InAppMessageTag(json.getJSONObject(TAGS))
        if (json.has(PROMPTS)) parsePrompts(json, promptFactory)
    }

    @Throws(JSONException::class)
    private fun parseOutcomes(json: JSONObject) {
        val outcomesJsonArray = json.getJSONArray(OUTCOMES)
        for (i in 0 until outcomesJsonArray.length()) {
            outcomes.add(InAppMessageOutcome((outcomesJsonArray[i] as JSONObject)))
        }
    }

    @Throws(JSONException::class)
    private fun parsePrompts(
        json: JSONObject,
        promptFactory: IInAppMessagePromptFactory,
    ) {
        val promptsJsonArray = json.getJSONArray(PROMPTS)
        for (i in 0 until promptsJsonArray.length()) {
            val promptType = promptsJsonArray.getString(i)
            val prompt = promptFactory.createPrompt(promptType)
            if (prompt != null) {
                prompts.add(prompt)
            }
        }
    }

    fun toJSONObject(): JSONObject {
        val mainObj = JSONObject()
        try {
            mainObj.put(CLICK_NAME, actionId)
            mainObj.put(CLICK_URL, url)
            mainObj.put(FIRST_CLICK, isFirstClick)
            mainObj.put(CLOSES_MESSAGE, closingMessage)
            val outcomesJson = JSONArray()
            for (outcome in outcomes) outcomesJson.put(outcome.toJSONObject())
            mainObj.put(OUTCOMES, outcomesJson)
            if (tags != null) {
                mainObj.put(TAGS, tags!!.toJSONObject())
            }
            if (urlTarget != null) {
                mainObj.put("url_target", urlTarget.toString())
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return mainObj
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
