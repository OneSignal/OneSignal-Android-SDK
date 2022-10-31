package com.onesignal.iam.internal

import com.onesignal.iam.IInAppMessageAction
import com.onesignal.iam.InAppMessageActionUrlType
import com.onesignal.iam.internal.prompt.IInAppMessagePromptFactory
import com.onesignal.iam.internal.prompt.impl.InAppMessagePrompt
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal class InAppMessageAction(json: JSONObject, promptFactory: IInAppMessagePromptFactory) : IInAppMessageAction {
    /**
     * UUID assigned by OneSignal for internal use.
     * Package-private to track which element was tapped to report to the OneSignal dashboard.
     */
    override val clickId: String?

    /**
     * An optional click name entered defined by the app developer when creating the IAM
     */
    override val clickName: String?

    /**
     * Determines where the URL is opened, ie. Default browser.
     */
    override var urlTarget: InAppMessageActionUrlType? = null

    /**
     * An optional URL that opens when the action takes place
     */
    override val clickUrl: String?

    /**
     * UUID for the page in an IAM Carousel
     */
    override val pageId: String?

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
    override var isFirstClick = false

    /**
     * Determines if tapping on the element should close the In-App Message.
     */
    override val closesMessage: Boolean

    init {
        clickId = json.optString(ID, null)
        clickName = json.optString(NAME, null)
        clickUrl = json.optString(URL, null)
        pageId = json.optString(PAGE_ID, null)

        urlTarget = InAppMessageActionUrlType.fromString(json.optString(URL_TARGET, null))
        if (urlTarget == null) {
            urlTarget = InAppMessageActionUrlType.IN_APP_WEBVIEW
        }

        closesMessage = json.optBoolean(CLOSE, true)
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
    private fun parsePrompts(json: JSONObject, promptFactory: IInAppMessagePromptFactory) {
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
            mainObj.put(CLICK_NAME, clickName)
            mainObj.put(CLICK_URL, clickUrl)
            mainObj.put(FIRST_CLICK, isFirstClick)
            mainObj.put(CLOSES_MESSAGE, closesMessage)
            val outcomesJson = JSONArray()
            for (outcome in outcomes) outcomesJson.put(outcome.toJSONObject())
            mainObj.put(OUTCOMES, outcomesJson)
            if (tags != null) {
                mainObj.put(TAGS, tags!!.toJSONObject())
            }
            // Omitted for now until necessary
//            if (urlTarget != null)
//                mainObj.put("url_target", urlTarget.toJSONObject());
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
