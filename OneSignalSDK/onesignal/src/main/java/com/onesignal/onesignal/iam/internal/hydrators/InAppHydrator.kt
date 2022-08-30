package com.onesignal.onesignal.iam.internal.hydrators

import com.onesignal.onesignal.core.internal.logging.Logging
import com.onesignal.onesignal.core.internal.time.ITime
import com.onesignal.onesignal.core.user.IUserManager
import com.onesignal.onesignal.iam.internal.InAppMessage
import com.onesignal.onesignal.iam.internal.InAppMessageContent
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.ArrayList

internal class InAppHydrator(
    private val _time: ITime,
    private val _userManager: IUserManager
) {

    fun hydrateIAMMessages(jsonArray: JSONArray): List<InAppMessage> {
        val newMessages = ArrayList<InAppMessage>()
        for (i in 0 until jsonArray.length()) {
            val messageJson: JSONObject = jsonArray.getJSONObject(i)
            val message = InAppMessage(messageJson, _time)
            // Avoid null checks later if IAM already comes with null id
            if (message.messageId != null) {
                newMessages.add(message)
            }
        }
        return newMessages
    }

    fun hydrateIAMMessageContent(jsonObject: JSONObject): InAppMessageContent? {
        try {
            val content = InAppMessageContent(jsonObject)
            if (content.contentHtml == null) {
                Logging.debug("displayMessage:OnSuccess: No HTML retrieved from loadMessageContent")
                return null
            }

            content.contentHtml = taggedHTMLString(content.contentHtml!!)
            return content
        } catch (e: JSONException) {
            Logging.error("Error attempting to hydrate InAppMessageContent: $jsonObject", e)
        }

        return null
    }

    private fun taggedHTMLString(untaggedString: String): String {
        val tagsAsJson = JSONObject(_userManager.tags)
        val tagsString = tagsAsJson.toString()
        val tagsDict: String = tagsString
        val tagScript = LIQUID_TAG_SCRIPT
        return untaggedString + String.format(tagScript, tagsDict)
    }

    companion object {
        private const val LIQUID_TAG_SCRIPT = "\n\n" +
            "<script>\n" +
            "    setPlayerTags(%s);\n" +
            "</script>"
    }
}
