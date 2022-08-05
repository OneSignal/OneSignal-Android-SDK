package com.onesignal.onesignal.iam.internal

import com.onesignal.onesignal.core.internal.common.DateUtils
import com.onesignal.onesignal.core.internal.time.ITime
import com.onesignal.onesignal.iam.IInAppMessage
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.ParseException
import java.util.*

/**
 * Represents an In App Message that exists within an application.
 */
internal class InAppMessage(
    override val messageId: String,
    time: ITime
) : IInAppMessage {
    /**
     * Allows in-app messages to use multiple language variants, or to have variations between
     * different device types (ie. a different image for phones vs. tablets, etc.).
     *
     * An example: {'ios' : {'en' : 'wfgkv-...', 'es' : '56ytdygd...' }}
     */
    var variants: Map<String, Map<String, String>> = mapOf()
        private set

    /**
     * An array of arrays of triggers. The outer array represents AND conditions,
     * while the inner array represents AND conditions.
     */
    var triggers: List<List<Trigger>> = listOf()
        private set

    /**
     * IAM clicks associated to this IAM
     */
    var clickedClickIds: MutableSet<String> = mutableSetOf()
        private set

    /**
     * Reference to redisplay properties
     */
    var redisplayStats: InAppMessageRedisplayStats = InAppMessageRedisplayStats(time)
        private set

    /**
     * The duration this IAM has been displayed on the device.
     */
    var displayDuration = 0.0

    /**
     * Whether this IAM has been displayed in the session.
     */
    var isDisplayedInSession = false

    /**
     * Whether a trigger change has occurred
     */
    var isTriggerChanged = false
    private var actionTaken = false
    private var endTime: Date? = null

    /**
     * whether this IAM is a preview message
     */
    var isPreview = false
        private set

    /**
     * Whether this IAM has Liquid syntax which requires processing.
     */
    var hasLiquid = false
        private set

    constructor(isPreview: Boolean, time: ITime) : this("", time) {
        this.isPreview = isPreview
    }

    constructor(
        messageId: String,
        clickIds: Set<String>,
        displayedInSession: Boolean,
        redisplayStats: InAppMessageRedisplayStats,
        time: ITime
    ) : this(messageId, time) {
        clickedClickIds = clickIds.toMutableSet()
        isDisplayedInSession = displayedInSession
        this.redisplayStats = redisplayStats
    }

    constructor(json: JSONObject, time: ITime) : this(json.getString(ID), time) {
        // initialize simple root properties
        // "id" is expected instead of "messageId" when parsing JSON from the backend
        variants = parseVariants(json.getJSONObject(IAM_VARIANTS))
        triggers = parseTriggerJson(json.getJSONArray(IAM_TRIGGERS))
        endTime = parseEndTimeJson(json)
        if (json.has(HAS_LIQUID))
            hasLiquid = json.getBoolean(HAS_LIQUID)

        if (json.has(IAM_REDISPLAY_STATS))
            redisplayStats = InAppMessageRedisplayStats(json.getJSONObject(IAM_REDISPLAY_STATS), time)
    }

    private fun parseEndTimeJson(json: JSONObject): Date? {
        val endTimeString: String
        endTimeString = try {
            json.getString(END_TIME)
        } catch (e: JSONException) {
            return null
        }
        if (endTimeString == "null") return null
        try {
            val format = DateUtils.iso8601Format()
            return format.parse(endTimeString)
        } catch (e: ParseException) {
            e.printStackTrace()
        }
        return null
    }

    @Throws(JSONException::class)
    private fun parseVariants(json: JSONObject): HashMap<String, HashMap<String, String>> {
        val variantTypes = HashMap<String, HashMap<String, String>>()
        val keyIterator = json.keys()
        while (keyIterator.hasNext()) {
            val variantType = keyIterator.next()
            val variant = json.getJSONObject(variantType)
            val variantMap = HashMap<String, String>()
            val variantIterator = variant.keys()
            while (variantIterator.hasNext()) {
                val languageType = variantIterator.next()
                variantMap[languageType] = variant.getString(languageType)
            }
            variantTypes[variantType] = variantMap
        }
        return variantTypes
    }

    @Throws(JSONException::class)
    private fun parseTriggerJson(triggersJson: JSONArray): ArrayList<ArrayList<Trigger>> {
        // initialize triggers
        val parsedTriggers = ArrayList<ArrayList<Trigger>>()
        for (i in 0 until triggersJson.length()) {
            val ands = triggersJson.getJSONArray(i)
            val converted = ArrayList<Trigger>()
            for (j in 0 until ands.length()) {
                val trigger = Trigger(ands.getJSONObject(j))
                converted.add(trigger)
            }
            parsedTriggers.add(converted)
        }
        return parsedTriggers
    }

    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        try {
            json.put(IAM_ID, messageId)
            val variants = JSONObject()
            for (key in this.variants.keys) {
                val variant = this.variants[key]!!
                val converted = JSONObject()
                for (variantKey in variant.keys) converted.put(variantKey, variant[variantKey])
                variants.put(key, converted)
            }
            json.put(IAM_VARIANTS, variants)
            json.put(DISPLAY_DURATION, displayDuration)
            json.put(IAM_REDISPLAY_STATS, redisplayStats.toJSONObject())
            val orConditions = JSONArray()
            for (andArray in triggers) {
                val andConditions = JSONArray()
                for (trigger in andArray) andConditions.put(trigger.toJSONObject())
                orConditions.put(andConditions)
            }
            json.put(IAM_TRIGGERS, orConditions)
            if (endTime != null) {
                val format = DateUtils.iso8601Format()
                val endTimeString = format.format(endTime)
                json.put(END_TIME, endTimeString)
            }
            json.put(HAS_LIQUID, hasLiquid)
        } catch (exception: JSONException) {
            exception.printStackTrace()
        }
        return json
    }

    /**
     * Called when an action is taken to track uniqueness
     * @return true if action taken was unique
     */
    fun takeActionAsUnique(): Boolean {
        return if (actionTaken) false else true.also { actionTaken = it }
    }

    fun isClickAvailable(clickId: String): Boolean {
        return !clickedClickIds.contains(clickId)
    }

    fun clearClickIds() {
        clickedClickIds.clear()
    }

    fun addClickId(clickId: String) {
        clickedClickIds.add(clickId)
    }

    fun removeClickId(clickId: String) {
        clickedClickIds.remove(clickId)
    }

    override fun toString(): String {
        return "OSInAppMessage{" +
                "messageId='" + messageId + '\'' +
                ", variants=" + variants +
                ", triggers=" + triggers +
                ", clickedClickIds=" + clickedClickIds +
                ", redisplayStats=" + redisplayStats +
                ", displayDuration=" + displayDuration +
                ", displayedInSession=" + isDisplayedInSession +
                ", triggerChanged=" + isTriggerChanged +
                ", actionTaken=" + actionTaken +
                ", isPreview=" + isPreview +
                ", endTime=" + endTime +
                ", hasLiquid=" + hasLiquid +
                '}'
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val that = o as InAppMessage
        return messageId == that.messageId
    }

    override fun hashCode(): Int {
        return messageId.hashCode()
    }

    val isFinished: Boolean
        get() {
            if (endTime == null) {
                return false
            }
            val now = Date()
            return endTime!!.before(now)
        }

    companion object {
        // "id" is expected instead of "messageId" when parsing JSON from the backend
        private const val ID = "id"
        private const val IAM_ID = "messageId"
        private const val IAM_VARIANTS = "variants"
        private const val IAM_TRIGGERS = "triggers"
        private const val IAM_REDISPLAY_STATS = "redisplay"
        private const val DISPLAY_DURATION = "displayDuration"
        private const val END_TIME = "end_time"
        private const val HAS_LIQUID = "has_liquid"
    }
}