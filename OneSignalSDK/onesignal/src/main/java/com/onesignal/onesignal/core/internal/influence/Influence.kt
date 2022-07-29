package com.onesignal.onesignal.core.internal.influence

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class Influence {
    /**
     * InfluenceType will be DISABLED only if the outcome feature is disabled.
     */
    var influenceType: InfluenceType
    var influenceChannel: InfluenceChannel
        private set
    var ids: JSONArray?

    @Throws(JSONException::class)
    constructor(jsonString: String) {
        val jsonObject = JSONObject(jsonString)
        val channel = jsonObject.getString(OSInfluenceConstants.INFLUENCE_CHANNEL)
        val type = jsonObject.getString(OSInfluenceConstants.INFLUENCE_TYPE)
        val ids = jsonObject.getString(OSInfluenceConstants.INFLUENCE_IDS)
        influenceChannel = InfluenceChannel.fromString(channel)
        influenceType = InfluenceType.fromString(type)
        this.ids = if (ids.isEmpty()) null else JSONArray(ids)
    }

    constructor(influenceChannel: InfluenceChannel,
                influenceType: InfluenceType,
                ids: JSONArray?) {
        this.influenceChannel = influenceChannel
        this.influenceType = influenceType
        this.ids = ids
    }

    @get:Throws(JSONException::class)
    val directId: String?
        get() = ids?.let { if (it.length() > 0) it.getString(0) else null }

    fun copy() = Influence(
            influenceChannel = this@Influence.influenceChannel,
            influenceType = this@Influence.influenceType,
            ids = this@Influence.ids
    )

    @Throws(JSONException::class)
    fun toJSONString() = JSONObject()
            .put(OSInfluenceConstants.INFLUENCE_CHANNEL, influenceChannel.toString())
            .put(OSInfluenceConstants.INFLUENCE_TYPE, influenceType.toString())
            .put(OSInfluenceConstants.INFLUENCE_IDS, if (ids != null) ids.toString() else "")
            .toString()

    override fun toString(): String {
        return "SessionInfluence{" +
                "influenceChannel=" + influenceChannel +
                ", influenceType=" + influenceType +
                ", ids=" + ids +
                '}'
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val that = o as Influence
        return influenceChannel == that.influenceChannel &&
                influenceType == that.influenceType
    }

    override fun hashCode(): Int {
        var result = influenceChannel.hashCode()
        result = 31 * result + influenceType.hashCode()
        return result
    }
}