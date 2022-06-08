package com.onesignal.onesignal.internal.push

import com.onesignal.onesignal.notification.IActionButton
import org.json.JSONObject

class ActionButton : IActionButton {
    override var id: String? = null
        private set
    override var text: String? = null
        private set
    override var icon: String? = null
        private set

    constructor() {}
    constructor(jsonObject: JSONObject) {
        id = jsonObject.optString("id")
        text = jsonObject.optString("text")
        icon = jsonObject.optString("icon")
    }

    constructor(id: String?, text: String?, icon: String?) {
        this.id = id
        this.text = text
        this.icon = icon
    }

    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        try {
            json.put("id", id)
            json.put("text", text)
            json.put("icon", icon)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        return json
    }
}