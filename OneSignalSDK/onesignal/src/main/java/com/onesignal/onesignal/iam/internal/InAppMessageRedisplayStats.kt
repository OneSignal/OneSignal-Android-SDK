package com.onesignal.onesignal.iam.internal

import com.onesignal.onesignal.core.internal.time.ITime
import com.onesignal.onesignal.core.internal.logging.Logging
import org.json.JSONException
import org.json.JSONObject

internal class InAppMessageRedisplayStats(
    private val _time: ITime
) {
    // Last IAM display time in seconds
    var lastDisplayTime: Long = -1

    // Current quantity of displays
    var displayQuantity = 0

    // Quantity of displays limit
    var displayLimit = 1

    // Delay between displays in seconds
    var displayDelay: Long = 0
    var isRedisplayEnabled = false
        private set

    constructor(displayQuantity: Int, lastDisplayTime: Long, time: ITime) : this(time) {
        this.displayQuantity = displayQuantity
        this.lastDisplayTime = lastDisplayTime
    }

    constructor(json: JSONObject, time: ITime) : this(time) {
        isRedisplayEnabled = true
        val displayLimit = json[DISPLAY_LIMIT]
        val displayDelay = json[DISPLAY_DELAY]
        if (displayLimit is Int)
            this.displayLimit = displayLimit

        if (displayDelay is Long)
            this.displayDelay = displayDelay
        else if (displayDelay is Int)
            this.displayDelay = displayDelay.toLong()
    }

    fun setDisplayStats(displayStats: InAppMessageRedisplayStats) {
        lastDisplayTime = displayStats.lastDisplayTime
        displayQuantity = displayStats.displayQuantity
    }

    fun incrementDisplayQuantity() {
        displayQuantity++
    }

    fun shouldDisplayAgain(): Boolean {
        val result = displayQuantity < displayLimit
        Logging.debug("OSInAppMessage shouldDisplayAgain: $result")
        return result
    }

    // Calculate gap between display times
    val isDelayTimeSatisfied: Boolean
        get() {
            if (lastDisplayTime < 0) return true
            val currentTimeInSeconds = _time.currentTimeMillis / 1000
            // Calculate gap between display times
            val diffInSeconds = currentTimeInSeconds - lastDisplayTime
            Logging.debug("OSInAppMessage lastDisplayTime: $lastDisplayTime currentTimeInSeconds: $currentTimeInSeconds diffInSeconds: $diffInSeconds displayDelay: $displayDelay")
            return diffInSeconds >= displayDelay
        }

    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        try {
            json.put(DISPLAY_LIMIT, displayLimit)
            json.put(DISPLAY_DELAY, displayDelay)
        } catch (exception: JSONException) {
            exception.printStackTrace()
        }
        return json
    }

    override fun toString(): String {
        return "OSInAppMessageDisplayStats{" +
                "lastDisplayTime=" + lastDisplayTime +
                ", displayQuantity=" + displayQuantity +
                ", displayLimit=" + displayLimit +
                ", displayDelay=" + displayDelay +
                '}'
    }

    companion object {
        private const val DISPLAY_LIMIT = "limit"
        private const val DISPLAY_DELAY = "delay"
    }
}