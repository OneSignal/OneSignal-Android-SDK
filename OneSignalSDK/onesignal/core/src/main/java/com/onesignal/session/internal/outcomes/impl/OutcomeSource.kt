package com.onesignal.session.internal.outcomes.impl

import org.json.JSONException
import org.json.JSONObject

internal class OutcomeSource(
    var directBody: OutcomeSourceBody?,
    var indirectBody: OutcomeSourceBody?,
) {
    @Throws(JSONException::class)
    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        directBody?.let {
            json.put(OutcomeConstants.DIRECT, it.toJSONObject())
        }
        indirectBody?.let {
            json.put(OutcomeConstants.INDIRECT, it.toJSONObject())
        }
        return json
    }

    fun setDirectBody(directBody: OutcomeSourceBody?) =
        this.apply {
            this.directBody = directBody
        }

    fun setIndirectBody(indirectBody: OutcomeSourceBody?) =
        this.apply {
            this.indirectBody = indirectBody
        }

    override fun toString(): String {
        return "OutcomeSource{" +
            "directBody=" + directBody +
            ", indirectBody=" + indirectBody +
            '}'
    }
}
