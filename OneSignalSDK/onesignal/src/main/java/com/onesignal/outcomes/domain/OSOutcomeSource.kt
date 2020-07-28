package com.onesignal.outcomes.domain

import com.onesignal.outcomes.OSOutcomeConstants
import org.json.JSONException
import org.json.JSONObject

class OSOutcomeSource(var directBody: OSOutcomeSourceBody?,
                      var indirectBody: OSOutcomeSourceBody?) {
    @Throws(JSONException::class)
    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        directBody?.let {
            json.put(OSOutcomeConstants.DIRECT, it.toJSONObject())
        }
        indirectBody?.let {
            json.put(OSOutcomeConstants.INDIRECT, it.toJSONObject())
        }
        return json
    }

    fun setDirectBody(directBody: OSOutcomeSourceBody?) = this.apply {
        this.directBody = directBody
    }

    fun setIndirectBody(indirectBody: OSOutcomeSourceBody?) = this.apply {
        this.indirectBody = indirectBody
    }

    override fun toString(): String {
        return "OSOutcomeSource{" +
                "directBody=" + directBody +
                ", indirectBody=" + indirectBody +
                '}'
    }
}